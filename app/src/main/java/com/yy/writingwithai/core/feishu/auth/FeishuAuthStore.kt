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

    suspend fun persistAppSecret(secret: String)
    suspend fun clearAppSecret()
    fun getAppSecretSnapshot(): String?
    fun getAppIdAndSecret(): Pair<String, String>?
}

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

    override suspend fun persistAppSecret(secret: String) {
        withContext(Dispatchers.IO) {
            requirePrefs()?.edit()?.putString(KEY_SECRET, secret)?.apply()
        }
    }
    override suspend fun clearAppSecret() {
        withContext(Dispatchers.IO) {
            requirePrefs()?.edit()?.remove(KEY_SECRET)?.apply()
        }
    }
    override fun getAppSecretSnapshot(): String? = requirePrefs()?.getString(KEY_SECRET, null)
    override fun getAppIdAndSecret(): Pair<String, String>? {
        val p = requirePrefs() ?: return null
        val id = p.getString(KEY_APP_ID, null) ?: return null
        val sec = p.getString(KEY_SECRET, null) ?: return null
        return id to sec
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
        private const val KEY_SECRET = "feishu_app_secret"
    }
}
