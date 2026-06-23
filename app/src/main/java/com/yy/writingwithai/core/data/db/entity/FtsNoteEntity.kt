package com.yy.writingwithai.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * search-enhancement · FTS4 全文搜索虚拟表。
 * contentEntity=NoteEntity 让 Room 自动同步主表内容。
 * FTS4 要求 rowid 为 INTEGER 类型主键。
 */
@Fts4(contentEntity = NoteEntity::class)
@Entity(tableName = "notes_fts")
data class FtsNoteEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val title: String,
    val content: String
)
