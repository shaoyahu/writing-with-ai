package com.yy.writingwithai.core.feishu.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** feishu-bidir-sync · 同步事件日志 DAO(tasks §1.4)。 */
@Dao
interface FeishuSyncEventDao {
    @Insert
    suspend fun insert(event: FeishuSyncEventEntity)

    @Query("SELECT * FROM feishu_sync_event ORDER BY createdAt DESC LIMIT :limit")
    suspend fun listLast(limit: Int = 20): List<FeishuSyncEventEntity>

    @Query("SELECT COUNT(*) FROM feishu_sync_event")
    suspend fun count(): Int

    @Query(
        "DELETE FROM feishu_sync_event WHERE id IN " +
            "(SELECT id FROM feishu_sync_event ORDER BY createdAt ASC LIMIT " +
            "MAX(0, (SELECT COUNT(*) FROM feishu_sync_event) - :cap))"
    )
    suspend fun trimTo(cap: Int = 100)
}
