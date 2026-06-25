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
    // M4 修:per-event 长度上限 1MB,避免恶意 / 误配置 provider 发 multi-GB 单事件 OOM crash。
    private const val MAX_EVENT_LEN = 1_048_576

    fun parse(source: BufferedSource): Flow<SseEvent> = flow {
        var dataBuffer = StringBuilder()
        // L7 修:首行剥离 UTF-8 BOM(﻿),某些 provider(尤其在 BOM 写入 default
        // charset 的 Windows / .NET 后端)会把 BOM 直接放在首行,导致首个 `data:` 事件
        // startsWith 失败。Okio `readUtf8Line` 不剥 BOM,这里手动处理。
        var firstLine = true

        while (!source.exhausted()) {
            val rawLine = source.readUtf8Line() ?: break
            val line =
                if (firstLine) {
                    firstLine = false
                    rawLine.removePrefix("﻿")
                } else {
                    rawLine
                }

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
                // L7 修:RFC 8895 规定 `:` 开头的行是注释 / heartbeat(如 `:keep-alive`),
                // 应被忽略(`startsWith("data:")` 自然 fall through)。
                line.startsWith(":", ignoreCase = true) -> {
                    // no-op: comment / heartbeat
                }
                // L4 修:RFC 允许大小写,`startsWith("data:", ignoreCase = true)` 容错。
                line.startsWith("data:", ignoreCase = true) -> {
                    val payload = line.removePrefix("data:").trimStart()
                    if (payload == "[DONE]") {
                        emit(SseEvent.Done)
                        return@flow
                    }
                    if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                    if (dataBuffer.length + payload.length > MAX_EVENT_LEN) {
                        emit(SseEvent.Error(IllegalStateException("SSE event exceeds 1MB limit")))
                        return@flow
                    }
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
