package com.yy.writingwithai.core.ai.stream

/** SSE 解析器输出的原始事件。 */
sealed interface SseEvent {
    data class Data(val content: String) : SseEvent

    data object Done : SseEvent

    data class Error(val cause: Throwable) : SseEvent
}
