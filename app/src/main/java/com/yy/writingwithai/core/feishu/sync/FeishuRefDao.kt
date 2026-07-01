package com.yy.writingwithai.core.feishu.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** feishu-bidir-sync · feishu_ref DAO(tasks §1.3)。 */
@Dao
interface FeishuRefDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ref: FeishuRefEntity)

    @Query("SELECT * FROM feishu_ref WHERE noteId = :noteId")
    suspend fun getByNoteId(noteId: String): FeishuRefEntity?

    @Query("SELECT * FROM feishu_ref WHERE noteId IN (:noteIds)")
    suspend fun getByNoteIds(noteIds: List<String>): List<FeishuRefEntity>

    @Query("SELECT * FROM feishu_ref WHERE docId = :docId")
    suspend fun getByDocId(docId: String): FeishuRefEntity?

    @Query("DELETE FROM feishu_ref WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String)

    /**
     * fix-2026-06-26-review-r3 HIGH H13:REMOTE_DELETED 死代码补完。
     * 远端文档被删除(在飞书侧) → 标记本地 ref 为 REMOTE_DELETED,
     * UI 层可基于此显示"远端已删除，本地保留副本"并提供"重新创建"操作。
     */
    @Query("UPDATE feishu_ref SET status = 'REMOTE_DELETED' WHERE noteId = :noteId")
    suspend fun markRemoteDeleted(noteId: String): Int

    @Query("SELECT * FROM feishu_ref")
    suspend fun listAll(): List<FeishuRefEntity>

    @Query("DELETE FROM feishu_ref")
    suspend fun deleteAll()

    // fix-2026-06-26-review-r3-critical C1:Room @Transaction 在跨 DAO 调用时，
    // InvalidatorTracker 可能不在同一事务上下文，导致看似原子实则非原子。
    // 改为要求调用方用 `db.withTransaction { ... }` 包裹，这里只保留
    // 单 DAO 范围的写入(仅 upsert(ref) 在 FeishuRefDao 上，跨 DAO 由调用方管)。
    // 保留 @Transaction 注解作为单 DAO 内部多写入的兜底保证，
    // 同时注释明确告诉调用方:跨 DAO 写入必须用 db.withTransaction 包裹。
    @Transaction
    suspend fun upsertNoteWithRef(
        note: com.yy.writingwithai.core.data.db.entity.NoteEntity,
        ref: FeishuRefEntity,
        noteDao: com.yy.writingwithai.core.data.db.NoteDao
    ) {
        // 调用方必须已经在 db.withTransaction 上下文中(FeishuSyncService.pull 已经做了),
        // 这里两个 DAO 调用共用同一事务，Room Invalidator 看到的是同一事务边界。
        noteDao.upsert(note)
        upsert(ref)
    }
}
