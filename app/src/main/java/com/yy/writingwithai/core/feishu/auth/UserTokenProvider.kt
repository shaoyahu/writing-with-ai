package com.yy.writingwithai.core.feishu.auth

import com.yy.writingwithai.core.feishu.api.FeishuError
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * feishu-user-oauth · 飞书用户 OAuth token 管理(参考官方文档)。
 *
 * 流程:
 * 1. app_access_token: POST /auth/v3/app_access_token/internal + app_id/app_secret
 * 2. user_access_token: POST /authen/v1/oidc/access_token + Bearer app_token
 * 3. refresh: POST /authen/v1/oidc/refresh_access_token + Bearer app_token
 */
@Singleton
class UserTokenProvider @Inject constructor(private val store: FeishuAuthStore) {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).build()

    // fix-2026-06-24-review-r1-high H6:consolidate token state under one Mutex (no @Volatile 分裂)
    private data class UserTokenState(val token: String?, val expiresAt: Long, val invalidated: Boolean)
    private data class AppTokenState(val token: String?, val expiresAt: Long)
    private var userState: UserTokenState = UserTokenState(null, 0L, false)
    private var appState: AppTokenState = AppTokenState(null, 0L)
    private val refreshMutex = Mutex()

    // ---- public API ----

    suspend fun getToken(): String {
        refreshMutex.withLock {
            userState.token?.takeIf { System.currentTimeMillis() < userState.expiresAt }?.let {
                return it
            }
            reentrantFetchLocked()
        }
        return userState.token ?: throw FeishuError.NotAuthorized
    }

    fun invalidate() {
        // best-effort;full reset inside mutex on next getToken
        userState = userState.copy(token = null, invalidated = true)
    }

    /** 拿 app_access_token,缓存 1.5h(app token 有效期 2h,提前刷) — caller must hold refreshMutex */
    private suspend fun getAppAccessTokenLocked(): String {
        appState.token?.takeIf { System.currentTimeMillis() < appState.expiresAt }?.let { return it }
        val (appId, appSecret) = store.getAppIdAndSecret(REQUEST_ID_OAUTH)
            ?: throw FeishuError.NotAuthorized
        val body = buildJsonObject {
            put("app_id", appId)
            put("app_secret", appSecret)
        }.toString()
        val resp = postJson(APP_TOKEN_URL, body, null)
        appState = AppTokenState(
            resp.str("app_access_token"),
            System.currentTimeMillis() + APP_TOKEN_TTL_MS
        )
        return appState.token!!
    }

    /** OAuth code → user_access_token */
    suspend fun exchangeCode(appId: String, appSecret: String, code: String) = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            store.persistAppSecret(REQUEST_ID_OAUTH, appSecret)
            try {
                val appToken = getAppAccessTokenLocked()
                val body = buildJsonObject {
                    put("grant_type", "authorization_code")
                    put("code", code)
                }.toString()
                val data = postJson(TOKEN_URL, body, appToken)
                persistUserTokenLocked(data, appId)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                // fix-MEDIUM(feishu M4):进程被杀导致取消,保留 appSecret 让下次冷启动
                // resume pending exchange 时仍能完成 token 交换(与 OAuthCodeReceiver.persistPendingExchange 配合)。
                throw e
            } catch (e: Throwable) {
                // fix-MEDIUM(feishu M4):exchange 失败时清掉 stale appSecret —
                // 否则下次启动 store.getAppSecretSnapshot("oauth") 仍能拿到旧 secret,
                // 但对应 code/refreshToken 已失效,造成"看上去授权完成但实际用旧 secret"的语义错乱。
                store.clearAppSecret(REQUEST_ID_OAUTH)
                throw e
            }
        }
    }

    // ---- internal ----

    private suspend fun reentrantFetchLocked() {
        if (!userState.invalidated) {
            store.getAccessTokenSnapshot()?.let { (access, expires) ->
                if (System.currentTimeMillis() < expires) {
                    userState = userState.copy(token = access, expiresAt = expires)
                    return
                }
            }
        }

        val (appId, refreshToken) = store.getAppIdAndRefreshToken()
            ?: throw FeishuError.NotAuthorized
        val appToken = getAppAccessTokenLocked()
        val body = buildJsonObject {
            put("grant_type", "refresh_token")
            put("refresh_token", refreshToken)
        }.toString()
        val data = postJson(REFRESH_URL, body, appToken)
        persistUserTokenLocked(data, appId)
    }

    /** 把 access_token + refresh_token 落 store + 缓存,并清 transient appSecret — caller holds mutex */
    private suspend fun persistUserTokenLocked(data: JsonObject, appId: String) {
        val now = System.currentTimeMillis()
        // fix H7:expires_in parse fail fallback 60s (保守),原 7000s 静默接受 2h token 危险
        val ttlSec = data.str("expires_in").toLongOrNull()
            ?: run {
                android.util.Log.w(TAG, "expires_in parse failed, fallback to ${FALLBACK_PARSE_TTL_S}s")
                FALLBACK_PARSE_TTL_S
            }
        val expiresAt = now + ttlSec * 1000L - REFRESH_LEAD_MS
        userState = UserTokenState(
            token = data.str("access_token"),
            expiresAt = expiresAt,
            invalidated = false
        )
        store.setOAuthCredentials(
            appId = appId,
            accessToken = data.str("access_token"),
            refreshToken = data.str("refresh_token"),
            expiresAt = expiresAt
        )
        store.clearAppSecret(REQUEST_ID_OAUTH)
    }

    /** POST JSON → 返回 data 层 JsonObject(值可为 Int/String,不用 Map<String,String>) */
    private suspend fun postJson(url: String, jsonBody: String, bearer: String?): JsonObject =
        withContext(Dispatchers.IO) {
            val b = Request.Builder().url(url)
                .post(jsonBody.toRequestBody(JSON))
            if (bearer != null) b.header("Authorization", "Bearer $bearer")
            val resp = try {
                httpClient.newCall(b.build()).execute()
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Throwable) {
                throw FeishuError.NetworkError(e.message ?: "network")
            }
            resp.use { r ->
                val raw = r.body?.string().orEmpty()
                val root = try {
                    Json.parseToJsonElement(raw).jsonObject
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    throw FeishuError.NetworkError("parse: ${e.message}, raw=$raw")
                }
                val code = root["code"]?.jsonPrimitive?.intOrNull ?: 0
                val msg = root["msg"]?.jsonPrimitive?.contentOrNull ?: ""
                if (code != 0) throw FeishuError.BadRequest(code, msg)
                root["data"]?.jsonObject ?: root // 若无 data,退回到 root(app_access_token 端点)
            }
        }

    private fun JsonObject.str(key: String): String = this[key]?.jsonPrimitive?.contentOrNull ?: ""

    companion object {
        private const val TAG = "UserTokenProvider"
        private const val APP_TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/app_access_token/internal"
        private const val TOKEN_URL = "https://open.feishu.cn/open-apis/authen/v1/oidc/access_token"
        private const val REFRESH_URL = "https://open.feishu.cn/open-apis/authen/v1/oidc/refresh_access_token"
        private const val REFRESH_LEAD_MS = 5 * 60 * 1000L
        private const val APP_TOKEN_TTL_MS = 90L * 60 * 1000L // 1.5h

        // fix H7:解析失败时 fallback 60s(原 7000s 静默 2h 太危险)
        private const val FALLBACK_PARSE_TTL_S = 60L
        private const val REQUEST_ID_OAUTH = "oauth"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
