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

    @Query("SELECT * FROM feishu_ref")
    suspend fun listAll(): List<FeishuRefEntity>

    @Query("DELETE FROM feishu_ref")
    suspend fun deleteAll()

    // fix-2026-06-24-review-r1-high H15:原子写 note + ref(防孤儿)
    @Transaction
    suspend fun upsertNoteWithRef(
        note: com.yy.writingwithai.core.data.db.entity.NoteEntity,
        ref: FeishuRefEntity,
        noteDao: com.yy.writingwithai.core.data.db.NoteDao
    ) {
        noteDao.upsert(note)
        upsert(ref)
    }
}
