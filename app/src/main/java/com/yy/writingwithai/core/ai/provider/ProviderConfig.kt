package com.yy.writingwithai.core.ai.provider

import kotlinx.serialization.Serializable

/**
 * API 响应格式。
 * - ANTHROPIC: 顶层 `system`, `{"delta":{"text":"..."}}`
 * - OPENAI: `system` 在 messages 数组, `{"choices":[{"delta":{"content":"..."}}]}`
 */
@Serializable
enum class ApiFormat { ANTHROPIC, OPENAI }

/**
 * Provider 的静态配置:URL / 认证 / 模型。
 *
 * 由 `AnthropicCompatibleAdapter` 在创建时读取,
 * 业务侧只传 [com.yy.writingwithai.core.ai.api.AiRequest] + [com.yy.writingwithai.core.ai.api.AiCredentials]。
 */
@Serializable
data class ProviderConfig(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val endpointPath: String,
    val authStyle: AuthStyle,
    val customAuthHeaderName: String? = null,
    val defaultModel: String,
    val supportedModels: List<String>,
    val customHeaders: Map<String, String> = emptyMap(),
    val apiFormat: ApiFormat = ApiFormat.ANTHROPIC
)
