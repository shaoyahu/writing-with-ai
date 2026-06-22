package com.yy.writingwithai.core.data.db.dao.entity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yy.writingwithai.core.data.db.entity.entity.NoteEntityRow

/** entity-extraction-association · note_entities DAO。 */
@Dao
interface NoteEntityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<NoteEntityRow>)

    @Query("SELECT * FROM note_entities WHERE noteId = :noteId")
    suspend fun getByNoteId(noteId: String): List<NoteEntityRow>

    @Query("DELETE FROM note_entities WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String)

    @Query(
        "SELECT other.* FROM note_entities self " +
            "JOIN note_entities other ON self.entityKey = other.entityKey " +
            "WHERE self.noteId = :srcNoteId AND other.noteId != :srcNoteId " +
            "LIMIT :limit"
    )
    suspend fun querySharedEntityHits(srcNoteId: String, limit: Int): List<NoteEntityRow>

    @Query("SELECT DISTINCT entityKey FROM note_entities ORDER BY entityKey LIMIT :limit OFFSET :offset")
    suspend fun queryAllEntityKeys(limit: Int, offset: Int): List<String>
}
