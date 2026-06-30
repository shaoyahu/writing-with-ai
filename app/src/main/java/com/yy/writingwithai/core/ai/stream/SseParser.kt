package com.yy.writingwithai.core.ai.stream

import java.io.EOFException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okio.BufferedSource

/**
 * SSE(Server-Sent Events)解析器。
 *
 * 从 OkHttp [BufferedSource] 中逐行读,聚合 `data: ` 行 → [SseEvent.Data],
 * 检测 `[DONE]` → [SseEvent.Done],IO 异常由外层 catch 处理,
 * 超时由 OkHttp readTimeout 提供。
 *
 * hardening-sse-and-widget-init C-1:维护 `cleanTermination` 状态机区分
 * 完整 SSE 事件(`[DONE]` / `\n\n` 终止)和截断流(socket 中途断连)。
 * 截断状态下若 buffer 内有未 emit 的 data,emit [SseEvent.Error] 而非
 * `Done`,避免下游把脏数据当成功展示给用户。
 */
internal object SseParser {
    // M4 修:per-event 长度上限 1MB,避免恶意 / 误配置 provider 发 multi-GB 单事件 OOM crash。
    private const val MAX_EVENT_LEN = 1_048_576

    // r2 修:用  转义取代字面量 BOM,避免 lint ByteOrderMark 报错。
    // hardening C-1 fix:ktlint 11.x 会把字面量 BOM 转成空字符串,改用 unicode escape 防回退。
    private const val UTF8_BOM = "﻿"

    // hardening C-1:截断时 emit 的错误消息。
    private const val TRUNCATED_MSG = "SSE stream truncated"

    fun parse(source: BufferedSource): Flow<SseEvent> = flow {
        var dataBuffer = StringBuilder()
        // L7 修:首行剥离 UTF-8 BOM (U+FEFF),某些 provider(尤其在 BOM 写入 default
        // charset 的 Windows / .NET 后端)会把 BOM 直接放在首行,导致首个 `data:` 事件
        // startsWith 失败。Okio `readUtf8Line` 不剥 BOM,这里手动处理。
        var firstLine = true
        // hardening C-1:clean termination 表示"看到完整的事件边界"。
        // - 完整 `data:` 行后接空行:置 true(空行本身 = 事件结束)
        // - `[DONE]` 命中:置 true
        // - `readUtf8Line() ?: break`(EOF 跳出):保持 false(没看到结束标志)
        var cleanTermination = false

        while (!source.exhausted()) {
            val rawLine = source.readUtf8Line() ?: break
            val line =
                if (firstLine) {
                    firstLine = false
                    rawLine.removePrefix(UTF8_BOM)
                } else {
                    rawLine
                }

            when {
                line.isEmpty() -> {
                    if (dataBuffer.isNotEmpty()) {
                        emit(SseEvent.Data(dataBuffer.toString().trimEnd()))
                        dataBuffer = StringBuilder()
                        // hardening C-1:空行表示完整事件边界。
                        cleanTermination = true
                    }
                }
                line == "[DONE]" -> {
                    if (dataBuffer.isNotEmpty()) {
                        emit(SseEvent.Data(dataBuffer.toString().trimEnd()))
                        dataBuffer = StringBuilder()
                    }
                    emit(SseEvent.Done)
                    cleanTermination = true
                    return@flow
                }
                // L7 修:RFC 8895 规定 `:` 开头的行是注释 / heartbeat(如 `:keep-alive`),
                // 应被忽略(`startsWith("data:")` 自然 fall through)。
                line.startsWith(":", ignoreCase = true) -> {
                    // no-op: comment / heartbeat
                }
                // L4 修:RFC 允许大小写,`startsWith("data:", ignoreCase = true)` 容错。
                // review r2 修:`removePrefix("data:")` 大小写敏感,当 provider 返回 `DATA:` 或 `Data:`
                // 时 removePrefix 不移除前缀,payload 残留 `DATA:...` 导致解析失败。
                // 改用 substring(5) + trimStart(),与 startsWith 的 ignoreCase 对齐。
                line.startsWith("data:", ignoreCase = true) -> {
                    // fix-2026-06-30-full-review-r1 HIGH H1:新 data 行开始累加时,清掉之前
                    // 空行 / [DONE] 留下的 cleanTermination=true。否则流 `data: first\n\ndata: second`
                    // (EOF 无尾换行)第二个事件继承 true,被误判为 Done 而非 EOF 截断。
                    cleanTermination = false
                    val payload = line.substring(5).trimStart()
                    if (payload == "[DONE]") {
                        if (dataBuffer.isNotEmpty()) {
                            emit(SseEvent.Data(dataBuffer.toString().trimEnd()))
                            dataBuffer = StringBuilder()
                        }
                        emit(SseEvent.Done)
                        cleanTermination = true
                        return@flow
                    }
                    if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                    if (dataBuffer.length + payload.length > MAX_EVENT_LEN) {
                        emit(SseEvent.Error(IllegalStateException("SSE event exceeds 1MB limit")))
                        cleanTermination = false
                        return@flow
                    }
                    dataBuffer.append(payload)
                    // hardening C-1:已读到 data 行,这一行本身合法;但单独一行不构成"clean
                    // termination",因为后面还可能需要空行 / [DONE] 来 flush。cleanTermination
                    // 在空行 / [DONE] 处才置 true。
                }
            }
        }

        // hardening C-1:出循环分两种情况。
        // 1) dataBuffer 非空 + `cleanTermination == false` → socket 在 data 行后断连
        //    (无尾随空行 / [DONE]),截断,emit Data(累计的部分) + Error(EOFException)。
        // 2) dataBuffer 空 + cleanTermination 任意 → emit Done(正常终止)。
        // 3) dataBuffer 非空 + cleanTermination == true → 已在前面的空行 flush 过一次,
        //    不应进入此分支(若有,空行后 dataBuffer 应已清空);为安全 emit Done。
        if (dataBuffer.isNotEmpty()) {
            emit(SseEvent.Data(dataBuffer.toString().trimEnd()))
            if (!cleanTermination) {
                emit(SseEvent.Error(EOFException(TRUNCATED_MSG)))
                return@flow
            }
        }
        emit(SseEvent.Done)
    }
}
