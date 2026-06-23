package com.yy.writingwithai.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * cloud-sync-foundation · 同步元数据表。
 */
@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey
    val key: String,
    val value: String
)
