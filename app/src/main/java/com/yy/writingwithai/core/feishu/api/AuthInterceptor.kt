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
import okhttp3.Response

/**
 * feishu-user-oauth · OkHttp 拦截器(user_access_token)。
 *
 * fix-2026-06-24-review-r1-high H5:把 `runBlocking` 内的 token fetch 移到 `Dispatchers.IO`
 * + `withTimeoutOrNull(5_000)`,避免卡 OkHttp dispatcher。
 *
 * review r1 C8:复用单一 `SupervisorJob` + 内部 `refreshMutex` 串行化 401-storm 下
 * 的并发 token 刷新,避免每次 runBlocking 新建临时 Job 累积内存。
 */
@Singleton
class AuthInterceptor
@Inject
constructor(
    private val tokenProvider: UserTokenProvider
) : Interceptor {

    private val noAuthKey = "X-No-Auth-Retry"
    private val interceptorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(noAuthKey) != null) {
            return chain.proceed(request.newBuilder().removeHeader(noAuthKey).build())
        }

        // OkHttp Interceptor 是同步契约;`runBlocking` 是桥接 suspend → 同步的唯一官方做法。
        val token = runBlocking(interceptorScope.coroutineContext) {
            refreshMutex.withLock {
                withTimeoutOrNull(5_000) { tokenProvider.getToken() }
            }
        } ?: ""
        val response = chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $token").build()
        )
        if (!isTokenInvalid(response)) return response

        response.close()
        tokenProvider.invalidate()
        val newToken = runBlocking(interceptorScope.coroutineContext) {
            refreshMutex.withLock {
                withTimeoutOrNull(5_000) { tokenProvider.getToken() }
            }
        } ?: ""
        return chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $newToken").build()
        )
    }

    private fun isTokenInvalid(response: Response): Boolean {
        if (response.code == 401) return true
        if (response.code != 200) return false
        return runCatching {
            Json.parseToJsonElement(response.peekBody(1024).string())
                .jsonObject["code"]?.jsonPrimitive?.intOrNull == 99991663
        }.getOrDefault(false)
    }
}
