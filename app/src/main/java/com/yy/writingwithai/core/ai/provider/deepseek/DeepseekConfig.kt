package com.yy.writingwithai.core.ai.provider.deepseek

import com.yy.writingwithai.core.ai.provider.AuthStyle
import com.yy.writingwithai.core.ai.provider.ProviderConfig

internal object DeepseekConfig {
    val config =
        ProviderConfig(
            id = "deepseek",
            displayName = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            endpointPath = "/anthropic/v1/messages",
            authStyle = AuthStyle.X_API_KEY,
            defaultModel = "deepseek-v4-flash",
            supportedModels = listOf("deepseek-v4-flash", "deepseek-v4-pro"),
        )
}
