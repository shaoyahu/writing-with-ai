package com.yy.writingwithai.core.data.mapper

import com.yy.writingwithai.core.data.db.entity.NoteEntity
import com.yy.writingwithai.core.data.model.Note

/**
 * `NoteEntity ↔ Note` 双向映射。
 *
 * M1 是直通;后续若加字段(比如本地化的 `lastAiOp` 枚举)，集中在这里转换，
 * 避免 Room 类型泄漏到 UI 层。
 *
 * review r2 修:sync 字段(syncRevision / syncStatus / lastSyncedAt)加入双向映射，
 * 避免 NoteEntity → Note → NoteEntity round-trip 丢失同步状态(每次 upsert 把
 * SYNCED 的 note 重置为 LOCAL)。
 */
internal fun NoteEntity.toModel(): Note = Note(
    id = id,
    title = title,
    content = content,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isPinned = isPinned,
    lastAiOp = lastAiOp,
    lastAiAt = lastAiAt,
    syncRevision = syncRevision,
    syncStatus = syncStatus,
    lastSyncedAt = lastSyncedAt
)

internal fun Note.toEntity(): NoteEntity = NoteEntity(
    id = id,
    title = title,
    content = content,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isPinned = isPinned,
    lastAiOp = lastAiOp,
    lastAiAt = lastAiAt,
    syncRevision = syncRevision,
    syncStatus = syncStatus,
    lastSyncedAt = lastSyncedAt
)
