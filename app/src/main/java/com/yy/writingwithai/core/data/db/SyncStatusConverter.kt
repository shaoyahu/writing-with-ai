package com.yy.writingwithai.core.data.db

import android.util.Log
import androidx.room.TypeConverter
import com.yy.writingwithai.core.data.db.entity.SyncStatus

/**
 * F3 fix L1:review r1 syncStatus enum ↔ String 转换。
 *
 * 持久化形式 lowercase string，与 v9 schema `syncStatus TEXT NOT NULL DEFAULT 'local'` 对齐，
 * 旧数据无须清理，AutoMigration 直接复用。
 *
 * 反序列化未知字符串时 fail open(回退到 [SyncStatus.LOCAL] + log warning)——
 * fix-full-review:之前 fail-closed(valueOf 抛 IllegalArgumentException)会导致旧版 DB
 * 新增 enum 值后 App 崩溃；回退到 LOCAL 更安全，新值丢失总比整库不可读好。
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
        // feishu-import-from-folder:从飞书导入部分图片失败的笔记
        "partial_import_fail" -> SyncStatus.PARTIAL_IMPORT_FAIL
        // fix-full-review:未知值回退到 LOCAL 并打 warning，而非抛异常导致 App 崩溃
        else -> {
            Log.w(TAG, "Unknown SyncStatus string in DB: $value, falling back to LOCAL")
            SyncStatus.LOCAL
        }
    }

    companion object {
        private const val TAG = "SyncStatusConverter"
    }
}
