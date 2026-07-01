package com.yy.writingwithai.core.data.db

import androidx.room.TypeConverter
import com.yy.writingwithai.core.data.db.entity.SyncStatus

/**
 * F3 fix L1:review r1 syncStatus enum ↔ String 转换。
 *
 * 持久化形式 lowercase string，与 v9 schema `syncStatus TEXT NOT NULL DEFAULT 'local'` 对齐，
 * 旧数据无须清理，AutoMigration 直接复用。
 *
 * 反序列化未知字符串时 fail closed(抛 IllegalArgumentException)—— 比静默回退到 [SyncStatus.LOCAL]
 * 更安全:如果飞书同步状态机出了新的 enum 值而 schema 没更新，显式崩在读写上，
 * 比悄悄把它当 LOCAL 处理更容易被 review 抓到。
 */
class SyncStatusConverter {
    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name.lowercase()

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = when (value) {
        "local" -> SyncStatus.LOCAL
        "synced" -> SyncStatus.SYNCED
        "dirty" -> SyncStatus.DIRTY
        "conflict" -> SyncStatus.CONFLICT
        else -> throw IllegalArgumentException("Unknown SyncStatus string in DB: $value")
    }
}
