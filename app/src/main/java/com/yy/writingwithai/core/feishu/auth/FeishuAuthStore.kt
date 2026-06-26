package com.yy.writingwithai.core.feishu.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/**
 * feishu-user-oauth · 飞书 OAuth user_access_token + refresh_token 加密存储。
 *
 * 不持久化 appSecret;exchangeCode 时 `persistAppSecret` 写 transient,refresh 时
 * `getAppSecretSnapshot` 取,完成后 `clearAppSecret` 清。
 *
 * 加密 prefs 初始化失败时不会静默降级:暴露 `prefsInitError`,`authState` 切到
 * [FeishuAuthState.KEYSTORE_UNAVAILABLE],UI 必须显式处理(而不是看着像"未登录")。
 */
interface FeishuAuthStore {
    val appId: Flow<String?>
    val folderToken: Flow<String?>
    val accessToken: Flow<String?>
    val refreshToken: Flow<String?>
    val expiresAt: Flow<Long?>
    val authState: StateFlow<FeishuAuthState>

    /** 加密 prefs 初始化异常(为 null 表示 OK)。 */
    val prefsInitError: Throwable?

    suspend fun setOAuthCredentials(appId: String, accessToken: String, refreshToken: String, expiresAt: Long)
    suspend fun setAuthState(state: FeishuAuthState)
    suspend fun clearAll()

    fun getAccessTokenSnapshot(): Pair<String, Long>?
    fun getRefreshTokenSnapshot(): String?
    fun getFolderTokenSnapshot(): String?
    fun getAppIdAndRefreshToken(): Pair<String, String>?

    /**
     * fix-2026-06-26-review-r3 CRITICAL C3:appSecret 现在持久化到 EncryptedSharedPreferences
     * (与 token / refreshToken 同一文件,同一加密策略)。原 in-memory `ConcurrentHashMap` 在
     * OAuthCodeReceiver 启动后立即读 `appSecret`,但 Android 进程可能因系统低内存被回收,
     * 进程重启后 cache 丢 → 用户看到"appSecret missing"假失败。现在改落盘后,
     * 即使进程被杀重启,OAuthCodeReceiver 仍能从加密 prefs 拿到 secret 完成 token exchange。
     *
     * 安全:EncryptedSharedPreferences 用 Android Keystore (AES256_GCM) 加密,
     * 不进明文 SharedPreferences / logcat / Auto Backup。
     */
    suspend fun persistAppSecret(requestId: String, secret: String)
    suspend fun clearAppSecret(requestId: String)
    fun getAppSecretSnapshot(requestId: String): String?
    fun getAppIdAndSecret(requestId: String): Pair<String, String>?

    /**
     * 是否有未完成的 OAuth 流程在等待 token exchange(用于进程重启后由
     * OAuthCodeReceiver resume)。fix C2/C3 配合:state + appSecret 一起落盘,
     * 进程被杀重启能恢复。
     */
    suspend fun persistPendingExchange(code: String, appId: String, secret: String, requestId: String)
    fun consumePendingExchange(): PendingExchange?
    fun hasPendingExchange(): Boolean

    /**
     * fix-2026-06-24-review-r1-critical · OAuth state CSRF 防护持久化。
     *
     * @param state 随机字符串(UUID/SecureRandom),OAuthLauncher 写、OAuthCodeReceiver 校验
     * @param ttlMs 存活时长(默认 5 分钟),超过即视为过期
     */
    suspend fun persistOAuthState(state: String, ttlMs: Long = OAUTH_STATE_DEFAULT_TTL_MS)

    /**
     * 取并清除已存的 OAuth state;返回 stored state,若不存在或过期返回 `null`。
     * (单 KEY,不支持并发多 flow — 同设备单用户场景足够。)
     */
    fun consumeOAuthState(): String?

    companion object {
        /** fix r1:OAuth state 默认 TTL = 5 分钟。 */
        const val OAUTH_STATE_DEFAULT_TTL_MS: Long = 5L * 60L * 1000L
    }
}

/**
 * fix-2026-06-26-review-r3 CRITICAL C2:OAuthCodeReceiver 进程被杀后 resume 用的 pending 状态。
 * code / appId / secret / requestId 都由 OAuthCodeReceiver 写入加密 prefs,
 * 下次应用启动时由 [FeishuSyncService] / [OAuthCodeReceiver] resume 检查。
 */
data class PendingExchange(
    val code: String,
    val appId: String,
    val secret: String,
    val requestId: String,
    val createdAt: Long
)

@Singleton
class FeishuAuthStoreImpl
@Inject
constructor(
    @ApplicationContext private val context: Context
) : FeishuAuthStore {

    private val stateFlow = MutableStateFlow(FeishuAuthState.DISCONNECTED)

    private val prefs: SharedPreferences? = try {
        openEncryptedPrefs()
    } catch (e: Throwable) {
        Log.e(TAG, "EncryptedSharedPreferences init failed: ${e.javaClass.simpleName}: ${e.message}", e)
        stateFlow.value = FeishuAuthState.KEYSTORE_UNAVAILABLE
        null
    }

    override val prefsInitError: Throwable?
        get() = if (prefs == null) IllegalStateException("EncryptedSharedPreferences unavailable") else null

    override val appId: Flow<String?> = keyFlow(KEY_APP_ID, ::identityString)
    override val folderToken: Flow<String?> = keyFlow(KEY_FOLDER, ::identityString)
    override val accessToken: Flow<String?> = keyFlow(KEY_ACCESS, ::identityString)
    override val refreshToken: Flow<String?> = keyFlow(KEY_REFRESH, ::identityString)
    override val expiresAt: Flow<Long?> = keyFlow(KEY_EXPIRES) { it?.toLongOrNull() }

    override val authState: StateFlow<FeishuAuthState> = stateFlow.asStateFlow()

    override suspend fun setOAuthCredentials(
        appId: String,
        accessToken: String,
        refreshToken: String,
        expiresAt: Long
    ) = withContext(Dispatchers.IO) {
        val p = requirePrefs() ?: return@withContext
        p.edit()
            .putString(KEY_APP_ID, appId)
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .putString(KEY_EXPIRES, expiresAt.toString())
            .apply()
        stateFlow.value = FeishuAuthState.CONNECTED
    }

    override suspend fun setAuthState(state: FeishuAuthState) {
        stateFlow.value = state
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        val p = requirePrefs() ?: return@withContext
        p.edit().clear().apply()
        // L2 修:切到 DISCONNECTED 时清掉 in-memory appSecret 缓存,避免 stale secret。
        secretCache.clear()
        stateFlow.value = FeishuAuthState.DISCONNECTED
    }

    override fun getAccessTokenSnapshot(): Pair<String, Long>? {
        val p = requirePrefs() ?: return null
        val t = p.getString(KEY_ACCESS, null) ?: return null
        val e = p.getString(KEY_EXPIRES, null)?.toLongOrNull() ?: return null
        return t to e
    }

    override fun getRefreshTokenSnapshot(): String? = requirePrefs()?.getString(KEY_REFRESH, null)
    override fun getFolderTokenSnapshot(): String? = requirePrefs()?.getString(KEY_FOLDER, null)
    override fun getAppIdAndRefreshToken(): Pair<String, String>? {
        val p = requirePrefs() ?: return null
        val id = p.getString(KEY_APP_ID, null) ?: return null
        val rt = p.getString(KEY_REFRESH, null) ?: return null
        return id to rt
    }

    // fix-2026-06-26-review-r3 CRITICAL C3:appSecret 现在落 EncryptedSharedPreferences(同名 prefs 文件),
    // in-memory cache 保留为 fast path(同进程内 hot read),fallback 到 prefs 应对冷启动。
    private val secretCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    override suspend fun persistAppSecret(requestId: String, secret: String) = withContext(Dispatchers.IO) {
        secretCache[requestId] = secret
        // 同步落盘:进程被杀后 OAuthCodeReceiver 仍能 resume
        val p = requirePrefs() ?: return@withContext
        p.edit().putString(secretKeyFor(requestId), secret).apply()
    }

    override suspend fun clearAppSecret(requestId: String) = withContext(Dispatchers.IO) {
        secretCache.remove(requestId)
        val p = requirePrefs() ?: return@withContext
        p.edit().remove(secretKeyFor(requestId)).apply()
    }

    override fun getAppSecretSnapshot(requestId: String): String? {
        secretCache[requestId]?.let { return it }
        // fix C3:cold start 时 cache miss,从 prefs 读
        val p = requirePrefs() ?: return null
        val fromDisk = p.getString(secretKeyFor(requestId), null) ?: return null
        secretCache[requestId] = fromDisk
        return fromDisk
    }

    override fun getAppIdAndSecret(requestId: String): Pair<String, String>? {
        val p = requirePrefs() ?: return null
        val id = p.getString(KEY_APP_ID, null) ?: return null
        val sec = getAppSecretSnapshot(requestId) ?: return null
        return id to sec
    }

    // ---- fix C2:pending exchange 持久化(进程被杀后 resume) ----

    override suspend fun persistPendingExchange(code: String, appId: String, secret: String, requestId: String) =
        withContext(Dispatchers.IO) {
            val p = requirePrefs() ?: return@withContext
            p.edit()
                .putString(KEY_PENDING_CODE, code)
                .putString(KEY_PENDING_APP_ID, appId)
                .putString(KEY_PENDING_SECRET, secret)
                .putString(KEY_PENDING_REQUEST_ID, requestId)
                .putLong(KEY_PENDING_CREATED_AT, System.currentTimeMillis())
                .apply()
        }

    override fun consumePendingExchange(): PendingExchange? {
        val p = requirePrefs() ?: return null
        val code = p.getString(KEY_PENDING_CODE, null) ?: return null
        val appId = p.getString(KEY_PENDING_APP_ID, null) ?: return null
        val secret = p.getString(KEY_PENDING_SECRET, null) ?: return null
        val reqId = p.getString(KEY_PENDING_REQUEST_ID, null) ?: return null
        val createdAt = p.getLong(KEY_PENDING_CREATED_AT, 0L)
        // 一次性消费
        p.edit()
            .remove(KEY_PENDING_CODE)
            .remove(KEY_PENDING_APP_ID)
            .remove(KEY_PENDING_SECRET)
            .remove(KEY_PENDING_REQUEST_ID)
            .remove(KEY_PENDING_CREATED_AT)
            .apply()
        // TTL 保护:超过 10 分钟的 pending exchange 视为过期,不再 resume
        if (System.currentTimeMillis() - createdAt > PENDING_TTL_MS) return null
        return PendingExchange(code, appId, secret, reqId, createdAt)
    }

    override fun hasPendingExchange(): Boolean {
        val p = requirePrefs() ?: return false
        if (p.getString(KEY_PENDING_CODE, null) == null) return false
        // fix-MEDIUM(feishu M3):与 consumePendingExchange 保持一致 — 过期 pending
        // (createdAt 超过 PENDING_TTL_MS) 不再视为 "has pending",避免冷启动 UI
        // 误显示"恢复上次未完成授权"按钮去 resume 一段已经过期的 OAuth code。
        val createdAt = p.getLong(KEY_PENDING_CREATED_AT, 0L)
        return System.currentTimeMillis() - createdAt <= PENDING_TTL_MS
    }

    override suspend fun persistOAuthState(state: String, ttlMs: Long) {
        withContext(Dispatchers.IO) {
            val p = requirePrefs() ?: return@withContext
            val expiresAt = System.currentTimeMillis() + ttlMs
            p.edit()
                .putString(KEY_OAUTH_STATE_VALUE, state)
                .putLong(KEY_OAUTH_STATE_EXPIRES, expiresAt)
                .apply()
        }
    }

    override fun consumeOAuthState(): String? {
        val p = requirePrefs() ?: return null
        val value = p.getString(KEY_OAUTH_STATE_VALUE, null) ?: return null
        val expiresAt = p.getLong(KEY_OAUTH_STATE_EXPIRES, 0L)
        // 一次性消费:不论是否过期都清,避免 replay
        p.edit()
            .remove(KEY_OAUTH_STATE_VALUE)
            .remove(KEY_OAUTH_STATE_EXPIRES)
            .apply()
        if (System.currentTimeMillis() > expiresAt) return null
        return value
    }

    /** 取 prefs;若 Keystore 不可用返回 null(调用方应继续 fail-safe,不抛错掩盖)。 */
    private fun requirePrefs(): SharedPreferences? = prefs

    private fun openEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun <T> keyFlow(key: String, transform: (String?) -> T?): Flow<T?> = callbackFlow {
        trySend(transform(prefs?.getString(key, null)))
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) trySend(transform(prefs?.getString(key, null)))
        }
        prefs?.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs?.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    private fun identityString(s: String?): String? = s

    companion object {
        private const val TAG = "FeishuAuth"
        internal const val PREFS_FILE = "feishu_oauth_prefs"
        private const val KEY_APP_ID = "feishu_app_id"
        private const val KEY_ACCESS = "feishu_access_token"
        private const val KEY_REFRESH = "feishu_refresh_token"
        private const val KEY_EXPIRES = "feishu_token_expires_at"
        private const val KEY_FOLDER = "feishu_folder_token"

        // fix H8:KEY_SECRET removed;appSecret now in-memory only
        private const val KEY_OAUTH_STATE_VALUE = "feishu_oauth_state_value"
        private const val KEY_OAUTH_STATE_EXPIRES = "feishu_oauth_state_expires_at"

        // fix-2026-06-26-review-r3 C2/C3:appSecret + pending exchange 落盘 keys
        private const val KEY_SECRET_PREFIX = "feishu_app_secret_"
        private const val KEY_PENDING_CODE = "feishu_pending_code"
        private const val KEY_PENDING_APP_ID = "feishu_pending_app_id"
        private const val KEY_PENDING_SECRET = "feishu_pending_secret"
        private const val KEY_PENDING_REQUEST_ID = "feishu_pending_request_id"
        private const val KEY_PENDING_CREATED_AT = "feishu_pending_created_at"
        private const val PENDING_TTL_MS: Long = 10L * 60L * 1000L // 10 min

        private fun secretKeyFor(requestId: String) = KEY_SECRET_PREFIX + requestId
    }
}
