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
            endpointPath = "/chat/completions",
            authStyle = AuthStyle.AUTHORIZATION,
            defaultModel = "deepseek-v4-flash",
            supportedModels = listOf("deepseek-v4-flash", "deepseek-v4-pro"),
            apiFormat = ApiFormat.OPENAI
        )
}
