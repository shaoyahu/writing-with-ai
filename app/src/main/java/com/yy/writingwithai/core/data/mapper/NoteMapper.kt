package com.yy.writingwithai.core.data.mapper

import com.yy.writingwithai.core.data.db.entity.NoteEntity
import com.yy.writingwithai.core.data.model.Note

/**
 * `NoteEntity ↔ Note` 双向映射。
 *
 * M1 是直通;后续若加字段(比如本地化的 `lastAiOp` 枚举),集中在这里转换,
 * 避免 Room 类型泄漏到 UI 层。
 */
internal fun NoteEntity.toModel(): Note =
    Note(
        id = id,
        title = title,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isPinned = isPinned,
        lastAiOp = lastAiOp,
        lastAiAt = lastAiAt,
    )

internal fun Note.toEntity(): NoteEntity =
    NoteEntity(
        id = id,
        title = title,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isPinned = isPinned,
        lastAiOp = lastAiOp,
        lastAiAt = lastAiAt,
    )
