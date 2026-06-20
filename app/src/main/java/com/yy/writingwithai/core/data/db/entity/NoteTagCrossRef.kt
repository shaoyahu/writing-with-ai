package com.yy.writingwithai.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * 笔记 ↔ 标签 多对多交叉表。
 *
 * - `noteId`: 外键到 [NoteEntity.id](逻辑外键,M1 不开 `ForeignKey` 注解,避免级联约束与删除逻辑打架)
 * - `tag`: 自由文本(M1 不规范化,不做 `tags` 表)
 *
 * 联合主键 `(noteId, tag)` 保证同一笔记不会挂重复 tag;
 * 两个独立二级索引让"按 tag 查所有笔记"和"按笔记查所有 tag"都走索引。
 *
 * 见 [openspec.changes.quick-note-feature.specs.quick-note.spec] §"Tag many-to-many"。
 */
@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tag"],
    indices = [
        Index("noteId"),
        Index("tag")
    ]
)
data class NoteTagCrossRef(
    val noteId: String,
    val tag: String
)
