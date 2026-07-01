package com.yy.writingwithai.core.ai.api

import kotlinx.serialization.Serializable

/**
 * API 响应格式。
 * - ANTHROPIC: 顶层 `system`, `{"delta":{"text":"..."}}`
 * - OPENAI: `system` 在 messages 数组， `{"choices":[{"delta":{"content":"..."}}]}`
 *
 * polish-review-r2 M10:从 `core/ai/provider/ProviderConfig.kt` 顶层挪到 `core/ai/api/`,
 * 是抽象层概念(provider 用哪条 SSE 路径)，跟 `AiRequest.apiFormatOverride` 同包。
 */
@Serializable
enum class ApiFormat { ANTHROPIC, OPENAI }
