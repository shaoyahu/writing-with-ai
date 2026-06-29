package com.yy.writingwithai.core.feishu.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.yy.writingwithai.core.feishu.auth.isKeystoreError as isKeystoreErrorFn
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyStoreException
import java.security.UnrecoverableKeyException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    /**
     * ux-2026-06-28 · 启动飞书 OAuth(user_access_token 流程)。
     *
     * suspend 函数,持久化阶段在 [Dispatchers.IO] 跑(避免 Main 线程 block);
     * 启动 CustomTabs/Activity 必须回到调用方所在 dispatcher(典型是 Main),
     * 所以用 [coroutineContext] 当前调度器做 Activity 启动。
     *
     * @param appId 飞书开放后台 → 应用凭证 → App ID
     * @param appSecret 飞书开放后台 → 应用凭证 → App Secret(token exchange 必需;
     *               不入 URL,只落 EncryptedSharedPreferences 供 OAuthCodeReceiver 读)
     */
    suspend fun launch(context: Context, appId: String, appSecret: String) {
        // 0. 落 appId + appSecret,供 OAuthCodeReceiver 首次回调时取回完成 token exchange
        // (exchangeCode 用 appSecret 调 /auth/v3/app_access_token 拿 app_access_token)。
        // 同步落盘:EncryptedSharedPreferences 写入 <10ms,正常路径可接受;不写则回调到达时
        // OAuthCodeReceiver 报"appSecret missing"假失败。
        try {
            withContext(Dispatchers.IO) {
                authStore.setAppId(appId)
                authStore.persistAppSecret(REQUEST_ID_OAUTH, appSecret)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "OAuthLauncher: failed to persist appId/secret to encrypted prefs", e)
            if (isKeystoreErrorFn(e)) throw OAuthLaunchException.KeystoreUnavailable(e)
            throw e
        }
        // 1. 生成随机 state 并落盘(URL encode 前的原值用于比较)
        val state = generateState()
        // review r2 修:persistOAuthState 必须在 launchUrl 前完成,否则回调到达时
        // consumeOAuthState() 找不到 state → 用户看到"state expired"假失败。
        try {
            withContext(Dispatchers.IO) { authStore.persistOAuthState(state) }
        } catch (e: Throwable) {
            Log.e(TAG, "OAuthLauncher: failed to persist OAuth state", e)
            if (isKeystoreErrorFn(e)) throw OAuthLaunchException.KeystoreUnavailable(e)
            throw e
        }
        // 2. 构造授权 URL(URL 编码后给浏览器)
        val url = buildAuthorizeUrl(appId, state)
        // 关键:CustomTabsIntent.launchUrl 内部走 ContextCompat.startActivity,
        // 调用方传 Application context 时必须显式加 FLAG_ACTIVITY_NEW_TASK,否则
        // 抛 android.util.AndroidRuntimeException(非 ActivityNotFoundException),
        // 被外层 catch (Throwable) 兜住 → 误报「未找到可用的浏览器」。
        //
        // 之前注释「CustomTabs 已处理启动 flag,不需要手动加」只在 Activity context 下
        // 成立;Application / RemoteService / null context 一律崩。这里显式加 NEW_TASK,
        // caller 传啥 context 都能跑。
        val customTabs = CustomTabsIntent.Builder().setShowTitle(true).build()
        customTabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            customTabs.launchUrl(context, Uri.parse(url))
            Log.i(TAG, "OAuthLauncher: CustomTabs launched url=$url")
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "OAuthLauncher: no CustomTabs, fall back to plain ACTION_VIEW", e)
            val browser = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            // 非 Activity context 启动需要 NEW_TASK;兜底分支必加。
            browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(browser)
            } catch (e2: Throwable) {
                Log.e(TAG, "OAuthLauncher: ACTION_VIEW fallback also failed", e2)
                throw OAuthLaunchException.LaunchFailed(e2)
            }
        } catch (e: Throwable) {
            // 防御:即使加了 NEW_TASK,某些 OEM ROM 上 CustomTabs 仍可能抛其他 RuntimeException
            // (比如 SecurityException / IllegalStateException)。最后兜底走 ACTION_VIEW 系统浏览器。
            Log.w(TAG, "OAuthLauncher: CustomTabs threw unexpected, fall back to ACTION_VIEW", e)
            val browser = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(browser)
            } catch (e2: Throwable) {
                Log.e(TAG, "OAuthLauncher: ACTION_VIEW fallback also failed", e2)
                throw OAuthLaunchException.LaunchFailed(e2)
            }
        }
    }

    internal fun generateState(): String = java.util.UUID.randomUUID().toString()

    internal fun buildAuthorizeUrl(appId: String, state: String): String {
        val enc = { s: String -> java.net.URLEncoder.encode(s, "UTF-8") }
        // ux-2026-06-28:迁飞书 OAuth v2:
        // - host 从 open.feishu.cn 改 accounts.feishu.cn(v2 授权专属域名)
        // - 参数 app_id 改 client_id;新增 response_type=code(必需)
        // 参考 https://open.feishu.cn/document/authentication-management/access-token/obtain-oauth-code
        // fix-2026-06-27-review-r4 L1:+ 拼接改 string template,可读性更好。
        return "$AUTHORIZE_URL" +
            "?client_id=${enc(appId)}" +
            "&response_type=code" +
            "&redirect_uri=${enc(REDIRECT_URI)}" +
            "&scope=${enc(SCOPE)}" +
            "&state=${enc(state)}"
    }

    /**
     * ux-2026-06-28:区分 OAuth 启动失败类型,便于 UI 给用户准确指引。
     *
     * - [KeystoreUnavailable]:系统密钥库不可用(ColorOS 上 rkpd_client 通信失败、
     *   AndroidKeyStore 未解锁、设备被管理员禁用)。UI 应提示用户检查系统密钥库,
     *   而不是反复重试启动。
     * - [LaunchFailed]:CustomTabs/浏览器启动失败,或 OAuth state 持久化失败。
     *   UI 可建议用户重试或更换默认浏览器。
     */
    sealed class OAuthLaunchException(message: String, cause: Throwable? = null) :
        RuntimeException(message, cause) {
        class KeystoreUnavailable(cause: Throwable) :
            OAuthLaunchException("Android Keystore unavailable", cause)

        class LaunchFailed(cause: Throwable) :
            OAuthLaunchException("OAuth launch failed", cause)
    }

    companion object {
        private const val TAG = "OAuthLauncher"

        // 飞书开放后台的 redirect_url 必须填 https URL(不接受 custom scheme)。
        // 授权完成后飞书跳 https://xiaozha.nananxue.cn/callback/?code=xxx,
        // 该路径托管一个 HTML 页面(JS)读 code 跳回 custom scheme → OAuthCodeReceiver。
        // ux-2026-06-28:从 internal 改为公开,供 FeishuAuthScreen 展示给用户,
        // 用户在飞书开放后台「安全设置 → 重定向 URL」必须填这一项。
        const val REDIRECT_URI: String = "https://xiaozha.nananxue.cn/callback"

        // ux-2026-06-28:与 UserTokenProvider.REQUEST_ID_OAUTH 同值("oauth"),
        // 用同一把 key 持久化 secret 让 OAuthCodeReceiver 回调时能取回。
        const val REQUEST_ID_OAUTH: String = "oauth"

        private const val APP_DEEP_LINK = "com.yy.writingwithai://feishu/callback"

        // ux-2026-06-28:迁 v2 — accounts.feishu.cn 是飞书 OAuth 授权专属域名(open.feishu.cn 已不服务 authorize 端点)
        private const val AUTHORIZE_URL = "https://accounts.feishu.cn/open-apis/authen/v1/authorize"
        private const val SCOPE = "docx:document drive:drive offline_access"
    }
}

/**
 * ux-2026-06-28:识别抛出的异常是否源于 Android Keystore / EncryptedSharedPreferences。
 *
 * 类型检测,不走字符串嗅探,避免网络错误 message 含 "keystore" 字样误分类。
 * 触发场景:
 * - AndroidKeyStore 创建 MasterKey 失败抛 [KeyStoreException] / [KeyPermanentlyInvalidatedException]
 * - EncryptedSharedPreferences 加密层失败抛 [GeneralSecurityException] / [InvalidKeyException]
 * - rkpd_client 通信失败抛 [KeyPermanentlyInvalidatedException]
 *
 * 沿 cause 链查任一节点匹配即返回 true。
 *
 * 提到 top-level 而非 [OAuthLauncher] 私有方法,便于单测直接调用(避免反射构造
 * Hilt 注入类),且函数本身无状态依赖。
 */
internal fun isKeystoreError(t: Throwable): Boolean {
    var cur: Throwable? = t
    while (cur != null) {
        if (cur is KeyStoreException ||
            cur is KeyPermanentlyInvalidatedException ||
            cur is InvalidKeyException ||
            cur is UnrecoverableKeyException ||
            cur is GeneralSecurityException
        ) {
            return true
        }
        cur = cur.cause
    }
    return false
}
