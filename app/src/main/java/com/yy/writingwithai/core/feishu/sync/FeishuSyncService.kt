package com.yy.writingwithai.core.feishu.sync

import com.yy.writingwithai.core.data.mapper.toEntity
import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.core.feishu.api.FeishuError
import com.yy.writingwithai.core.feishu.auth.FeishuAuthStore
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * feishu-bidir-sync · 同步工作流核心编排(design D2/D3) — facade 模式。
 *
 * 公开 API `push` / `pull` 保持原签名,内部委托 `FeishuDocService` 4 个 sub-command。
 * 已有 caller(`FeishuSyncRepository` / UI 详情页)无感,行为兼容。
 *
 * spec: openspec/changes/feishu-bidir-sync/specs/feishu-bidir-sync/spec.md
 * refactor: openspec/changes/feishu-doc-service-refactor (M3)
 */
/** M3:事务执行器接口,prod 走 Room withTransaction,test 可传 passthrough。 */
interface TransactionExecutor {
    suspend fun <R> execute(block: suspend () -> R): R
}

@Singleton
class FeishuSyncService
@Inject
constructor(
    private val noteRepository: NoteRepository,
    private val docService: FeishuDocService,
    private val refDao: FeishuRefDao,
    private val eventDao: FeishuSyncEventDao,
    private val authStore: FeishuAuthStore,
    private val noteDao: com.yy.writingwithai.core.data.db.NoteDao,
    private val txExecutor: TransactionExecutor
) {
    /**
     * push 工作流(design D2):委托 `FeishuDocService.createDoc` / `.updateDoc`。
     * 无 ref → `createDoc`;有 ref → `updateDoc`。
     */
    suspend fun push(noteId: String): String = withContext(Dispatchers.IO) {
        val note = noteRepository.getNote(noteId) ?: throw FeishuError.NotFound("note $noteId")
        val existingRef = refDao.getByNoteId(noteId)

        if (existingRef == null) {
            val folderToken = authStore.getFolderTokenSnapshot()
            val ref = docService.createDoc(note, folderToken)
            "同步完成: ${ref.docUrl}"
        } else {
            val ref = docService.updateDoc(note, existingRef)
            "同步完成: ${ref.docUrl}"
        }
    }

    /**
     * pull 工作流(design D3):委托 `FeishuDocService.readDoc` 拿真 markdown + 写本地 note。
     * 飞书侧 markdown 为空时抛 BadRequest,不覆盖本地。
     */
    suspend fun pull(docId: String, docUrl: String, titleHint: String = "来自飞书"): String = withContext(Dispatchers.IO) {
        val content = docService.readDoc(docUrl)
        if (content.markdown.isBlank()) {
            throw FeishuError.BadRequest(0, "飞书端为空,不覆盖本地")
        }
        // review r2 修:用 readDoc 从 URL 提取的权威 docId,而非调用方传入的 docId 参数。
        // 调用方的 extractDocId(正则) 与 FeishuDocService.extractDocIdFromUrl(取末段) 逻辑可能不同,
        // 导致查不到已有 ref、创建重复 note+ref 记录。
        val resolvedDocId = content.docId
        val title = content.title.ifBlank { titleHint }
        val markdown = content.markdown

        val existingRef = refDao.getByDocId(resolvedDocId)
        // M3 修:note + ref 必须在同一事务,避免 crash 留 orphan ref。
        // 走 FeishuRefDao.upsertNoteWithRef 已有 @Transaction 包装。
        val noteId: String = txExecutor.execute {
            val resolvedNoteId: String
            val noteToWrite: Note
            if (existingRef != null) {
                resolvedNoteId = existingRef.noteId
                val existingNote = noteRepository.getNote(resolvedNoteId)
                if (existingNote != null) {
                    noteToWrite = existingNote.copy(content = markdown, title = title)
                } else {
                    noteToWrite = Note(
                        id = resolvedNoteId,
                        title = title,
                        content = markdown,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        isPinned = false,
                        lastAiOp = null,
                        lastAiAt = null
                    )
                }
            } else {
                resolvedNoteId = UUID.randomUUID().toString()
                noteToWrite = Note(
                    id = resolvedNoteId,
                    title = title,
                    content = markdown,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    isPinned = false,
                    lastAiOp = null,
                    lastAiAt = null
                )
            }
            refDao.upsertNoteWithRef(
                note = noteToWrite.toEntity(),
                ref = FeishuRefEntity(
                    noteId = resolvedNoteId,
                    docId = resolvedDocId,
                    docUrl = docUrl,
                    lastSyncedAt = System.currentTimeMillis(),
                    syncDirection = SyncDirection.PULL,
                    localRevision = System.currentTimeMillis(),
                    remoteRevision = content.revisionId,
                    status = FeishuRefStatus.SYNCED
                ),
                noteDao = noteDao
            )
            resolvedNoteId
        }

        recordEventSafe(noteId, SyncDirection.PULL, "OK", null)
        "拉取完成: $title"
    }

    suspend fun logSyncError(noteId: String, direction: SyncDirection, error: String) {
        recordEventSafe(noteId, direction, "ERROR", error)
    }

    suspend fun getRef(noteId: String): FeishuRefEntity? = refDao.getByNoteId(noteId)

    suspend fun getRefsForNotes(noteIds: List<String>): Map<String, FeishuRefEntity> =
        refDao.getByNoteIds(noteIds).associateBy { it.noteId }

    /**
     * 断开所有同步:清 refs + events(容错 events 清理失败不阻塞 refs 清空)。
     */
    suspend fun disconnectAll() {
        refDao.deleteAll()
        runCatching { eventDao.trimTo(0) }
    }

    private suspend fun recordEventSafe(
        noteId: String,
        direction: SyncDirection,
        status: String,
        errorMessage: String?
    ) {
        runCatching {
            eventDao.insert(
                FeishuSyncEventEntity(
                    id = UUID.randomUUID().toString(),
                    noteId = noteId,
                    direction = direction,
                    status = status,
                    errorMessage = errorMessage,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }
}
