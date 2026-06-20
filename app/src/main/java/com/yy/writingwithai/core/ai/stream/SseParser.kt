package com.yy.writingwithai.core.ai.stream

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okio.BufferedSource

/**
 * SSE(Server-Sent Events)解析器。
 *
 * 从 OkHttp [BufferedSource] 中逐行读,聚合 `data: ` 行 → [SseEvent.Data],
 * 检测 `[DONE]` → [SseEvent.Done],IO 异常由外层 catch 处理,
 * 超时由 OkHttp readTimeout 提供。
 */
internal object SseParser {
    fun parse(source: BufferedSource): Flow<SseEvent> = flow {
        var dataBuffer = StringBuilder()

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break

            when {
                line.isEmpty() -> {
                    if (dataBuffer.isNotEmpty()) {
                        emit(SseEvent.Data(dataBuffer.toString().trimEnd()))
                        dataBuffer = StringBuilder()
                    }
                }
                line == "[DONE]" -> {
                    emit(SseEvent.Done)
                    return@flow
                }
                line.startsWith("data:") -> {
                    val payload = line.removePrefix("data:").trimStart()
                    if (payload == "[DONE]") {
                        emit(SseEvent.Done)
                        return@flow
                    }
                    if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                    dataBuffer.append(payload)
                }
            }
        }

        if (dataBuffer.isNotEmpty()) {
            emit(SseEvent.Data(dataBuffer.toString().trimEnd()))
        }
        emit(SseEvent.Done)
    }
}
