package com.yy.writingwithai.core.ai.api

/** 所有 AI 调用的流式事件(M2 定义,M3 UI 消费)。 */
sealed interface AiStreamEvent {
    data object Started : AiStreamEvent

    data class Delta(val text: String) : AiStreamEvent

    data class Usage(
        val inputTokens: Int,
        val outputTokens: Int,
        val totalTokens: Int,
    ) : AiStreamEvent

    data class Failed(val error: AiError, val recoverable: Boolean) : AiStreamEvent

    data object Done : AiStreamEvent
}
