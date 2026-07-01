package com.yy.writingwithai.core.data.export

import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.core.data.repo.AiHistoryRepository
import com.yy.writingwithai.core.data.repo.NoteRepository
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.builtins.ListSerializer

/**
 * M4-3 · 笔记导入器(Hilt 单例)。
 *
 * 读 zip → JSON 反序列化 → 逐条 note 去重(按 id)— 已存在跳过，不覆盖。
 * 失败条目收集到 [ImportReport.failedNotes](UI 渲染 + `import_report.md` 文件)。
 *
 * 写入循环(必须按此顺序):
 * 1. 从 [input] 读 zip entries(`notes.json` / `tags.json` 必读，其它原样保留)
 * 2. 逐条导入:已存在跳过 / 失败收集 / 成功写入 Room
 * 3. 把 `ImportReport` 序列化成 Markdown 文本，作为新 entry `import_report.md` 加进 zip
 * 4. 用 [ZipHelper.writeZip] 把更新后的 entries 写回 [output]
 *
 * spec §"失败条目收集到 import_report.md" 要求报告**写回压缩包根目录**(不是只返回数据),
 * 满足 M4-3 验收 "导出再导入数据完整" + 用户事后可看报告。
 */
@Singleton
class NoteImporter
@Inject
constructor(
    private val noteRepository: NoteRepository,
    private val aiHistoryRepository: AiHistoryRepository,
    private val zipHelper: ZipHelper
) {
    private val json = ExportJsonFormat

    /**
     * 闭循环导入:读 zip → 处理 → 把 `import_report.md` 加进 zip → 写回 output。
     *
     * @return 导入结果(同步写到 `import_report.md` entry 里)
     */
    suspend fun importFromZip(input: InputStream, output: OutputStream): ImportReport {
        val entries = zipHelper.readZip(input)
        val exportNotes: List<ExportNote> =
            entries["notes.json"]?.let {
                json.decodeFromString(
                    ListSerializer(ExportNote.serializer()),
                    it.toString(Charsets.UTF_8)
                )
            } ?: emptyList()
        val tags: Map<String, List<String>> =
            entries["tags.json"]?.let {
                json.decodeFromString(ExportTags.serializer(), it.toString(Charsets.UTF_8)).noteIdToTags
            } ?: emptyMap()

        var success = 0
        var skipped = 0
        val failed = mutableListOf<FailedNote>()
        for (exportNote in exportNotes) {
            try {
                val existing = noteRepository.getNote(exportNote.id)
                if (existing != null) {
                    skipped++
                    continue
                }
                val note =
                    Note(
                        id = exportNote.id,
                        title = exportNote.title,
                        content = exportNote.content,
                        createdAt = exportNote.createdAt,
                        updatedAt = exportNote.updatedAt,
                        isPinned = exportNote.isPinned,
                        lastAiOp = exportNote.lastAiOp,
                        lastAiAt = exportNote.lastAiAt,
                        // fix-2026-06-30-full-review-r1 H4:round-trip 保留同步状态
                        syncRevision = exportNote.syncRevision,
                        syncStatus = exportNote.syncStatus,
                        lastSyncedAt = exportNote.lastSyncedAt
                    )
                noteRepository.upsert(note, tags[exportNote.id].orEmpty())
                success++
            } catch (e: kotlinx.coroutines.CancellationException) {
                // H1:r1 review 发现 catch (Exception) 会吞掉 CancellationException,
                // 把协程取消错误当"导入失败"记录。必须重新抛出。
                throw e
            } catch (e: Exception) {
                failed.add(FailedNote(exportNote.id, exportNote.title, e.message ?: "unknown"))
            }
        }

        // H3:r1 review 发现 spec §"ai_history 同步导入" 场景未实现。读 ai_history.json 后遍历:
        // - noteId == null 或 noteRepository.getNote(noteId) == null → orphan，跳过(M5 polish)
        // - 调 aiHistoryRepository.record(...) 复用 M2 既有 API，失败 swallowed(ImportReport 不破坏 schema,
        //   ai_history 失败细节 M5 polish 再加 aiHistoryFailed 字段)
        val exportAiHistories: List<ExportAiHistory> =
            entries["ai_history.json"]?.let {
                json.decodeFromString(
                    ListSerializer(ExportAiHistory.serializer()),
                    it.toString(Charsets.UTF_8)
                )
            } ?: emptyList()
        for (history in exportAiHistories) {
            val linkedNoteId = history.noteId ?: continue
            try {
                if (noteRepository.getNote(linkedNoteId) == null) continue
                aiHistoryRepository.record(
                    noteId = linkedNoteId,
                    providerId = history.providerId,
                    model = history.model,
                    op = history.op,
                    inputTokens = history.inputTokens,
                    outputTokens = history.outputTokens,
                    totalTokens = history.totalTokens,
                    durationMs = history.durationMs,
                    createdAt = history.createdAt,
                    inputSnapshot = history.inputSnapshot,
                    outputSnapshot = history.outputSnapshot,
                    error = history.error
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // R3 fix M6:之前完全无声 — 调试 / 审计 / 历史完整性都查不到为什么少了一条 history。
                // 不计入 note failedNotes(语义不同)，但要 log 出 id + 原因便于事后排查。
                // M5 polish 加 aiHistoryFailed 字段到 ImportReport 时再升级为结构化报告。
                android.util.Log.w(
                    "NoteImporter",
                    "ai_history record failed for noteId=${history.noteId} providerId=${history.providerId}",
                    e
                )
            }
        }

        val report =
            ImportReport(
                successCount = success,
                skippedCount = skipped,
                failedCount = failed.size,
                failedNotes = failed
            )

        // 把 ImportReport 序列化成 Markdown，加进 zip 根目录，再写回 output
        val updatedEntries = entries.toMutableMap()
        updatedEntries["import_report.md"] = formatReport(report).toByteArray(Charsets.UTF_8)
        zipHelper.writeZip(updatedEntries, output)

        return report
    }

    /** 序列化为 Markdown 报告(spec §"报告格式纯文本 Markdown" 场景)。 */
    internal fun formatReport(report: ImportReport): String {
        val timestamp =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).format(Date())
        val total = report.successCount + report.skippedCount + report.failedCount
        return buildString {
            appendLine("# writing-with-ai 导入报告")
            appendLine()
            appendLine("时间:$timestamp")
            appendLine("总计:$total 条笔记")
            appendLine("- 成功:${report.successCount}")
            appendLine("- 跳过(id 重复):${report.skippedCount}")
            appendLine("- 失败:${report.failedCount}")
            if (report.failedNotes.isNotEmpty()) {
                appendLine()
                appendLine("## 失败详情")
                report.failedNotes.forEach { fn ->
                    appendLine()
                    appendLine("### note id=${fn.noteId} title=\"${fn.title}\"")
                    appendLine("失败原因:${fn.error}")
                    appendLine("解决建议:手动检查后重试")
                }
            }
        }
    }
}
