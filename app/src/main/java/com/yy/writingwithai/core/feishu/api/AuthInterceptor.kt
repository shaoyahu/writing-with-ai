package com.yy.writingwithai.core.feishu.api

import com.yy.writingwithai.core.feishu.auth.UserTokenProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Response

/** feishu-user-oauth · OkHttp 拦截器(user_access_token) */
@Singleton
class AuthInterceptor
@Inject
constructor(
    private val tokenProvider: UserTokenProvider
) : Interceptor {

    private val noAuthKey = "X-No-Auth-Retry"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(noAuthKey) != null) {
            return chain.proceed(request.newBuilder().removeHeader(noAuthKey).build())
        }

        // OkHttp Interceptor 是同步契约;`runBlocking` 是桥接 suspend token provider
        // 到同步 OkHttp 调用栈的官方推荐做法。
        val token = runBlocking { tokenProvider.getToken() }
        val response = chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $token").build()
        )
        if (!isTokenInvalid(response)) return response

        response.close()
        tokenProvider.invalidate()
        val newToken = runBlocking { tokenProvider.getToken() }
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
