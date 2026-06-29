package com.yy.writingwithai.core.ai.provider.mimo

import com.yy.writingwithai.core.ai.provider.AuthStyle
import com.yy.writingwithai.core.ai.provider.ProviderConfig

internal object MimoConfig {
    val config =
        ProviderConfig(
            id = "mimo",
            displayName = "MiMo",
            baseUrl = "https://api.xiaomimimo.com",
            endpointPath = "/anthropic/v1/messages",
            authStyle = AuthStyle.CUSTOM_HEADER,
            customAuthHeaderName = "api-key",
            defaultModel = "mimo-v2.5-pro",
            supportedModels =
            listOf(
                "mimo-v2.5-flash",
                "mimo-v2.5-pro",
                "mimo-v2.5-mini"
            )
        )
}
