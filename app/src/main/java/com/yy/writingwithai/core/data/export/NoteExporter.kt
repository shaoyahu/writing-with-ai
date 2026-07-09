package com.yy.writingwithai.core.data.export

import com.yy.writingwithai.BuildConfig
import com.yy.writingwithai.core.data.repo.AiHistoryRepository
import com.yy.writingwithai.core.data.repo.NoteRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * M4-3 · 笔记导出器(Hilt 单例)。
 *
 * 用 [kotlinx.serialization.json.Json] 序列化全部 notes / ai_history / tags / meta 为 zip,
 * 同时生成 Markdown 可读副本。
 */
@Singleton
class NoteExporter
@Inject
constructor(
    private val noteRepository: NoteRepository,
    private val aiHistoryRepository: AiHistoryRepository,
    private val zipHelper: ZipHelper
) {
    private val json = ExportJsonFormat

    /**
     * 导出全部数据为 JSON zip(含 notes.json / ai_history.json / tags.json / meta.json + notes/Markdown files)。
     *
     * @param outputStream SAF `contentResolver.openOutputStream(uri)` 传进的 OutputStream。
     * @return 实际导出的 note 条数(供 SettingsDataViewModel 显示 Done 文案，X = notes.size)。
     */
    suspend fun exportToJsonZip(outputStream: java.io.OutputStream): Int {
        // R3 fix M5:之前 `noteRepository.observeNotesWithTags(null, null).first()` 内部
        // 已经 `combine(notesFlow, noteTagDao.observeAllCrossRefs())`，然后又单独
        // 调 `noteTagDao.observeAllCrossRefs().first()` 再 groupBy —— 两次全表 crossRef
        // 读。现在直接复用 `NoteWithTags.tags`，组 tags map 在内存里(已经是按 note 摊开的)。
        val withTags = noteRepository.observeNotesWithTags(null, null).first()
        val notes = withTags.map { it.note }
        val aiHistories = aiHistoryRepository.observeAll(Int.MAX_VALUE).first()
        val tags: Map<String, List<String>> =
            withTags.associate { it.note.id to it.tags }
        val now = ISO_TIMESTAMP_FORMAT.format(java.util.Date())
        // fix M12 (full-review):用 BuildConfig.VERSION_NAME 而不是硬编码 "0.4.0"。
        // 之前 hardcode 容易在版本升级后忘记同步,导致导出文件元数据 appVersion 永远停在 0.4.0,
        // 用户跨设备 round-trip 时 import_report 看到的版本号和实际 APK 不一致,排查靠版本号定位问题失败。
        val meta = ExportMeta(exportTimestamp = now, appVersion = BuildConfig.VERSION_NAME, schemaVersion = "1")
        val entries = mutableMapOf<String, ByteArray>()
        entries["notes.json"] =
            json.encodeToString(ExportNoteListSerializer, notes.map { it.toExport() })
                .toByteArray(Charsets.UTF_8)
        entries["ai_history.json"] =
            json
                .encodeToString(ExportAiHistoryListSerializer, aiHistories.map { it.toExport() })
                .toByteArray(Charsets.UTF_8)
        val tagsJson = json
            .encodeToString(ExportTags.serializer(), ExportTags(tags))
            .toByteArray(Charsets.UTF_8)
        entries["tags.json"] = tagsJson
        entries["meta.json"] = json.encodeToString(ExportMeta.serializer(), meta).toByteArray(Charsets.UTF_8)
        // Markdown 可读副本
        notes.forEach { note ->
            // L8 修:note.id 必须经 PathSafety 校验，避免 zip slip / 路径遍历。
            com.yy.writingwithai.core.security.PathSafety.requireSafeId(note.id, "note.id")
            val md = "# ${note.title.ifBlank { "Untitled" }}\n\n${note.content}"
            entries["notes/${note.id}.md"] = md.toByteArray(Charsets.UTF_8)
        }
        zipHelper.writeZip(entries, outputStream)
        return notes.size
    }

    companion object {
        // fix:提取 SimpleDateFormat 常量，避免每次调用创建新实例
        // fix-full-review:格式含字面量 'Z'，必须显式设 UTC 时区，否则输出的是本地时间而非 UTC
        private val ISO_TIMESTAMP_FORMAT =
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.ROOT).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
    }
}

private fun com.yy.writingwithai.core.data.model.Note.toExport() = ExportNote(
    id = id,
    title = title,
    content = content,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isPinned = isPinned,
    lastAiOp = lastAiOp,
    lastAiAt = lastAiAt,
    // fix-2026-06-30-full-review-r1 H4:补同步字段，导出-导入 round-trip 不丢状态
    syncRevision = syncRevision,
    syncStatus = syncStatus,
    lastSyncedAt = lastSyncedAt
)

private fun com.yy.writingwithai.core.data.model.AiHistory.toExport() = ExportAiHistory(
    id = id, noteId = noteId, providerId = providerId, model = model,
    op = op, inputTokens = inputTokens, outputTokens = outputTokens,
    totalTokens = totalTokens, durationMs = durationMs, createdAt = createdAt,
    inputSnapshot = inputSnapshot, outputSnapshot = outputSnapshot,
    truncated = truncated, error = error
)
