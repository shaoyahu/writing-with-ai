package com.yy.writingwithai.core.common

import android.util.Log
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 将异常映射为用户可读的简短文案，同时将完整 message 写入 logcat 供调试。
 *
 * 设计原则：
 * - 永远不将 raw exception message 暴露到 UI（可能含 API 端点、SQL 语句、内部类名）
 * - 对已知异常类型给出明确提示，未知异常 fallback 通用文案
 * - 全量 message 通过 `Log.w` 记录，方便排查
 */
fun mapToUserMessage(e: Throwable, tag: String = "UserError"): String {
    Log.w(tag, "Exception mapped to user message", e)
    return when (e) {
        is UnknownHostException -> "网络不可用，请检查连接"
        is SocketTimeoutException -> "请求超时，请稍后重试"
        is IOException -> "网络异常，请稍后重试"
        is kotlinx.coroutines.CancellationException -> throw e
        else -> "操作失败，请稍后重试"
    }
}
