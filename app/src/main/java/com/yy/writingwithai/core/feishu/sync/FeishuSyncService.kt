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
     *
     * feishu-folder-migration:有 ref 时，先检测当前 folder token 与 ref.folderToken 是否一致，
     * 不一致 → 抛 [FeishuError.FolderTokenMismatch] 让 UI 弹迁移对话框。
     */
    /**
     * feishu-sync-feedback · 返回 ref entity(而不是 string 拼接),
     * 让 VM 拿到结构化的 docUrl / folderToken 等字段,不再 startsWith("同步完成:") 解析。
     */
    suspend fun push(noteId: String): FeishuRefEntity = withContext(Dispatchers.IO) {
        val note = noteRepository.getNote(noteId) ?: throw FeishuError.NotFound("note $noteId")
        val existingRef = refDao.getByNoteId(noteId)

        if (existingRef == null) {
            val folderToken = authStore.getFolderTokenSnapshot()
            docService.createDoc(note, folderToken)
        } else {
            // feishu-folder-migration:检测 folder token 变更
            // fix(folder-mismatch-false-positive):mismatch 检测必须用 resolve 后的 token 比较，
            // 因为 ref.folderToken 是 resolved 值(createDoc 时 resolveFolderToken 结果)，
            // 而 authStore.getFolderTokenSnapshot() 返回用户输入的原始值。
            // 用户输入 wiki token 时，原始值 != resolved 值，但功能上指向同一文件夹，
            // 不应触发迁移对话框。先 resolve 当前 token 再比较。
            val currentRawToken = authStore.getFolderTokenSnapshot()
            val currentResolvedToken = if (currentRawToken != null) {
                docService.resolveFolderToken(currentRawToken)
            } else {
                null
            }
            // fix(folder-mismatch-legacy-null):existing ref 是该字段上线前的旧数据
            // (folderToken == null,默认根目录)。现在用户设了 folder token,严格比较
            // 会误报 mismatch。视为"未知 → 已知"的迁移:把当前 folderToken 写回 ref,
            // 跳过对话框,走 updateDoc。后续 push 就有可比较的 ref 值。
            val isLegacyNullRef = existingRef.folderToken == null && currentResolvedToken != null
            val refForUpdate: FeishuRefEntity = if (isLegacyNullRef) {
                val migrated = existingRef.copy(folderToken = currentResolvedToken)
                refDao.upsert(migrated)
                recordEventSafe(
                    noteId,
                    SyncDirection.PUSH,
                    "LEGACY_FOLDER_TOKEN_MIGRATED",
                    "null→$currentResolvedToken"
                )
                migrated
            } else {
                existingRef
            }
            if (!isLegacyNullRef && currentResolvedToken != existingRef.folderToken) {
                recordEventSafe(
                    noteId,
                    SyncDirection.PUSH,
                    "FOLDER_MISMATCH",
                    "current=$currentRawToken currentResolved=$currentResolvedToken ref=${existingRef.folderToken}"
                )
                throw FeishuError.FolderTokenMismatch(
                    noteId = noteId,
                    docId = existingRef.docId,
                    docUrl = existingRef.docUrl,
                    currentFolderToken = currentRawToken,
                    refFolderToken = existingRef.folderToken
                )
            }

            // fix(feishu-remote-deleted):远端文档已删除时,标记为 REMOTE_DELETED
            // 并删除旧 ref 后重新创建文档(与用户删除文档后的期望一致)。
            if (existingRef.status == FeishuRefStatus.REMOTE_DELETED) {
                refDao.deleteByNoteId(noteId)
                recordEventSafe(noteId, SyncDirection.PUSH, "RECREATE_AFTER_REMOTE_DELETED", null)
                val folderToken = authStore.getFolderTokenSnapshot()
                return@withContext docService.createDoc(note, folderToken)
            }

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

            // fix(feishu-remote-deleted):updateDoc 返回 404 时,删除旧 ref 并重新创建
            val ref = try {
                docService.updateDoc(note, refForUpdate)
            } catch (e: FeishuError.NotFound) {
                // 远端文档已删除(可能在 readDoc 和 updateDoc 之间被删),
                // 删除旧 ref 并重新创建文档
                refDao.deleteByNoteId(noteId)
                recordEventSafe(noteId, SyncDirection.PUSH, "RECREATE_AFTER_404", null)
                val folderToken = authStore.getFolderTokenSnapshot()
                docService.createDoc(note, folderToken)
            }
            ref
        }
    }

    /**
     * feishu-folder-migration · folder token 变更后的迁移 push。
     *
     * 用户在 FolderMigrationDialog 中做出选择后调用:
     * - [FolderMigrationChoice.DELETE_AND_RECREATE]: 删除远端旧文档 → 删本地 ref → 在新文件夹创建
     * - [FolderMigrationChoice.UPDATE_IN_PLACE]: 原地更新(忽略 folder token 变更)
     *
     * DELETE_AND_RECREATE 路径约束:
     * 1. 远端 deleteFile 必须返回成功 — 失败 → 抛 FeishuError.ServerError / .NetworkError(避免
     *    远端删除失败却创建新文档,导致"旧文档仍在 + 新文档在另一文件夹"双份)
     * 2. refDao.deleteByNoteId + createDoc 包进 txExecutor.execute,保证本地状态原子一致
     *    (M3 H6 note + ref 必须在同一事务思路的延伸)
     * 3. UPDATE_IN_PLACE 不涉及 ref 删除/重建,直接走 updateDoc;不通过 txExecutor
     *    (只在 DAO 层走单条 refDao.upsert,createDoc / updateDoc 自管)
     */
    suspend fun pushWithFolderMigration(noteId: String, choice: FolderMigrationChoice): FeishuRefEntity =
        withContext(Dispatchers.IO) {
            val note = noteRepository.getNote(noteId) ?: throw FeishuError.NotFound("note $noteId")
            val existingRef = refDao.getByNoteId(noteId)
                ?: throw FeishuError.BadRequest(0, "no existing ref for migration")

            when (choice) {
                FolderMigrationChoice.DELETE_AND_RECREATE -> {
                    // 1. 远端删除(必须成功;失败已被 docService.deleteDoc 内部降级记录 DELETE_FAILED 事件,
                    //    此处把 Boolean 转成异常抛给 VM,VM 已知 DELETE_FAILED 事件存在)
                    val deleted = docService.deleteDoc(existingRef)
                    if (!deleted) {
                        // 不删 ref、不创建新文档,保留旧 ref 让用户下次 push 重试或选 UPDATE_IN_PLACE
                        recordEventSafe(
                            noteId,
                            SyncDirection.PUSH,
                            "DELETE_AND_RECREATE_ABORTED",
                            "远程删除失败,旧文档保留在原位置"
                        )
                        throw FeishuError.ServerError(0)
                    }
                    // 2+3. 本地 ref 删除 + 新建文档在同一事务内,保证本地 ref ↔ 远端 doc 一致性
                    val folderToken = authStore.getFolderTokenSnapshot()
                    txExecutor.execute {
                        refDao.deleteByNoteId(noteId)
                        docService.createDoc(note, folderToken)
                    }
                }
                FolderMigrationChoice.UPDATE_IN_PLACE -> {
                    // 原地更新(不改变文件夹)
                    docService.updateDoc(note, existingRef)
                }
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

/**
 * feishu-folder-migration · folder token 变更后用户选择的迁移方式。
 */

enum class FolderMigrationChoice {
    /** 删除远端旧文档(移到回收站) + 在新文件夹创建新文档 */
    DELETE_AND_RECREATE,

    /** 忽略 folder token 变更，在原位置更新文档内容 */
    UPDATE_IN_PLACE
}
