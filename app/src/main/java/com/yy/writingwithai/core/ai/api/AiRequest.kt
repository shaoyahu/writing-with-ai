package com.yy.writingwithai.core.ai.api

/** 一次 AI 调用(单个 provider + 模型)的请求体。 */
data class AiRequest(
    val op: WritingOp,
    val sourceText: String,
    val model: String,
    /** custom-prompt-template 增量:用户自定义 system prompt;null → provider 走 fallback 默认。 */
    val systemPrompt: String? = null
)
