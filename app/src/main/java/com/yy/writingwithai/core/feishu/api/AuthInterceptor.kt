package com.yy.writingwithai.core.feishu.api

import com.yy.writingwithai.core.feishu.auth.HEADER_NO_AUTH
import com.yy.writingwithai.core.feishu.auth.TenantTokenProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Response

/**
 * feishu-oauth-flow · OkHttp 拦截器(design D3)。
 *
 * 责任:
 * 1. 请求塞 `Authorization: Bearer <tenant_access_token>`
 * 2. 401 或 200+`code==99991663` → invalidate + 重取 + 重试一次
 * 3. 二次失败 → 上层(FeishuApiClientImpl)映射为 FeishuError.AuthExpired
 *
 * spec: openspec/changes/feishu-oauth-flow/specs/feishu-api-client/spec.md
 * "Auth header injection" / "Tenant token re-fetch on 401" / "99991663 treated as 401"
 *
 * review r1:403 不再当 token 失效(spec 403 → Forbidden,重取无意义);99991663 走重取。
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
        if (!isTokenInvalid(response)) return response

        // 401 / 99991663 → invalidate + 重取 + 重试一次
        response.close()
        tokenProvider.invalidate()
        val newToken = runBlocking { tokenProvider.getToken() }
        val retry = request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
        return chain.proceed(retry)
    }

    /**
     * token 失效判定:
     * - HTTP 401 → 是
     * - HTTP 200 + body `code == 99991663`(飞书 token invalid 码)→ 是(peekBody 不消费流)
     * - HTTP 403 → 否(spec 403 → Forbidden,权限不足,重取 token 无意义)
     */
    private fun isTokenInvalid(response: Response): Boolean {
        if (response.code == 401) return true
        if (response.code != 200) return false
        return runCatching {
            val peek = response.peekBody(MAX_PEEK_BYTES).string()
            val parsed = Json.parseToJsonElement(peek).jsonObject
            parsed["code"]?.jsonPrimitive?.intOrNull == 99991663
        }.getOrDefault(false)
    }

    companion object {
        private const val MAX_PEEK_BYTES = 1024L
    }
}
