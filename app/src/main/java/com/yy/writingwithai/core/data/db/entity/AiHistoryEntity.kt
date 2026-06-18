package com.yy.writingwithai.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_history",
    indices = [Index("noteId"), Index("createdAt")],
)
data class AiHistoryEntity(
    @PrimaryKey val id: String,
    val noteId: String?,
    val providerId: String,
    val model: String,
    val op: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val durationMs: Long,
    val createdAt: Long,
    val inputSnapshot: String,
    val outputSnapshot: String,
    val truncated: Boolean = false,
    val error: String? = null,
)
