package com.yy.writingwithai.core.data.db.dao.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yy.writingwithai.core.data.db.entity.SyncMetaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetaDao {
    @Query("SELECT * FROM sync_meta WHERE `key` = :key")
    suspend fun get(key: String): SyncMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entity: SyncMetaEntity)

    @Query("SELECT * FROM sync_meta")
    fun observeAll(): Flow<List<SyncMetaEntity>>
}
