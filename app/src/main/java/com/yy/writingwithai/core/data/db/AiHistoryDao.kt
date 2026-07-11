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

    /**
     * fix-2026-06-30-full-review-r1 HIGH H5:删 note 时清掉对应 ai_history 行，避免
     * observeByNoteId 持续发射孤儿行 + observeTotalTokens 计入脏数据。AiHistoryEntity.noteId
     * 无 ForeignKey，所以必须由业务层显式清理。
     */
    @Query("DELETE FROM ai_history WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String): Int

    // ----- ai-usage-statistics §1:SQL GROUP BY 聚合(走 idx_ai_history_createdAt) -----
    // 只聚合 error IS NULL 的成功行:失败调用 token 语义模糊,污染统计。
    // 半开区间 [periodStart, periodEnd),periodEnd 不含。

    /** GROUP BY (createdAt / 86400000)。dayBucket = epochDay。空区间返回空列表。 */
    @Query(
        "SELECT (createdAt / 86400000) AS dayBucket, " +
            "SUM(inputTokens) AS sumInput, SUM(outputTokens) AS sumOutput, " +
            "SUM(totalTokens) AS sumTotal, COUNT(*) AS count " +
            "FROM ai_history " +
            "WHERE createdAt >= :periodStart AND createdAt < :periodEnd " +
            "AND error IS NULL " +
            "GROUP BY dayBucket " +
            "ORDER BY dayBucket ASC"
    )
    fun aggregateByDay(periodStart: Long, periodEnd: Long): Flow<List<DailyUsageBucket>>

    /** GROUP BY op。返回的 op 是 raw string(WritingOp enum.name)。 */
    @Query(
        "SELECT op AS op, " +
            "SUM(inputTokens) AS sumInput, SUM(outputTokens) AS sumOutput, " +
            "SUM(totalTokens) AS sumTotal, COUNT(*) AS count " +
            "FROM ai_history " +
            "WHERE createdAt >= :periodStart AND createdAt < :periodEnd " +
            "AND error IS NULL " +
            "GROUP BY op"
    )
    fun aggregateByOp(periodStart: Long, periodEnd: Long): Flow<List<OpUsageBucket>>

    /** GROUP BY providerId。 */
    @Query(
        "SELECT providerId AS providerId, " +
            "SUM(inputTokens) AS sumInput, SUM(outputTokens) AS sumOutput, " +
            "SUM(totalTokens) AS sumTotal, COUNT(*) AS count " +
            "FROM ai_history " +
            "WHERE createdAt >= :periodStart AND createdAt < :periodEnd " +
            "AND error IS NULL " +
            "GROUP BY providerId"
    )
    fun aggregateByProvider(periodStart: Long, periodEnd: Long): Flow<List<ProviderUsageBucket>>
}
