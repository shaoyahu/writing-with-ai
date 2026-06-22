package com.yy.writingwithai.core.feishu.auth

import com.yy.writingwithai.core.feishu.api.FeishuError
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * feishu-oauth-flow · tenant_access_token 二级缓存 + 并发去重(design D2)。
 *
 * - 内存 hot path:@Volatile CachedToken 引用
 * - 持久 cold path:FeishuAuthStore.getTokenSnapshot()
 * - 提前刷新:now() + 5min > expiresAt → 重新 POST
 * - 并发去重:fetchMutex.withLock { reentrantFetch() },double-check 防重发
 *
 * spec: openspec/changes/feishu-oauth-flow/specs/feishu-auth/spec.md
 * "Token storage and lifecycle"
 */
@Singleton
open class TenantTokenProvider
@Inject
constructor(
    private val store: FeishuAuthStore
) {
    /**
     * 独立的小 OkHttpClient,只为取 token 用。
     *
     * 为什么不复用 [FeishuModule.provideOkHttpClient]:它带 AuthInterceptor,
     * AuthInterceptor 注入 TenantTokenProvider,形成循环。
     * 隔离后:TenantTokenProvider 不依赖带 interceptor 的 client,只用一个干净 client
     * POST token endpoint。AuthInterceptor 之后用 TenantTokenProvider 拼 token 是单向依赖。
     */
    protected open val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedToken: CachedToken? = null

    /**
     * invalidate 标志:AuthInterceptor 401 后置 true,下一次 getToken 强制走 fetch
     * 路径(跳过 store snapshot cold path,因为 store 里的 token 可能就是失效的那个)。
     */
    @Volatile
    private var invalidated: Boolean = false

    private val fetchMutex = Mutex()

    suspend fun getToken(): String {
        cachedToken?.takeIf { it.isValid() }?.let { return it.token }
        return fetchMutex.withLock { reentrantFetch() }
    }

    private suspend fun reentrantFetch(): String {
        cachedToken?.takeIf { it.isValid() }?.let { return it.token }

        // 仅在非 invalidated 路径读 store snapshot(进程重启 cold path)
        if (!invalidated) {
            store.getTokenSnapshot()?.let { (token, expiresAt) ->
                val candidate = CachedToken(token, expiresAt)
                if (candidate.isValid()) {
                    cachedToken = candidate
                    return token
                }
            }
        }

        val (appId, appSecret) = store.getCredentialsSnapshot() ?: run {
            store.setAuthState(FeishuAuthState.FAILED)
            throw FeishuError.NotAuthorized
        }
        store.setAuthState(FeishuAuthState.TOKEN_FETCHING)
        val response = fetchTenantToken(appId, appSecret)
        invalidated = false // fetch 成功后清标志
        val expiresAt = System.currentTimeMillis() + response.expire * 1000L - REFRESH_LEAD_MS
        val token = CachedToken(response.tenantAccessToken, expiresAt)
        cachedToken = token
        store.persistToken(response.tenantAccessToken, expiresAt)
        return token.token
    }

    fun invalidate() {
        cachedToken = null
        invalidated = true
    }

    private suspend fun fetchTenantToken(appId: String, appSecret: String): TenantTokenResponse =
        withContext(Dispatchers.IO) {
            val jsonBody = Json.encodeToString(
                TenantTokenRequest.serializer(),
                TenantTokenRequest(appId, appSecret)
            )
            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .header(HEADER_NO_AUTH, "1")
                .build()
            val response = try {
                httpClient.newCall(request).execute()
            } catch (e: Throwable) {
                store.setAuthState(FeishuAuthState.FAILED)
                throw FeishuError.NetworkError(detail = e.javaClass.simpleName + ": " + (e.message ?: ""))
            }
            response.use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    store.setAuthState(FeishuAuthState.FAILED)
                    throw FeishuError.ServerError(resp.code)
                }
                val parsed = try {
                    Json.decodeFromString(TenantTokenResponse.serializer(), body)
                } catch (e: Throwable) {
                    store.setAuthState(FeishuAuthState.FAILED)
                    throw FeishuError.NetworkError(detail = "JSON parse failed")
                }
                if (parsed.code != 0) {
                    store.setAuthState(FeishuAuthState.FAILED)
                    if (parsed.code == 99991663) throw FeishuError.TokenInvalid
                    throw FeishuError.BadRequest(parsed.code, parsed.msg)
                }
                parsed
            }
        }

    private data class CachedToken(val token: String, val expiresAt: Long) {
        fun isValid(): Boolean = System.currentTimeMillis() < expiresAt
    }

    @Serializable
    private data class TenantTokenRequest(
        @SerialName("app_id") val appId: String,
        @SerialName("app_secret") val appSecret: String
    )

    @Serializable
    private data class TenantTokenResponse(
        val code: Int,
        val msg: String,
        @SerialName("tenant_access_token")
        val tenantAccessToken: String,
        val expire: Int
    )

    companion object {
        internal const val TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal"
        private const val REFRESH_LEAD_MS = 5 * 60 * 1000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal const val HEADER_NO_AUTH = "X-No-Auth-Retry"
