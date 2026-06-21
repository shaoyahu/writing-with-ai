package com.yy.writingwithai.core.ai.api

/**
 * 一次 AI 调用(单个 provider + 模型)的请求体。
 *
 * model-management-detail-dropdown X 方案:[apiFormatOverride] 可由用户在详情页覆盖
 * (同 provider 走 OpenAI / Anthropic 不同 endpoint);null → provider 走 [com.yy.writingwithai.core.ai.provider.ProviderConfig.apiFormat] 兜底。
 */
data class AiRequest(
    val op: WritingOp,
    val sourceText: String,
    val model: String,
    /** custom-prompt-template 增量:用户自定义 system prompt;null → provider 走 fallback 默认。 */
    val systemPrompt: String? = null,
    /** model-management-detail-dropdown X 方案:用户在详情页选的 API 格式覆盖;null → fallback ProviderConfig.apiFormat。 */
    val apiFormatOverride: ApiFormat? = null
)
