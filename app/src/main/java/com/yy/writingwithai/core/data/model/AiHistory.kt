package com.yy.writingwithai.core.data.model

/** UI 侧 AiHistory(不带 Room 注解)。 */
data class AiHistory(
    val id: String,
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
    val truncated: Boolean,
    val error: String?
)
