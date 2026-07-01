package com.yy.writingwithai.core.ai.api

import kotlinx.coroutines.flow.Flow

/**
 * Provider 适配器 SPI:屏蔽 HTTP 细节，统一为 [Flow]<[AiStreamEvent]>。
 *
 * M2 提供三个实现:[FakeAiProvider]、[AnthropicCompatibleAdapter];
 * M5 真联调时换真 adapter(配真 credentials)。
 */
interface AiProvider {
    val id: String
    val displayName: String
    val supportedModels: List<String>

    /**
     * 无用户偏好(`selected_model_<id>` 未设)时使用的 fallback model。
     *
     * 语义:
     * - **fallback 入口，不是推荐体验**。`ModelManagementViewModel` 在用户新装 + 配置
     *   apikey 后会用 [defaultModel] 引导值预写 selected_model，从而保证「apikey 已落
     *   → 必有 selectedModel」不变式。
     * - 因此 [defaultModel] 应当选择**与该 provider 默认体验一致的模型**(建议为该
     *   provider 官方推荐的 balanced / pro 档)，而不是 flash / mini / high-speed 类
     *   极速入口 — 否则新装用户会被静默推入「选 pro 实际调 flash」类的退化体验。
     * - 业务侧统一走 [resolveActualModel] 算「实际将调用」，不要直接 `?: defaultModel`
     *   / `?: supportedModels.first()` 任一种。
     */
    val defaultModel: String

    /** 按 token 粒度流式返回(含 Started/Delta/Usage/Failed/Done)。 */
    fun stream(request: AiRequest, credentials: AiCredentials): Flow<AiStreamEvent>
}
