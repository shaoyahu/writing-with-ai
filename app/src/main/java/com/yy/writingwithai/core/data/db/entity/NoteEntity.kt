package com.yy.writingwithai.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 随手记 Note 在 Room 中的持久化形态。
 *
 * 字段定义见 [openspec.changes.quick-note-feature.specs.quick-note.spec] §"Note entity schema":
 * - `id`: UUID 字符串主键
 * - `title`: 用户填的标题;空时由正文前 30 字派生(派生逻辑在 UI 层)
 * - `content`: Markdown 源码
 * - `createdAt` / `updatedAt`: Long(epoch millis)
 * - `isPinned`: 是否固定(列表置顶用)
 * - `lastAiOp` / `lastAiAt`: M2 AI 抽象层写入;M1 始终 null
 *
 * `updatedAt` 上建索引，因为列表按它降序排;`isPinned` 不单独建索引(基数太小)。
 */
@Entity(
    tableName = "notes",
    indices = [Index("updatedAt")]
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val lastAiOp: String? = null,
    val lastAiAt: Long? = null,
    val syncRevision: String? = null,
    // F3 fix L1:syncStatus String → SyncStatus enum。ColumnInfo defaultValue 仍写 "local",
    // 与 SyncStatusConverter.toSyncStatus("local") == LOCAL 对齐，旧 schema 不动。
    @androidx.room.ColumnInfo(defaultValue = "local")
    val syncStatus: SyncStatus = SyncStatus.LOCAL,
    val lastSyncedAt: Long? = null
)
