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
    val error: String?,
    /**
     * ai-regenerate-versions:多版本生成的同组 id;null = 单版本。
     */
    val versionGroupId: String? = null,
    /**
     * ai-regenerate-versions:本行在多版本生成中的位置(0..N-1);null = 单版本。
     */
    val versionPosition: Int? = null
)
