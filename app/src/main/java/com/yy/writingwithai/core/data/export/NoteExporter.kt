package com.yy.writingwithai.core.data.export

import com.yy.writingwithai.core.data.db.NoteTagDao
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
    private val noteTagDao: NoteTagDao,
    private val zipHelper: ZipHelper
) {
    private val json = ExportJsonFormat

    /**
     * 导出全部数据为 JSON zip(含 notes.json / ai_history.json / tags.json / meta.json + notes/Markdown files)。
     *
     * @param outputStream SAF `contentResolver.openOutputStream(uri)` 传进的 OutputStream。
     * @return 实际导出的 note 条数(供 SettingsDataViewModel 显示 Done 文案,X = notes.size)。
     */
    suspend fun exportToJsonZip(outputStream: java.io.OutputStream): Int {
        val notes = noteRepository.observeNotesWithTags(null, null).first().map { it.note }
        val aiHistories = aiHistoryRepository.observeAll(Int.MAX_VALUE).first()
        val crossRefs = noteTagDao.observeAllCrossRefs().first()
        val tags: Map<String, List<String>> =
            crossRefs.groupBy({ it.noteId }, { it.tag })
        val now =
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.ROOT)
                .format(java.util.Date())
        val meta = ExportMeta(exportTimestamp = now, appVersion = "0.4.0", schemaVersion = "1")
        val entries = mutableMapOf<String, ByteArray>()
        entries["notes.json"] =
            json.encodeToString(ExportNoteListSerializer, notes.map { it.toExport() })
                .toByteArray(Charsets.UTF_8)
        entries["ai_history.json"] =
            json
                .encodeToString(ExportAiHistoryListSerializer, aiHistories.map { it.toExport() })
                .toByteArray(Charsets.UTF_8)
        entries["tags.json"] = json.encodeToString(ExportTags.serializer(), ExportTags(tags)).toByteArray(Charsets.UTF_8)
        entries["meta.json"] = json.encodeToString(ExportMeta.serializer(), meta).toByteArray(Charsets.UTF_8)
        // Markdown 可读副本
        notes.forEach { note ->
            val md = "# ${note.title.ifBlank { "Untitled" }}\n\n${note.content}"
            entries["notes/${note.id}.md"] = md.toByteArray(Charsets.UTF_8)
        }
        zipHelper.writeZip(entries, outputStream)
        return notes.size
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
    lastAiAt = lastAiAt
)

private fun com.yy.writingwithai.core.data.model.AiHistory.toExport() = ExportAiHistory(
    id = id, noteId = noteId, providerId = providerId, model = model,
    op = op, inputTokens = inputTokens, outputTokens = outputTokens,
    totalTokens = totalTokens, durationMs = durationMs, createdAt = createdAt,
    inputSnapshot = inputSnapshot, outputSnapshot = outputSnapshot,
    truncated = truncated, error = error
)
