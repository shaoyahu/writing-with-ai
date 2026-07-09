package com.yy.writingwithai.core.ai.provider

import com.yy.writingwithai.core.ai.api.ApiFormat
import kotlinx.serialization.Serializable

/**
 * Provider 的静态配置:URL / 认证 / 模型。
 *
 * 由 `AnthropicCompatibleAdapter` 在创建时读取，
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
) {
    init {
        // L4 修:customAuthHeaderName 不得为保留 header name，大小写不敏感。
        // 防止用户填 "Authorization" 把 apikey 覆盖到标准 header，然后被 OkHttp 二次处理 / provider 误以为 token refresh。
        customAuthHeaderName?.let { name ->
            require(name.isNotBlank()) { "customAuthHeaderName must not be blank" }
            require(name.lowercase() !in RESERVED_AUTH_HEADERS) {
                "customAuthHeaderName=$name collides with reserved auth header"
            }
        }
    }

    companion object {
        // L4 修:reserved auth header 白名单,customAuthHeaderName 不允许撞。
        // fix M1 (full-review):与 AnthropicCompatibleAdapter.RESERVED_HEADERS 对齐,加
        // "x-api-key" —— DeepSeek/Mimo 等 OpenAI-compat provider 走 Bearer 也可能在
        // 第三方自定义 provider 配置里被当作 custom header 名提交,撞上后 OkHttp
        // 二次处理或 provider 误以为 token refresh。
        val RESERVED_AUTH_HEADERS: Set<String> = setOf(
            "authorization",
            "x-api-key",
            "cookie",
            "set-cookie"
        )
    }
}
