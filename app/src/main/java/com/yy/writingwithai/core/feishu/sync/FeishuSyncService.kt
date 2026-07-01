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
 * 公开 API `push` / `pull` 保持原签名，内部委托 `FeishuDocService` 4 个 sub-command。
 * 已有 caller(`FeishuSyncRepository` / UI 详情页)无感，行为兼容。
 *
 * spec: openspec/changes/feishu-bidir-sync/specs/feishu-bidir-sync/spec.md
 * refactor: openspec/changes/feishu-doc-service-refactor (M3)
 */
/** M3:事务执行器接口，prod 走 Room withTransaction,test 可传 passthrough。 */
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
    private val txExecutor: TransactionExecutor,
    private val conflictResolver: FeishuConflictResolver
) {
    /**
     * push 工作流(design D2):委托 `FeishuDocService.createDoc` / `.updateDoc`。
     * 无 ref → `createDoc`;有 ref → `updateDoc`。
     *
     * fix-2026-06-30-full-review-r1 CRITICAL C2:有 ref 时，updateDoc 前先 readDoc 拿
     * 远端当前 revision，与 ref.remoteRevision 比较:不一致 → 标记 ref.status = CONFLICT
     * 并抛 [FeishuError.Conflict] 让 UI 弹冲突对话框，不再静默覆盖远端修改。
     */
    suspend fun push(noteId: String): String = withContext(Dispatchers.IO) {
        val note = noteRepository.getNote(noteId) ?: throw FeishuError.NotFound("note $noteId")
        val existingRef = refDao.getByNoteId(noteId)

        if (existingRef == null) {
            val folderToken = authStore.getFolderTokenSnapshot()
            val ref = docService.createDoc(note, folderToken)
            "同步完成: ${ref.docUrl}"
        } else {
            // C2 修:远端 revision 若已变，不让 push 静默覆盖。
            val remoteContent = runCatching { docService.readDoc(existingRef.docUrl) }.getOrNull()
            val newRemoteRev = remoteContent?.revisionId.orEmpty()
            val conflict = conflictResolver.detect(
                localRev = note.updatedAt,
                lastSyncedAt = existingRef.lastSyncedAt,
                storedRemoteRev = existingRef.remoteRevision,
                newRemoteRev = newRemoteRev
            )
            if (conflict == ConflictResult.BOTH_DIRTY) {
                refDao.upsert(existingRef.copy(status = FeishuRefStatus.CONFLICT))
                recordEventSafe(noteId, SyncDirection.PUSH, "CONFLICT", "remote_changed_after_local_edit")
                throw FeishuError.Conflict(noteId, existingRef.docId, existingRef.docUrl)
            }
            val ref = docService.updateDoc(note, existingRef)
            "同步完成: ${ref.docUrl}"
        }
    }

    /**
     * pull 工作流(design D3):委托 `FeishuDocService.readDoc` 拿真 markdown + 写本地 note。
     * 飞书侧 markdown 为空时抛 BadRequest，不覆盖本地。
     *
     * fix-2026-06-30-full-review-r1 CRITICAL C2:有 ref 且本地有修改(localRev > lastSyncedAt)
     * 时，标记 ref.status = CONFLICT 并抛 [FeishuError.Conflict] 让 UI 弹冲突对话框，不再静默
     * 覆盖本地编辑。
     */
    suspend fun pull(docId: String, docUrl: String, titleHint: String = "来自飞书"): String = withContext(Dispatchers.IO) {
        val content = docService.readDoc(docUrl)
        if (content.markdown.isBlank()) {
            throw FeishuError.BadRequest(0, "飞书端为空，不覆盖本地")
        }
        // review r2 修:用 readDoc 从 URL 提取的权威 docId，而非调用方传入的 docId 参数。
        // 调用方的 extractDocId(正则) 与 FeishuDocService.extractDocIdFromUrl(取末段) 逻辑可能不同，
        // 导致查不到已有 ref、创建重复 note+ref 记录。
        val resolvedDocId = content.docId
        val title = content.title.ifBlank { titleHint }
        val markdown = content.markdown

        val existingRef = refDao.getByDocId(resolvedDocId)

        // C2 修:本地有未推修改时不让 pull 静默覆盖。
        val conflict = if (existingRef != null) {
            val existingNote = noteRepository.getNote(existingRef.noteId)
            val localRev = existingNote?.updatedAt ?: 0L
            conflictResolver.detect(
                localRev = localRev,
                lastSyncedAt = existingRef.lastSyncedAt,
                storedRemoteRev = existingRef.remoteRevision,
                newRemoteRev = content.revisionId
            )
        } else {
            ConflictResult.NO_CONFLICT
        }
        if (existingRef != null && conflict == ConflictResult.BOTH_DIRTY) {
            refDao.upsert(existingRef.copy(status = FeishuRefStatus.CONFLICT))
            recordEventSafe(existingRef.noteId, SyncDirection.PULL, "CONFLICT", "local_changed_before_pull")
            throw FeishuError.Conflict(existingRef.noteId, existingRef.docId, existingRef.docUrl)
        }

        // M3 修:note + ref 必须在同一事务，避免 crash 留 orphan ref。
        // 走 FeishuRefDao.upsertNoteWithRef 已有 @Transaction 包装。
        val noteId: String = txExecutor.execute {
            val resolvedNoteId: String
            val noteToWrite: Note
            // fix-2026-06-30-full-review-r1 MEDIUM M3:pull 成功后 NoteEntity.syncStatus
            // 必须切到 SYNCED，与 FeishuRefEntity.status 对齐;默认 LOCAL 或保留旧 DIRTY
            // 会让 NoteEntity 与 FeishuRefEntity 状态不一致。
            if (existingRef != null) {
                resolvedNoteId = existingRef.noteId
                val existingNote = noteRepository.getNote(resolvedNoteId)
                if (existingNote != null) {
                    noteToWrite = existingNote.copy(
                        content = markdown,
                        title = title,
                        syncStatus = com.yy.writingwithai.core.data.db.entity.SyncStatus.SYNCED
                    )
                } else {
                    noteToWrite = Note(
                        id = resolvedNoteId,
                        title = title,
                        content = markdown,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        isPinned = false,
                        lastAiOp = null,
                        lastAiAt = null,
                        syncStatus = com.yy.writingwithai.core.data.db.entity.SyncStatus.SYNCED
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
                    lastAiAt = null,
                    syncStatus = com.yy.writingwithai.core.data.db.entity.SyncStatus.SYNCED
                )
            }
            // fix-2026-06-26-review-r3 HIGH H12:localRevision 不应被 pull 重置为 now。
            // pull 是 "远端覆盖本地",ref.localRevision 反映 "本地 last write 时刻",
            // 应当等于本次 noteToWrite.updatedAt —— 与 push 走同样的 note.updatedAt 一致。
            // 之前 `localRevision = System.currentTimeMillis()` 导致 pull 后 localRev
            // 永远 > lastSyncedAt，下次 conflict 检测永远 LOCAL > REMOTE,BOTH_DIRTY 假阳性。
            val pullTimestamp = System.currentTimeMillis()
            refDao.upsertNoteWithRef(
                note = noteToWrite.toEntity(),
                ref = FeishuRefEntity(
                    noteId = resolvedNoteId,
                    docId = resolvedDocId,
                    docUrl = docUrl,
                    lastSyncedAt = pullTimestamp,
                    syncDirection = SyncDirection.PULL,
                    localRevision = noteToWrite.updatedAt,
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
     * fix-2026-06-26-review-r3 HIGH H13:标记 ref 为 REMOTE_DELETED，补完死代码路径。
     * 调用方(同步引擎 / 飞书侧 410 处理)检测到远端 doc 已删除时调用，
     * 把 ref.status 切到 REMOTE_DELETED，本地 note 保留(用户决定是否重传)。
     */
    suspend fun markRemoteDeleted(noteId: String): Int = refDao.markRemoteDeleted(noteId)

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
