package com.yy.writingwithai.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yy.writingwithai.core.data.db.entity.AiHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AiHistoryEntity)

    @Query("SELECT * FROM ai_history WHERE noteId = :noteId ORDER BY createdAt DESC")
    fun observeByNoteId(noteId: String): Flow<List<AiHistoryEntity>>

    @Query("SELECT * FROM ai_history ORDER BY createdAt DESC LIMIT :limit")
    fun observeAll(limit: Int = 100): Flow<List<AiHistoryEntity>>

    @Query("DELETE FROM ai_history WHERE createdAt < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Query("SELECT SUM(totalTokens) FROM ai_history")
    fun observeTotalTokens(): Flow<Long?>
}
