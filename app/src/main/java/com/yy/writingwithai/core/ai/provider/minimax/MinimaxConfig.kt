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
            defaultModel = "MiniMax-M2.7",
            supportedModels =
            listOf(
                "MiniMax-M2.7-highspeed",
                "MiniMax-M2.7",
                "MiniMax-M2.5-highspeed",
                "MiniMax-M2.5",
                "MiniMax-M2.1-highspeed",
                "MiniMax-M2.1",
                "MiniMax-M2",
                "MiniMax-M3"
            )
        )
}
