package com.yy.writingwithai.core.feishu.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * feishu-user-oauth · 启动系统浏览器拉飞书 OAuth 授权页。
 *
 * fix-2026-06-24-review-r1-critical:生成随机 `state`,持久化到 [FeishuAuthStore],URL 携带。
 * [OAuthCodeReceiver] 校验 state 相等 + 未过期才走 token exchange,防 CSRF / code 注入。
 */
@Singleton
class OAuthLauncher
@Inject
constructor(
    private val authStore: FeishuAuthStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun launch(context: Context, appId: String) {
        // 1. 生成随机 state 并落盘(URL encode 前的原值用于比较)
        val state = generateState()
        scope.launch {
            authStore.persistOAuthState(state)
        }
        // 2. 构造授权 URL(URL 编码后给浏览器)
        val url = buildAuthorizeUrl(appId, state)
        // CustomTabs 已处理启动 flag,不需要手动加 FLAG_ACTIVITY_NEW_TASK。
        val intent = CustomTabsIntent.Builder().setShowTitle(true).build()
        try {
            intent.launchUrl(context, Uri.parse(url))
        } catch (e: ActivityNotFoundException) {
            val browser = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            // 非 Activity context 启动需要 NEW_TASK;兜底分支必加。
            browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(browser)
        }
    }

    internal fun generateState(): String = java.util.UUID.randomUUID().toString()

    internal fun buildAuthorizeUrl(appId: String, state: String): String {
        val enc = { s: String -> java.net.URLEncoder.encode(s, "UTF-8") }
        return AUTHORIZE_URL +
            "?app_id=" + enc(appId) +
            "&redirect_uri=" + enc(REDIRECT_URI) +
            "&scope=" + enc(SCOPE) +
            "&state=" + enc(state)
    }

    companion object {
        // 飞书开放后台的 redirect_url 必须填 https URL(不接受 custom scheme)。
        // 授权完成后飞书跳 https://xiaozha.nananxue.cn/callback/?code=xxx,
        // 该路径托管一个 HTML 页面(JS)读 code 跳回 custom scheme → OAuthCodeReceiver。
        internal const val REDIRECT_URI = "https://xiaozha.nananxue.cn/callback"
        private const val APP_DEEP_LINK = "com.yy.writingwithai://feishu/callback"
        private const val AUTHORIZE_URL = "https://open.feishu.cn/open-apis/authen/v1/authorize"
        private const val SCOPE = "docx:document drive:drive"
    }
}
