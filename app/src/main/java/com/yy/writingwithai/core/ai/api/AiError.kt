package com.yy.writingwithai.core.ai.api

/** AI 调用失败原因(M2 定义:映射 HTTP status / IO 异常 / SSE 解析失败)。 */
sealed interface AiError {
    data class Network(val code: Int, val detail: String) : AiError

    data class Auth(val code: Int, val detail: String) : AiError

    data class InsufficientBalance(val detail: String) : AiError

    data class ContentModeration(val detail: String) : AiError

    data class Timeout(val message: String) : AiError

    data class Deserialization(val message: String) : AiError

    data class Unknown(val code: Int?, val detail: String) : AiError

    /** 供 AiHistory 落库用的单行摘要。 */
    fun summary(): String =
        when (this) {
            is Network -> "Network($code): $detail"
            is Auth -> "Auth($code): $detail"
            is InsufficientBalance -> "InsufficientBalance: $detail"
            is ContentModeration -> "ContentModeration: $detail"
            is Timeout -> "Timeout: $message"
            is Deserialization -> "Deserialization: $message"
            is Unknown -> "Unknown($code): $detail"
        }
}
