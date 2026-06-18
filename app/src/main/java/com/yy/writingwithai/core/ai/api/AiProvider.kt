package com.yy.writingwithai.core.ai.api

import kotlinx.coroutines.flow.Flow

/**
 * Provider 适配器 SPI:屏蔽 HTTP 细节,统一为 [Flow]<[AiStreamEvent]>。
 *
 * M2 提供三个实现:[FakeAiProvider]、[AnthropicCompatibleAdapter];
 * M5 真联调时换真 adapter(配真 credentials)。
 */
interface AiProvider {
    val id: String
    val displayName: String
    val supportedModels: List<String>

    /** 按 token 粒度流式返回(含 Started/Delta/Usage/Failed/Done)。 */
    fun stream(
        request: AiRequest,
        credentials: AiCredentials,
    ): Flow<AiStreamEvent>
}
