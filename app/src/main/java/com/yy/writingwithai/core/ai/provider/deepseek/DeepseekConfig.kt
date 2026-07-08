package com.yy.writingwithai.core.ai.provider.deepseek

import com.yy.writingwithai.core.ai.api.ApiFormat
import com.yy.writingwithai.core.ai.provider.AuthStyle
import com.yy.writingwithai.core.ai.provider.ProviderConfig

internal object DeepseekConfig {
    val config =
        ProviderConfig(
            id = "deepseek",
            displayName = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            // CLAUDE.md 关键决策:"三家统一走 Anthropic 兼容"。
            // Deepseek 也支持 /anthropic/v1/messages + x-api-key，
            // 统一走 Anthropic 协议而非 OpenAI /chat/completions。
            endpointPath = "/anthropic/v1/messages",
            authStyle = AuthStyle.X_API_KEY,
            defaultModel = "deepseek-v4-pro",
            supportedModels = listOf("deepseek-v4-flash", "deepseek-v4-pro"),
            apiFormat = ApiFormat.ANTHROPIC
        )
}
