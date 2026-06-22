package com.yy.writingwithai.core.feishu.sync

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yy.writingwithai.core.data.db.entity.NoteEntity

/**
 * feishu-bidir-sync · 笔记与飞书文档关联表(design D1)。
 *
 * spec: openspec/changes/feishu-bidir-sync/specs/feishu-bidir-sync/spec.md
 */
@Entity(
    tableName = "feishu_ref",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class FeishuRefEntity(
    @PrimaryKey val noteId: String,
    val docId: String,
    val docUrl: String,
    val lastSyncedAt: Long,
    val syncDirection: SyncDirection,
    val localRevision: Long,
    val remoteRevision: String,
    val status: FeishuRefStatus
)

/**
 * feishu-bidir-sync · 同步事件日志(design §7)。
 */
@Entity(
    tableName = "feishu_sync_event",
    indices = [Index("createdAt")]
)
data class FeishuSyncEventEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val direction: SyncDirection,
    val status: String,
    val errorMessage: String?,
    val createdAt: Long
)