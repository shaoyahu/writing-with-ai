package com.yy.writingwithai.core.feishu.auth

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.yy.writingwithai.R
import com.yy.writingwithai.core.feishu.api.FeishuError
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * feishu-user-oauth · OAuth 回调接收 Activity。
 *
 * 飞书重定向到 com.yy.writingwithai://feishu/callback?code=xxx,
 * 此 Activity 接 code 并触发 token 交换，完成后 finish。
 *
 * fix-2026-06-24-review-r1-critical:校验 URL `state` 参数 vs [FeishuAuthStore.consumeOAuthState]
 * 返回值，相等且未过期才走 exchange;不匹配直接 finish，不调 token 端点(防 CSRF / code 注入)。
 *
 * fix-2026-06-26-review-r3 CRITICAL C2:放弃 `GlobalScope + NonCancellable`。
 * 进程被杀(Android 系统低内存 / 用户从最近任务清掉)时，Activity finish() 之前
 * 启动的协程会被取消，token 写一半丢失。
 * 改为:把 pending exchange 写 [FeishuAuthStore.persistPendingExchange] 同步落盘，
 * 然后 finish()。下次 [OAuthCodeReceiver.onCreate] 启动时(也包括应用冷启动后),
 * 通过 [FeishuAuthStore.consumePendingExchange] resume 上次未完成的 exchange。
 * 这样:
 *   1. Activity 立即 finish,UX 流畅
 *   2. 进程被杀重启后，resume 拿到 code+secret 续走 token exchange
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

        // ux-2026-06-29 re-delivery 兜底:检测到 pending exchange 还在，意味着上一轮
        // onCreate 已成功 persist 但后台协程没正常完成(进程被杀 / Toast NPE 崩协程 /
        // 系统重投 intent)。
        //
        // 处理:消费 pending exchange 拿到上轮的 code/appId/secret，在 appScope 上重新
        // 跑 performExchange(同 finish() 前 launch，跟正常路径一致)。这样:
        // - 用户当前 OAuth 流程不丢，能完成
        // - 不再"静默 finish"把用户卡死
        // - state 校验跳过(pending 才是真相，state 已过期无关)
        //
        // 关键:只能调 [hasPendingExchange](纯读)，不能调 [consumeOAuthState](有副作用，
        // 会从 prefs 删除 state)。
        if (authStore.hasPendingExchange()) {
            val pending = authStore.consumePendingExchange()
            if (pending != null) {
                // H3 fix:re-delivery 也校验 code 一致性 — 当前 intent 的 code 必须匹配
                // pending 中的 code(说明是同一 OAuth 流程的重投)，否则丢弃(可能是攻击者
                // 伪造 code 的重投，利用已存在的 pending 跳过 state 校验)。
                if (code != pending.code) {
                    Log.w(
                        TAG,
                        "OAuth re-delivery: code mismatch (intent=${code.take(
                            8
                        )}... vs pending=${pending.code.take(8)}...), discarding"
                    )
                    // pending 已 consume，不会再 resume;走下面 state 校验(可能也过期了)
                } else {
                    Log.i(
                        TAG,
                        "OAuth callback re-delivery: resuming pending exchange (code=${pending.code.take(8)}...)"
                    )
                    val appCtx = applicationContext
                    // hardening H-7:re-persist 在 launch exchange 之前。
                    // 旧实现 consume 完不重 persist，如果 performExchange 期间进程被杀，
                    // 下次启动没有 pending 可 consume，用户必须重走 OAuth。同步 join()
                    // 阻塞主线程 ~100-200ms(EncryptedSharedPreferences 落盘)，只发生在
                    // re-delivery 罕见路径，UX 可接受。
                    lifecycleScope.launch {
                        performReDelivery(
                            code = code,
                            pending = pending,
                            authStore = authStore,
                            redirectUri = OAuthLauncher.REDIRECT_URI,
                            appCtx = appCtx
                        )
                    }
                    startMainActivity()
                    finish()
                    return
                }
            }
            // pending 已过期(>10 min)被 consumePendingExchange 内部过滤掉 → 当成
            // 没有 pending，继续走下面 state 校验(可能也过期了，显示 error 让用户重试)
            Log.w(TAG, "OAuth callback re-delivery: hasPendingExchange=true but consume returned null (expired)")
        }

        // fix r1:state 校验 — 缺失 / 不匹配 / 过期 → 不调 exchange
        val expectedState = authStore.consumeOAuthState()
        if (expectedState == null) {
            // 没 pending + state 为空 = 真正的过期(>5 min TTL 或从未启动 OAuth 流程)
            Log.w(TAG, "OAuth state validation failed: no stored state (expired or never persisted)")
            android.widget.Toast.makeText(
                this,
                getString(R.string.feishu_oauth_state_expired),
                android.widget.Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }
        if (receivedState == null || receivedState != expectedState) {
            Log.w(TAG, "OAuth state validation failed: expected=$expectedState received=$receivedState")
            android.widget.Toast.makeText(
                this,
                getString(R.string.feishu_oauth_state_mismatch),
                android.widget.Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        // appId 不在 redirect URL 中(飞书只回传 code)，从 store 读。
        // ux-2026-06-28:用 getAppIdSnapshot() — 首次 OAuth 时 refreshToken 尚未写入，
        // 原 getAppIdAndRefreshToken() 会因 refreshToken 为 null 而整体返回 null,
        // 导致首次回调永远拿不到 appId。OAuthLauncher.launch 现在会先 setAppId,
        // 所以这里用 snapshot 直接读即可。
        val appId = authStore.getAppIdSnapshot()
        if (appId == null) {
            Log.w(TAG, "OAuth callback but no appId in store")
            finish()
            return
        }

        // fix C3:从 store 拿 secret(cold start 时也走 encrypted prefs 兜底，见 FeishuAuthStore.getAppSecretSnapshot)
        val appSecret = authStore.getAppSecretSnapshot("oauth")
        if (appSecret == null) {
            Log.w(TAG, "OAuth callback but appSecret missing from store")
            finish()
            return
        }

        // fix C2:异步落盘 pending exchange → 进程被杀也能 resume
        // H2 fix:runBlocking 阻塞主线程(EncryptedSharedPreferences 冷启动 200ms+),
        // 改用 lifecycleScope.launch 异步落盘，落盘完成后再 launch exchange。
        val appCtx = applicationContext
        lifecycleScope.launch {
            authStore.persistPendingExchange(
                code = code,
                appId = appId,
                secret = appSecret,
                requestId = "oauth"
            )
            // fix C2:在 Hilt 应用 scope 上异步执行 exchange,Activity 立即 finish。
            // 进程被杀时 persistPendingExchange 已落盘 → 下次启动可 resume。
            // v2 token endpoint 必需 redirect_uri(必须跟 authorize 时一致，否则 20071)
            appScope.launch { performExchange(appCtx, code, appId, appSecret, OAuthLauncher.REDIRECT_URI) }
        }
        // ux-2026-06-29:成功后把用户带回主 app，而不是留在浏览器 / 空 task。
        // CLEAR_TOP:如果主 app 已在 recents，清掉它之上的 activity 回到 MainActivity(单
        // activity 应用，等价于把 MainActivity 带到前台，Compose Nav 状态保留)。
        // NEW_TASK:从 receiver 独立 task(已 taskAffinity="")启动需要 NEW_TASK flag。
        // 不加这两个 flag，用户点完"打开 APP"后还会被丢回浏览器回调页(bounce back)。
        startMainActivity()
        finish()
    }

    /**
     * ux-2026-06-29:OAuth 成功后把用户带回主 app。
     * 单 Activity 架构，直接启动 MainActivity 即可;Compose Nav 状态由 ViewModelStore
     * 跨 task 保留(同一个进程、同一 Application 实例)。
     *
     * r2 改:去掉 FLAG_ACTIVITY_NEW_TASK。原因:receiver 现在跟 MainActivity 共享
     * 默认 taskAffinity(Manifest 已回退),startActivity 不加 NEW_TASK 会把 MainActivity
     * 加到 receiver 所在 task 的 back stack 顶部 → receiver finish 时 MainActivity 自动
     * 顶上 → 用户留在 app。加 NEW_TASK 会把 MainActivity 拉进新 task,receiver 自己的
     * task 被系统回收时回退到 intent source(浏览器)，用户被丢回浏览器回调页(bounce back)。
     *
     * CLEAR_TOP:如果 MainActivity 已在 task 里(receiver 是后加进来的)，把 receiver
     * 上面的 activity 全部清掉，直接落到 MainActivity;同时把 MainActivity 带到栈顶。
     */
    private fun startMainActivity() {
        val intent = android.content.Intent(this, com.yy.writingwithai.app.MainActivity::class.java)
            .setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try {
            startActivity(intent)
        } catch (e: Throwable) {
            Log.e(TAG, "OAuthCodeReceiver: failed to start MainActivity", e)
        }
    }

    private suspend fun performExchange(
        ctx: android.content.Context,
        code: String,
        appId: String,
        appSecret: String,
        redirectUri: String
    ) {
        // 关键:exchange 结果(成功/失败/异常)都要 consume pending，避免下次 receiver
        // 命中 re-delivery 检查(hasPendingExchange=true) 静默 finish，用户卡死。
        // 用 try/finally 保证;CancellationException(进程被杀)不 consume，留 resume 用。
        var exchangeSucceeded = false
        var errorMessage: String? = null
        var cancelled = false
        try {
            try {
                tokenProvider.exchangeCode(
                    appId = appId,
                    appSecret = appSecret,
                    code = code,
                    redirectUri = redirectUri
                )
                exchangeSucceeded = true
            } catch (e: FeishuError) {
                Log.e(TAG, "OAuth exchange failed: ${e.message}", e)
                errorMessage = ctx.getString(
                    R.string.feishu_oauth_exchange_failed_fmt,
                    e.message ?: ctx.getString(R.string.feishu_oauth_exchange_unknown)
                )
            } catch (e: java.io.IOException) {
                Log.e(TAG, "OAuth exchange network error", e)
                errorMessage = ctx.getString(R.string.feishu_oauth_exchange_network_fmt, e.message ?: "")
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                // 进程被杀:不 consume pending state，下次冷启动 resume
                cancelled = true
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "OAuth exchange unknown error", e)
                errorMessage = ctx.getString(R.string.feishu_oauth_exchange_unknown)
            }
        } finally {
            // 成功 / 业务失败 / 网络失败 / 未知异常:都 consume pending,
            // 避免下次 receiver 命中 re-delivery 检查静默 finish 把用户卡死。
            // CancellationException(进程被杀)不 consume，留 resume 用。
            if (!cancelled) {
                try {
                    authStore.consumePendingExchange()
                } catch (e: Throwable) {
                    Log.e(TAG, "performExchange: consumePendingExchange failed", e)
                }
            }
        }
        // Toast 必须在 Main 线程(在 OAuthAppScope 的 IO dispatcher 上跑会 NPE 崩进程)。
        // withContext(Main) 切回主线程;ctx 是 applicationContext,Toast 仍然能正常显示
        // (Android 11+ Toast 允许 application context)。
        withContext(kotlinx.coroutines.Dispatchers.Main) {
            val msg = if (exchangeSucceeded) {
                ctx.getString(
                    R.string.feishu_oauth_success
                )
            } else {
                (errorMessage ?: ctx.getString(R.string.feishu_oauth_failed))
            }
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /**
     * hardening H-7:OAuth re-delivery 核心序列。**先 re-persist pending，再 launch
     * exchange**。若 exchange 期间进程被杀，下次的 re-delivery 仍能恢复。
     *
     * 抽成 private suspend function 便于单元测试 `OAuthCodeReceiverTest` 验证顺序
     * (MockK 验证 `authStore.persistPendingExchange` 在 `appScope.launch` 前调用)。
     *
     * 注意:此函数通过 `lifecycleScope.launch` 在 Activity onCreate 内调用，内部
     * `appScope.launch` 在 Activity 已 finish 后才启动，不会因 lifecycle 取消。
     */
    @androidx.annotation.VisibleForTesting
    internal suspend fun performReDelivery(
        code: String,
        pending: PendingExchange,
        authStore: FeishuAuthStore,
        redirectUri: String,
        appCtx: android.content.Context
    ) {
        // H3:code mismatch 已在外层判断，这里假设 code == pending.code。
        // 1) 同步落盘 pending(让二次 crash 可 resume)。
        authStore.persistPendingExchange(
            code = pending.code,
            appId = pending.appId,
            secret = pending.secret,
            requestId = pending.requestId
        )
        // 2) 在应用 scope 上异步 exchange(Activity 已 finish，不能 lifecycleScope)。
        appScope.launch {
            performExchange(appCtx, pending.code, pending.appId, pending.secret, redirectUri)
        }
    }

    companion object {
        private const val TAG = "OAuthCodeReceiver"
    }
}

/**
 * fix-2026-06-26-review-r3 CRITICAL C2:OAuth 后台 token exchange 用的应用级 CoroutineScope。
 * Hilt @Singleton 注入，生命周期 = 进程生命周期。Activity finish 不取消这个 scope,
 * 进程被杀时由 [FeishuAuthStore.persistPendingExchange] 落盘恢复。
 *
 * 不用 [GlobalScope] 是因为它隐式全局可写，难以测试和被监控。
 */
@Singleton
class OAuthAppScope @Inject constructor(
    @ApplicationContext context: Context
) : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)
