package com.yy.writingwithai.core.ai.api

/** Provider 认证凭证(M2 用 fake,M4 从 EncryptedSharedPreferences 读真 apikey)。
 *
 * fix-full-review:重写 toString() 遮蔽 apikey，防止日志 / 调试器 / 异常消息泄露密钥。
 */
data class AiCredentials(
    val apikey: String,
    val baseUrlOverride: String? = null
) {
    override fun toString(): String = "AiCredentials(apikey=***REDACTED***, baseUrlOverride=$baseUrlOverride)"
}
