package com.yy.writingwithai.core.ai.provider

import kotlinx.serialization.Serializable

/** Provider 的 HTTP 认证方式。 */
@Serializable
enum class AuthStyle {
    /** `Authorization: Bearer $apikey` */
    AUTHORIZATION,

    /** `x-api-key: $apikey` */
    X_API_KEY,

    /** `$customAuthHeaderName: $apikey`(mimo 用 `api-key`) */
    CUSTOM_HEADER
}
