package com.yy.writingwithai.core.ai.provider.minimax

import com.yy.writingwithai.core.ai.provider.AuthStyle
import com.yy.writingwithai.core.ai.provider.ProviderConfig

internal object MinimaxConfig {
    val config =
        ProviderConfig(
            id = "minimax",
            displayName = "MiniMax",
            baseUrl = "https://api.minimaxi.com",
            endpointPath = "/anthropic/v1/messages",
            authStyle = AuthStyle.AUTHORIZATION,
            defaultModel = "MiniMax-M2.7-highspeed",
            supportedModels = listOf("MiniMax-M2.7-highspeed")
        )
}
