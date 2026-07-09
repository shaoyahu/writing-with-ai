package com.yy.writingwithai.core.ai.stream

/**
 * SSE 解析器输出的原始事件。
 *
 * fix M8 (full-review):携带 event 字段(RFC 8895 `event: <name>` 行)，让调用方
 * 区分 `message_start` / `content_block_delta` / `message_stop` / `error` 等事件。
 * 之前 `event:` 行被忽略,Anthropic `message_stop` / `error` 等关键事件无法路由。
 */
sealed interface SseEvent {
    /**
     * @param content data 行 payload(已 trimEnd)。
     * @param eventName `event:` 行携带的事件名,缺省 `null`(未携带 event 字段)。
     */
    data class Data(val content: String, val eventName: String? = null) : SseEvent

    data object Done : SseEvent

    data class Error(val cause: Throwable) : SseEvent
}
