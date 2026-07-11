package com.yy.writingwithai.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_history",
    indices = [
        Index("noteId"),
        Index("createdAt"),
        Index(value = ["noteId", "versionGroupId"])
    ]
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
    /**
     * ai-regenerate-versions:本次操作归属的版本组 id;null = 单版本(M3 行为,向后兼容)。
     * 同一 sourceText + op 一次多版本生成的 N 行共享同一非空 groupId(UUID)。
     */
    val versionGroupId: String? = null,
    /**
     * ai-regenerate-versions:本行在多版本生成中的位置(0..N-1),用于 ai_history 行排序
     * 与 UI tab 顺序。null = 单版本(M3 行为)。
     */
    val versionPosition: Int? = null
)
