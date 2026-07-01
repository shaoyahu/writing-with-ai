package com.yy.writingwithai.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * media-attachment-infrastructure · 笔记附件表。
 */
@Entity(
    tableName = "note_attachments",
    // fix-2026-06-25-review-r1 H1:JOIN attachments ON noteId = ? 走全表扫，
    // 加 `Index("noteId")` 让查询走索引。
    indices = [Index("noteId")],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class NoteAttachmentEntity(
    @PrimaryKey
    val id: String,
    val noteId: String,
    val mimeType: String,
    val localPath: String,
    @ColumnInfo(defaultValue = "0")
    val fileSize: Long = 0,
    val createdAt: Long
)
