package com.yy.writingwithai.core.feishu.auth

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.yy.writingwithai.core.feishu.api.FeishuError
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * feishu-user-oauth · OAuth 回调接收 Activity。
 *
 * 飞书重定向到 com.yy.writingwithai://feishu/callback?code=xxx,
 * 此 Activity 接 code 并触发 token 交换,完成后 finish。
 *
 * fix-2026-06-24-review-r1-critical:校验 URL `state` 参数 vs [FeishuAuthStore.consumeOAuthState]
 * 返回值,相等且未过期才走 exchange;不匹配直接 finish,不调 token 端点(防 CSRF / code 注入)。
 *
 * fix-2026-06-26-review-r3 CRITICAL C2:放弃 `GlobalScope + NonCancellable`。
 * 进程被杀(Android 系统低内存 / 用户从最近任务清掉)时,Activity finish() 之前
 * 启动的协程会被取消,token 写一半丢失。
 * 改为:把 pending exchange 写 [FeishuAuthStore.persistPendingExchange] 同步落盘,
 * 然后 finish()。下次 [OAuthCodeReceiver.onCreate] 启动时(也包括应用冷启动后),
 * 通过 [FeishuAuthStore.consumePendingExchange] resume 上次未完成的 exchange。
 * 这样:
 *   1. Activity 立即 finish,UX 流畅
 *   2. 进程被杀重启后,resume 拿到 code+secret 续走 token exchange
 *   3. 不依赖 GlobalScope(进程级 leak)
 */
@AndroidEntryPoint
class OAuthCodeReceiver : ComponentActivity() {

    @Inject
    lateinit var tokenProvider: UserTokenProvider

    @Inject
    lateinit var authStore: FeishuAuthStore

    @Inject
    lateinit var appScope: OAuthAppScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent.data
        val code = data?.getQueryParameter("code")
        val receivedState = data?.getQueryParameter("state")
        val errCode = data?.getQueryParameter("error")
        if (errCode != null) {
            Log.w(TAG, "OAuth error from feishu: $errCode")
            finish()
            return
        }
        if (code == null) {
            Log.w(TAG, "OAuth callback missing code: $data")
            finish()
            return
        }

        // fix r1:state 校验 — 缺失 / 不匹配 / 过期 → 不调 exchange
        val expectedState = authStore.consumeOAuthState()
        if (expectedState == null) {
            Log.w(TAG, "OAuth state validation failed: no stored state (expired or never persisted)")
            android.widget.Toast.makeText(this, "授权失败: state 已过期或不存在,请重试", android.widget.Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (receivedState == null || receivedState != expectedState) {
            Log.w(TAG, "OAuth state validation failed: expected=$expectedState received=$receivedState")
            android.widget.Toast.makeText(this, "授权失败: state 校验失败", android.widget.Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // appId 不在 redirect URL 中(飞书只回传 code),从 store 读
        val appId = authStore.getAppIdAndRefreshToken()?.first
        if (appId == null) {
            Log.w(TAG, "OAuth callback but no appId in store")
            finish()
            return
        }

        // fix C3:从 store 拿 secret(cold start 时也走 encrypted prefs 兜底,见 FeishuAuthStore.getAppSecretSnapshot)
        val appSecret = authStore.getAppSecretSnapshot("oauth")
        if (appSecret == null) {
            Log.w(TAG, "OAuth callback but appSecret missing from store")
            finish()
            return
        }

        // fix C2:同步落盘 pending exchange → 进程被杀也能 resume
        runBlocking {
            authStore.persistPendingExchange(
                code = code,
                appId = appId,
                secret = appSecret,
                requestId = "oauth"
            )
        }

        // fix C2:在 Hilt 应用 scope 上异步执行 exchange,Activity 立即 finish。
        // 进程被杀时 persistPendingExchange 已落盘 → 下次启动可 resume。
        val appCtx = applicationContext
        appScope.launch { performExchange(appCtx, code, appId, appSecret) }
        finish()
    }

    private suspend fun performExchange(ctx: android.content.Context, code: String, appId: String, appSecret: String) {
        try {
            tokenProvider.exchangeCode(appId = appId, appSecret = appSecret, code = code)
            // consume 掉 pending state(成功 → 清掉,失败也清掉,下次重新走 OAuth)
            authStore.consumePendingExchange()
            android.widget.Toast.makeText(ctx, "飞书授权成功", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: FeishuError) {
            Log.e(TAG, "OAuth exchange failed: ${e.message}", e)
            authStore.consumePendingExchange() // 清掉失败 pending,避免下次误 resume
            android.widget.Toast.makeText(
                ctx,
                "授权失败: ${e.message ?: "未知错误"}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        } catch (e: java.io.IOException) {
            Log.e(TAG, "OAuth exchange network error", e)
            authStore.consumePendingExchange()
            android.widget.Toast.makeText(
                ctx,
                "授权失败: 网络异常 ${e.message ?: ""}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            // 进程被杀:不 consume pending state,下次冷启动 resume
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "OAuth exchange unknown error", e)
            authStore.consumePendingExchange()
        }
    }

    companion object {
        private const val TAG = "OAuthCodeReceiver"
    }
}

/**
 * fix-2026-06-26-review-r3 CRITICAL C2:OAuth 后台 token exchange 用的应用级 CoroutineScope。
 * Hilt @Singleton 注入,生命周期 = 进程生命周期。Activity finish 不取消这个 scope,
 * 进程被杀时由 [FeishuAuthStore.persistPendingExchange] 落盘恢复。
 *
 * 不用 [GlobalScope] 是因为它隐式全局可写,难以测试和被监控。
 */
@Singleton
class OAuthAppScope @Inject constructor(
    @ApplicationContext context: Context
) : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)
