package com.yy.writingwithai.core.ai.provider

/**
 * Provider 的静态配置:URL / 认证 / 模型。
 *
 * 由 `AnthropicCompatibleAdapter` 在创建时读取,
 * 业务侧只传 [com.yy.writingwithai.core.ai.api.AiRequest] + [com.yy.writingwithai.core.ai.api.AiCredentials]。
 */
data class ProviderConfig(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val endpointPath: String,
    val authStyle: AuthStyle,
    val customAuthHeaderName: String? = null,
    val defaultModel: String,
    val supportedModels: List<String>,
    val customHeaders: Map<String, String> = emptyMap()
)
