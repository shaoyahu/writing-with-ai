package com.yy.writingwithai.core.feishu.sync

import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.core.feishu.api.FeishuApiClient
import com.yy.writingwithai.core.feishu.api.FeishuError
import com.yy.writingwithai.core.feishu.converter.DocxToMarkdownConverter
import com.yy.writingwithai.core.feishu.converter.FeishuBlock
import com.yy.writingwithai.core.feishu.converter.MarkdownToDocxConverter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * feishu-bidir-sync · 同步工作流核心编排(design D2/D3)。
 *
 * spec: openspec/changes/feishu-bidir-sync/specs/feishu-bidir-sync/spec.md
 */
@Singleton
class FeishuSyncService
@Inject
constructor(
    private val noteRepository: NoteRepository,
    private val feishuApi: FeishuApiClient,
    private val mdConverter: MarkdownToDocxConverter,
    private val docxConverter: DocxToMarkdownConverter,
    private val refDao: FeishuRefDao,
    private val eventDao: FeishuSyncEventDao,
    private val conflictResolver: FeishuConflictResolver
) {
    /**
     * push 工作流(design D2):
     * 读 note → 转换 MD → 写 feishu_ref → 对接飞书 API。
     */
    suspend fun push(noteId: String): String = withContext(Dispatchers.IO) {
        val note = noteRepository.getNote(noteId) ?: throw FeishuError.NotFound("note $noteId")
        val existingRef = refDao.getByNoteId(noteId)

        val docId: String = if (existingRef == null) {
            val created = feishuApi.createDocument(note.title.ifBlank { "未命名笔记" })
            refDao.upsert(
                FeishuRefEntity(
                    noteId = noteId,
                    docId = created.docId,
                    docUrl = created.docUrl,
                    lastSyncedAt = System.currentTimeMillis(),
                    syncDirection = SyncDirection.PUSH,
                    localRevision = note.updatedAt,
                    remoteRevision = "",
                    status = FeishuRefStatus.SYNCED
                )
            )
            created.docId
        } else {
            existingRef.docId
        }

        val blocks = mdConverter.convert(note.content)
        val childrenJson = buildBlockChildrenJson(blocks)
        feishuApi.appendChildren(docId, docId, childrenJson)

        val meta = feishuApi.getDocument(docId)
        refDao.upsert(
            (
                existingRef ?: FeishuRefEntity(
                    noteId = noteId, docId = docId, docUrl = "", lastSyncedAt = 0L,
                    syncDirection = SyncDirection.PUSH, localRevision = 0L,
                    remoteRevision = "", status = FeishuRefStatus.SYNCED
                )
                ).copy(
                localRevision = note.updatedAt,
                remoteRevision = meta.revisionId,
                lastSyncedAt = System.currentTimeMillis(),
                status = FeishuRefStatus.SYNCED
            )
        )

        recordEvent(noteId, SyncDirection.PUSH, "OK", null)
        "同步完成: ${note.title}"
    }

    /**
     * pull 工作流(design D3):
     * GET blocks → MD 转换 → 新建/更新 note → 写 ref。
     */
    suspend fun pull(docId: String, docUrl: String, titleHint: String = "来自飞书"): String = withContext(Dispatchers.IO) {
        val rawBlocks = feishuApi.getBlocks(docId)
        if (rawBlocks.isBlank() || rawBlocks == "{}") {
            throw FeishuError.BadRequest(0, "飞书端为空,不覆盖本地")
        }

        // getBlocks 返回飞书 block 结构 JSON,由 caller 解析回 FeishuBlock 列表
        val markdown = docxConverter.convert(emptyList()) // TODO:实际由 caller 映射原始 API JSON → FeishuBlock
        val title = titleHint.ifBlank { "来自飞书" }

        val existingRef = refDao.getByDocId(docId)
        val noteId: String
        if (existingRef != null) {
            noteId = existingRef.noteId
            val existingNote = noteRepository.getNote(noteId)
            if (existingNote != null) {
                noteRepository.upsert(
                    existingNote.copy(content = markdown, title = title),
                    emptyList()
                )
            }
        } else {
            val newNote = Note(
                id = UUID.randomUUID().toString(),
                title = title,
                content = markdown,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isPinned = false,
                lastAiOp = null,
                lastAiAt = null
            )
            noteRepository.upsert(newNote, emptyList())
            noteId = newNote.id
        }

        val meta = feishuApi.getDocument(docId)
        refDao.upsert(
            FeishuRefEntity(
                noteId = noteId,
                docId = docId,
                docUrl = docUrl,
                lastSyncedAt = System.currentTimeMillis(),
                syncDirection = SyncDirection.PULL,
                localRevision = System.currentTimeMillis(),
                remoteRevision = meta.revisionId,
                status = FeishuRefStatus.SYNCED
            )
        )

        recordEvent(noteId, SyncDirection.PULL, "OK", null)
        "拉取完成: $title"
    }

    suspend fun logSyncError(noteId: String, direction: SyncDirection, error: String) {
        recordEvent(noteId, direction, "ERROR", error)
    }

    suspend fun getRef(noteId: String): FeishuRefEntity? = refDao.getByNoteId(noteId)

    suspend fun getRefsForNotes(noteIds: List<String>): Map<String, FeishuRefEntity> =
        refDao.getByNoteIds(noteIds).associateBy { it.noteId }

    suspend fun disconnectAll() = refDao.deleteAll()

    private suspend fun recordEvent(noteId: String, direction: SyncDirection, status: String, error: String?) {
        eventDao.insert(
            FeishuSyncEventEntity(
                id = UUID.randomUUID().toString(),
                noteId = noteId,
                direction = direction,
                status = status,
                errorMessage = error,
                createdAt = System.currentTimeMillis()
            )
        )
    }
}

/** 将 FeishuBlock 列表序列化为飞书 children API JSON。 */
private fun buildBlockChildrenJson(blocks: List<FeishuBlock>): String {
    // 简化:当前 appendChildren 接受 raw JSON,这里由 caller(feishu-bidir-sync)按 design 决定格式
    val sb = StringBuilder()
    sb.append("{\"children\":[")
    blocks.forEachIndexed { i, block ->
        if (i > 0) sb.append(',')
        sb.append(blockToJsonDict(block))
    }
    sb.append("]}")
    return sb.toString()
}

private fun blockToJsonDict(block: FeishuBlock): String = when (block) {
    is FeishuBlock.Heading -> """{"block_type":"heading","heading_level":${block.level},"content":"${runsToText(
        block.runs
    )}"}"""
    is FeishuBlock.Paragraph -> """{"block_type":"text","content":"${runsToText(block.runs)}"}"""
    is FeishuBlock.CodeBlock -> """{"block_type":"code","language":"${block.language}","content":"${escapeJson(
        block.text
    )}"}"""
    is FeishuBlock.Quote -> """{"block_type":"quote","content":"${runsToText(block.runs)}"}"""
    is FeishuBlock.Divider -> """{"block_type":"divider"}"""
    else -> """{"block_type":"text","content":""}"""
}

private fun runsToText(runs: List<com.yy.writingwithai.core.feishu.converter.Run>): String =
    escapeJson(runs.joinToString("") { it.text })

private fun escapeJson(s: String): String = s
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
