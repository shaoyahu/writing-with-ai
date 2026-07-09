package com.yy.writingwithai.core.feishu.auth

import android.util.Log
import com.yy.writingwithai.core.feishu.api.FeishuError
import com.yy.writingwithai.core.feishu.api.skipFeishuAuth
import javax.inject.Inject
import javax.inject.Named
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
 * feishu-user-oauth · 飞书用户 OAuth token 管理(最新 v2 协议)。
 *
 * ux-2026-06-29:按官方三篇文档重写，抛弃 cac0003 用的 OIDC v1 废弃协议。
 *
 * **完整协议 + 字段表 + 错误码见 `docs/usage/api-feishu.md`**。任何字段疑问先查那篇，
 * **不要瞎猜**(历史踩坑:漏 `redirect_uri` → 飞书返回 20063 "请求体缺少必要字段" 且 msg 空)。
 *
 * 官方文档:
 * - 获取 authorization code: https://open.feishu.cn/document/authentication-management/access-token/obtain-oauth-code
 * - 换取 user_access_token: https://open.feishu.cn/document/authentication-management/access-token/get-user-access-token
 * - 刷新 user_access_token: https://open.feishu.cn/document/authentication-management/access-token/refresh-user-access-token
 *
 * 关键协议:
 * - authorize URL: `https://accounts.feishu.cn/open-apis/authen/v1/authorize`(v1!)
 * - token endpoint: `POST https://open.feishu.cn/open-apis/authen/v2/oauth/token`(v2)
 * - **不需要** Authorization header / **不需要** app_access_token 中间步骤(OIDC v1 已废弃)
 * - exchange body 必需: grant_type + client_id + client_secret + code + **redirect_uri**
 * - refresh body 必需: grant_type=refresh_token + client_id + client_secret + refresh_token(不需要 redirect_uri)
 * - 响应字段顶层(无 data 包装):code=0 表示成功
 */
@Singleton
class UserTokenProvider @Inject constructor(
    private val store: FeishuAuthStore,
    // fix-full-review:注入 @Named("feishu-auth") OkHttpClient 替代自建 client。
    // 自建 client 绕过 logging / cert pinning / shared connection pool，
    // 且用于最敏感的 token exchange/refresh 操作(client_secret 在 body 中)。
    // 使用 @Named("feishu-auth") 而非 @Named("feishu") 避免 Hilt 依赖循环
    // (AuthInterceptor→UserTokenProvider→OkHttpClient→AuthInterceptor)。
    // token exchange/refresh 请求通过 skipFeishuAuth() 标记，功能等效。
    @Named("feishu-auth") private val httpClient: OkHttpClient
) {

    // fix-2026-06-24-review-r1-high H6:consolidate token state under one Mutex (no @Volatile 分裂)
    private data class UserTokenState(val token: String?, val expiresAt: Long, val invalidated: Boolean)
    private var userState: UserTokenState = UserTokenState(null, 0L, false)
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

    /**
     * fix-2026-06-30-full-review-r1 HIGH H7:invalidate 写入 userState 之前抢 mutex,
     * 避免与 getToken() 内部 mutex 内的读 / mutex 外的 return 形成 race。
     * 之前在 mutex 外写，getToken() 在 mutex 内读到 token,mutex 外 return 前 invalidate
     * 抢先设 null，导致返回 null 抛 NotAuthorized。invalidate 改成 suspend + mutex
     * 锁内执行，保证写与读在统一临界区。
     */
    suspend fun invalidate() {
        refreshMutex.withLock {
            userState = userState.copy(token = null, invalidated = true)
        }
    }

    /**
     * OAuth code → user_access_token。
     *
     * @param redirectUri 必须跟 authorize 时传给飞书的一致(否则 20071)。由 caller
     *        ([OAuthCodeReceiver]) 从 [OAuthLauncher.REDIRECT_URI] 传入。
     */
    suspend fun exchangeCode(appId: String, appSecret: String, code: String, redirectUri: String) =
        withContext(Dispatchers.IO) {
            refreshMutex.withLock {
                // 持久化 secret — 成功后**保留**(refresh 流程要用)，失败才清
                store.persistAppSecret(REQUEST_ID_OAUTH, appSecret)
                try {
                    val body = buildJsonObject {
                        put("grant_type", "authorization_code")
                        put("client_id", appId)
                        put("client_secret", appSecret)
                        put("code", code)
                        // 关键:redirect_uri 必需，漏了飞书返回 20063 "请求体缺少必要字段"
                        put("redirect_uri", redirectUri)
                    }.toString()
                    val data = postJson(TOKEN_URL, body)
                    persistUserTokenLocked(data, appId)
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    // fix-MEDIUM(feishu M4):进程被杀导致取消，保留 appSecret 让下次冷启动
                    // resume pending exchange 时仍能完成 token 交换(与 OAuthCodeReceiver.persistPendingExchange 配合)。
                    throw e
                } catch (e: Throwable) {
                    // fix-MEDIUM(feishu M4):exchange 失败时清掉 stale appSecret —
                    // 否则下次启动 store.getAppSecretSnapshot("oauth") 仍能拿到旧 secret,
                    // 但对应 code/refreshToken 已失效，造成"看上去授权完成但实际用旧 secret"的语义错乱。
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
                    userState = UserTokenState(access, expires, false)
                    return
                }
            }
        }

        val (appId, appSecret) = store.getAppIdAndSecret(REQUEST_ID_OAUTH)
            ?: throw FeishuError.NotAuthorized
        val refreshToken = store.getRefreshTokenSnapshot()
            ?: throw FeishuError.NotAuthorized
        // refresh 不需要 redirect_uri(官方文档 refresh-user-access-token body 列表无此项)
        val body = buildJsonObject {
            put("grant_type", "refresh_token")
            put("client_id", appId)
            put("client_secret", appSecret)
            put("refresh_token", refreshToken)
        }.toString()
        val data = postJson(TOKEN_URL, body)
        persistUserTokenLocked(data, appId)
    }

    /** 把 access_token + refresh_token 落 store + 缓存 — caller holds mutex */
    private suspend fun persistUserTokenLocked(data: JsonObject, appId: String) {
        val now = System.currentTimeMillis()
        // fix H7:expires_in parse fail fallback 60s (保守)，原 7000s 静默接受 2h token 危险
        val ttlSec = data.str("expires_in").toLongOrNull()
            ?: run {
                Log.w(TAG, "expires_in parse failed, fallback to ${FALLBACK_PARSE_TTL_S}s")
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
    }

    /**
     * POST JSON → 返回根 JsonObject(响应字段顶层，无 data 包装)。
     * v2 协议不需要 Authorization header。
     * fix-full-review:skipFeishuAuth() 避免 AuthInterceptor 注入 token(v2 协议自带
     * client_secret 认证，不需要 Bearer token;注入反而可能触发 AuthInterceptor→getToken→postJson 循环)。
     */
    private suspend fun postJson(url: String, jsonBody: String): JsonObject {
        return withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).post(jsonBody.toRequestBody(JSON))
                .skipFeishuAuth().build()
            // 诊断日志:飞书 20063 等业务错误 msg 字段常为空，需要看完整 body
            // 定位是缺字段 / redirect_uri 不匹配 / client_secret 错 / code 过期 等。
            // body 不能直接 log(可能含 client_secret)，改 log 脱敏后 body。
            Log.i(TAG, "postJson: url=$url body=${sanitizeBodyForLog(jsonBody)}")
            val resp = try {
                httpClient.newCall(req).execute()
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
                    // fix H6:截断 raw 避免泄露 access_token/refresh_token 到 logcat。
                    Log.e(TAG, "postJson: parse fail, raw=${raw.take(100)}", e)
                    throw FeishuError.NetworkError("parse: ${e.message}")
                }
                val code = root["code"]?.jsonPrimitive?.intOrNull ?: 0
                val msg = root["msg"]?.jsonPrimitive?.contentOrNull ?: ""
                if (code != 0) {
                    // fix H6:截断 raw 避免泄露 token 到 logcat。
                    Log.w(TAG, "postJson: feishu business error code=$code msg=$msg")
                    throw FeishuError.BadRequest(code, msg)
                }
                root
            }
        }
    }

    /** 脱敏 body:client_secret / code / refresh_token 替换成 ****，避免 logcat 泄露。 */
    private fun sanitizeBodyForLog(body: String): String {
        return body
            .replace(REGEX_SECRET, "\"client_secret\":\"****\"")
            .replace(REGEX_CODE, "\"code\":\"****\"")
            .replace(REGEX_REFRESH, "\"refresh_token\":\"****\"")
    }

    private fun JsonObject.str(key: String): String = this[key]?.jsonPrimitive?.contentOrNull ?: ""

    companion object {
        private const val TAG = "UserTokenProvider"

        // v2 token endpoint(官方推荐，OIDC v1 已废弃)
        private const val TOKEN_URL = "https://open.feishu.cn/open-apis/authen/v2/oauth/token"
        private const val REFRESH_LEAD_MS = 5 * 60 * 1000L

        // fix H7:解析失败时 fallback 60s(原 7000s 静默 2h 太危险)
        private const val FALLBACK_PARSE_TTL_S = 60L
        private const val REQUEST_ID_OAUTH = "oauth"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        // fix:提取 Regex 常量避免每次调用 sanitizeBodyForLog 重新编译
        private val REGEX_SECRET = Regex("\"client_secret\"\\s*:\\s*\"[^\"]+\"")
        private val REGEX_CODE = Regex("\"code\"\\s*:\\s*\"[^\"]+\"")
        private val REGEX_REFRESH = Regex("\"refresh_token\"\\s*:\\s*\"[^\"]+\"")
    }
}
