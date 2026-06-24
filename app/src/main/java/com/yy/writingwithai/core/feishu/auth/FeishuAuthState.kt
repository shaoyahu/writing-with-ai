package com.yy.writingwithai.core.feishu.auth

/**
 * feishu-oauth-flow · 设置页「飞书授权」section 状态机(design D5)。
 *
 * - [DISCONNECTED]:无 app_id/secret
 * - [CONFIGURED]:有凭证但 token 未取过
 * - [TOKEN_FETCHING]:正在 POST 取 token
 * - [CONNECTED]:token 有效
 * - [FAILED]:最近一次取 token 失败
 * - [KEYSTORE_UNAVAILABLE]:EncryptedSharedPreferences 初始化失败,无法安全存凭据
 */
enum class FeishuAuthState {
    DISCONNECTED,
    CONFIGURED,
    TOKEN_FETCHING,
    CONNECTED,
    FAILED,
    KEYSTORE_UNAVAILABLE
}
