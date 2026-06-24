package com.yy.writingwithai.core.update

/**
 * app-self-hosted-update · 检查更新失败分类。
 *
 * UI 层按类型给用户不同提示文案;不暴露底层异常细节(PII 防御)。
 */
sealed class UpdateError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** 网络层失败(超时/连接 reset/DNS)。 */
    class Network(cause: Throwable? = null) : UpdateError("network error", cause)

    /** HTTP 4xx/5xx(服务端问题)。 */
    class Http(val code: Int) : UpdateError("http $code")

    /** 响应体无法解析(服务端 schema 不一致)。 */
    class Parse(cause: Throwable? = null) : UpdateError("parse error", cause)

    /** APK 文件校验失败(SHA-256 不匹配)。 */
    class ChecksumMismatch(val expected: String, val actual: String) :
        UpdateError("checksum mismatch: expected=$expected actual=$actual")
}
