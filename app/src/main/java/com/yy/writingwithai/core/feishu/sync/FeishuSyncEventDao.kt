package com.yy.writingwithai.core.feishu.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** feishu-bidir-sync · 同步事件日志 DAO(tasks §1.4)。 */
@Dao
interface FeishuSyncEventDao {
    @Insert
    suspend fun insert(event: FeishuSyncEventEntity)

    @Query("SELECT * FROM feishu_sync_event ORDER BY createdAt DESC LIMIT :limit")
    suspend fun listLast(limit: Int = 20): List<FeishuSyncEventEntity>

    /**
     * feishu-sync-end-to-end · 设置页同步日志 reactive 版本。
     *
     * 与 [listLast] 同语义(最近 [limit] 条按 `createdAt DESC`)，但返回 `Flow` —— Room
     * 内部用 InvalidationTracker 自动响应 `feishu_sync_event` 表的写入/删除，无需
     * 手动重新查询。
     */
    @Query("SELECT * FROM feishu_sync_event ORDER BY createdAt DESC LIMIT :limit")
    fun observeLast(limit: Int = 20): Flow<List<FeishuSyncEventEntity>>

    @Query("SELECT COUNT(*) FROM feishu_sync_event")
    suspend fun count(): Int

    @Query(
        "DELETE FROM feishu_sync_event WHERE id IN " +
            "(SELECT id FROM feishu_sync_event ORDER BY createdAt ASC LIMIT " +
            "MAX(0, (SELECT COUNT(*) FROM feishu_sync_event) - :cap))"
    )
    suspend fun trimTo(cap: Int = 100)
}
