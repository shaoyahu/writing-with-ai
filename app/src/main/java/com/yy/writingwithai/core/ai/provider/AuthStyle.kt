package com.yy.writingwithai.core.ai.provider

/** Provider 的 HTTP 认证方式。 */
enum class AuthStyle {
    /** `Authorization: Bearer $apikey` */
    AUTHORIZATION,

    /** `x-api-key: $apikey` */
    X_API_KEY,

    /** `$customAuthHeaderName: $apikey`(mimo 用 `api-key`) */
    CUSTOM_HEADER
}
