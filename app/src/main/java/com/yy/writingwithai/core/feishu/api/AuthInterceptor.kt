package com.yy.writingwithai.core.feishu.api

import com.yy.writingwithai.core.feishu.auth.UserTokenProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * feishu-api:跳过 AuthInterceptor 的 request header 约定。
 *
 * 飞书 CDN / SSE / 图片下载走临时签名 query,不需要 Bearer token,但会被 AuthInterceptor
 * 自动注入 → 401。约定:`X-No-Auth-Retry: 1` 表示本请求跳过 AuthInterceptor。
 *
 * review 2026-07-07 Finding #15:把 header 字面量集中到 internal const 跨包共享,
 * 避免调用方(FeishuImageDownloader 等)硬编码同一字符串 → AuthInterceptor 改名后
 * 下载器静默失效。
 *
 * 使用方式:
 * ```
 * val req = Request.Builder()
 *     .url(imageUrl)
 *     .skipFeishuAuth()      // ← 走 [AuthInterceptor.NO_AUTH_HEADER] 扩展
 *     .get()
 *     .build()
 * ```
 */
internal const val NO_AUTH_HEADER = "X-No-Auth-Retry"
internal const val NO_AUTH_HEADER_VALUE = "1"

/**
 * feishu-api:给 OkHttp Request 标记 "跳过 AuthInterceptor"。本扩展函数是 [AuthInterceptor]
 * 与外部调用方(图片下载 / SSE)之间唯一约定的入口。
 */
fun Request.Builder.skipFeishuAuth(): Request.Builder = header(NO_AUTH_HEADER, NO_AUTH_HEADER_VALUE)

/**
 * feishu-user-oauth · OkHttp 拦截器(user_access_token)。
 *
 * fix-2026-06-24-review-r1-high H5:把 `runBlocking` 内的 token fetch 移到 `Dispatchers.IO`
 * + `withTimeoutOrNull(5_000)`，避免卡 OkHttp dispatcher。
 *
 * review r1 C8:复用单一 `SupervisorJob` + 内部 `refreshMutex` 串行化 401-storm 下
 * 的并发 token 刷新，避免每次 runBlocking 新建临时 Job 累积内存。
 */
@Singleton
class AuthInterceptor
@Inject
constructor(
    private val tokenProvider: UserTokenProvider
) : Interceptor {

    private val NO_AUTH_HEADER = "X-No-Auth-Retry"
    private val interceptorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(NO_AUTH_HEADER) != null) {
            return chain.proceed(request.newBuilder().removeHeader(NO_AUTH_HEADER).build())
        }

        // OkHttp Interceptor 是同步契约;`runBlocking` 是桥接 suspend → 同步的唯一官方做法。
        val firstToken = runBlocking(interceptorScope.coroutineContext) {
            refreshMutex.withLock {
                withTimeoutOrNull(5_000) { tokenProvider.getToken() }
            }
        }
        // fix-MEDIUM(feishu M6):第一次 token 拉取超时不重试 — 直接走 X-No-Auth-Retry
        // 标记(本拦截器内识别) + 移除 Authorization 头(让 FeishuApiClient 拿到 401
        // → 抛 AuthExpired)，业务层 fail-fast。 之前 fallback 到 `Bearer ` 空白，服务端再
        // 401 → 触发 isTokenInvalid → 进入"刷新+重试"循环，第二次同样超时 → 客户端空转 10 秒。
        // X-No-Auth-Retry 在发出去之前用同样的 removeHeader 模式剥掉，与原始 design 一致。
        if (firstToken == null) {
            val noAuthReq = request.newBuilder()
                .removeHeader("Authorization")
                .header(NO_AUTH_HEADER, NO_AUTH_HEADER_VALUE)
                .build()
            // 立即剥掉标记头，避免发到飞书服务端。拦截器对调一次后，
            // noAuthReq 不会再进入本拦截器(它的 NO_AUTH_HEADER 已被链上一步剥掉)，所以
            // 也不会"再取 token 再 401"的死循环。
            return chain.proceed(
                noAuthReq.newBuilder().removeHeader(NO_AUTH_HEADER).build()
            )
        }
        val response = chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $firstToken").build()
        )
        if (!isTokenInvalid(response)) return response

        response.close()
        // fix-2026-06-30-full-review-r1 H7:invalidate 变 suspend;AuthInterceptor 是
        // 同步 OkHttp 契约，用 runBlocking 桥接(本类已用 runBlocking 桥接 getToken)。
        runBlocking(interceptorScope.coroutineContext) { tokenProvider.invalidate() }
        val secondToken = runBlocking(interceptorScope.coroutineContext) {
            refreshMutex.withLock {
                withTimeoutOrNull(5_000) { tokenProvider.getToken() }
            }
        }
        // 第二次仍取不到(网络/配置问题)— 用 firstToken==null 同 path 走 noAuthReq
        // 重发，不再返回已关闭的 response(use-after-close → IllegalStateException
        // 或 OkHttp 静默丢错误)。fix-2026-06-30-full-review-r1 HIGH H6。
        if (secondToken == null) {
            val noAuthReq = request.newBuilder()
                .removeHeader("Authorization")
                .header(NO_AUTH_HEADER, NO_AUTH_HEADER_VALUE)
                .build()
            return chain.proceed(
                noAuthReq.newBuilder().removeHeader(NO_AUTH_HEADER).build()
            )
        }
        return chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $secondToken").build()
        )
    }

    private fun isTokenInvalid(response: Response): Boolean {
        if (response.code == 401) return true
        if (response.code != 200) return false
        // fix-2026-06-26-review-r3 HIGH(feishu agent re-scan):runCatching 捕获 Throwable 包含
        // CancellationException，会在结构化并发取消时把"取消"误判为"token invalid"，导致
        // 提前触发 refresh。先显式 rethrow CancellationException。
        return try {
            Json.parseToJsonElement(response.peekBody(1024).string())
                .jsonObject["code"]?.jsonPrimitive?.intOrNull == FEISHU_TOKEN_INVALID_CODE
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Throwable) {
            false
        }
    }

    companion object {
        // fix-2026-06-26-review-r3 LOW:飞书 token 失效业务码，AuthInterceptor + FeishuApiClientImpl 共享。
        const val FEISHU_TOKEN_INVALID_CODE = 99991663
    }
}
