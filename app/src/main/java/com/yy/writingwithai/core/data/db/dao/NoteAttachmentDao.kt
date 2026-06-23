package com.yy.writingwithai.core.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yy.writingwithai.core.data.db.entity.NoteAttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteAttachmentDao {
    @Query("SELECT * FROM note_attachments WHERE noteId = :noteId ORDER BY createdAt")
    fun observeForNote(noteId: String): Flow<List<NoteAttachmentEntity>>

    @Query("SELECT * FROM note_attachments WHERE noteId = :noteId")
    suspend fun getForNote(noteId: String): List<NoteAttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NoteAttachmentEntity)

    @Delete
    suspend fun delete(entity: NoteAttachmentEntity)

    @Query("DELETE FROM note_attachments WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: String)
}
