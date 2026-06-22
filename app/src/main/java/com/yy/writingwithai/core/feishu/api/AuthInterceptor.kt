package com.yy.writingwithai.core.feishu.api

import com.yy.writingwithai.core.feishu.auth.HEADER_NO_AUTH
import com.yy.writingwithai.core.feishu.auth.TenantTokenProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * feishu-oauth-flow · OkHttp 拦截器(design D3)。
 *
 * 责任:
 * 1. 请求塞 `Authorization: Bearer <tenant_access_token>`
 * 2. 401 / 403 → invalidate + 重取 + 重试一次
 * 3. 二次失败 → 让上层(FeishuApiClientImpl)映射为 FeishuError.AuthExpired
 *
 * spec: openspec/changes/feishu-oauth-flow/specs/feishu-api-client/spec.md
 * "Auth header injection" / "Tenant token re-fetch on 401"
 */
@Singleton
class AuthInterceptor
@Inject
constructor(
    private val tokenProvider: TenantTokenProvider
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // 跳过自身(token POST 不带 Authorization,且 token POST 失败不会递归重试)
        if (request.header(HEADER_NO_AUTH) != null) {
            return chain.proceed(request.newBuilder().removeHeader(HEADER_NO_AUTH).build())
        }

        val token = runBlocking { tokenProvider.getToken() }
        val authed = request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        val response = chain.proceed(authed)
        if (!isUnauthorized(response)) return response

        // 401 → invalidate + 重取 + 重试一次
        response.close()
        tokenProvider.invalidate()
        val newToken = runBlocking { tokenProvider.getToken() }
        val retry = request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
        return chain.proceed(retry)
    }

    /**
     * 401 / 403(403 在飞书也可能 token invalid)→ 走重取路径。
     * 200 + `code == 99991663` 由 FeishuApiClientImpl 在解析 body 时识别并抛 TokenInvalid,
     * 该路径不会走到 interceptor(因为 HTTP 200)。
     */
    private fun isUnauthorized(response: Response): Boolean {
        return response.code == 401 || response.code == 403
    }
}
