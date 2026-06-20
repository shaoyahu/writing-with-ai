package com.yy.writingwithai.core.data.mapper

import com.yy.writingwithai.core.data.db.entity.AiHistoryEntity
import com.yy.writingwithai.core.data.model.AiHistory

internal fun AiHistoryEntity.toModel(): AiHistory = AiHistory(
    id = id,
    noteId = noteId,
    providerId = providerId,
    model = model,
    op = op,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    totalTokens = totalTokens,
    durationMs = durationMs,
    createdAt = createdAt,
    inputSnapshot = inputSnapshot,
    outputSnapshot = outputSnapshot,
    truncated = truncated,
    error = error
)

internal fun AiHistory.toEntity(): AiHistoryEntity = AiHistoryEntity(
    id = id,
    noteId = noteId,
    providerId = providerId,
    model = model,
    op = op,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    totalTokens = totalTokens,
    durationMs = durationMs,
    createdAt = createdAt,
    inputSnapshot = inputSnapshot,
    outputSnapshot = outputSnapshot,
    truncated = truncated,
    error = error
)
