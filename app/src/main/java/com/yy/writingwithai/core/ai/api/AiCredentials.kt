package com.yy.writingwithai.core.ai.api

/** Provider 认证凭证(M2 用 fake,M4 从 EncryptedSharedPreferences 读真 apikey)。 */
data class AiCredentials(
    val apikey: String,
    val baseUrlOverride: String? = null,
)
