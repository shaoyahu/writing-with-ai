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
 * feishu-oauth-flow · 飞书凭证 + tenant_access_token 加密存储(design D1)。
 *
 * 独立 EncryptedSharedPreferences 文件 `feishu_oauth_prefs.xml`(与 apikey 文件分开)。
 * key 集合:`feishu_app_id` / `feishu_app_secret` / `feishu_tenant_token` /
 * `feishu_token_expires_at`。
 *
 * spec: openspec/changes/feishu-oauth-flow/specs/feishu-auth/spec.md
 * "Tenant access token acquisition" / "Token storage and lifecycle"
 */
interface FeishuAuthStore {
    val appId: Flow<String?>
    val appSecret: Flow<String?>
    val tenantAccessToken: Flow<String?>
    val expiresAt: Flow<Long?>
    val authState: StateFlow<FeishuAuthState>

    suspend fun setCredentials(appId: String, appSecret: String)
    suspend fun persistToken(token: String, expiresAt: Long)
    suspend fun setAuthState(state: FeishuAuthState)
    suspend fun clearAll()

    /** 同步读,用于 TenantTokenProvider cold start 路径(进程内 hot path)。 */
    fun getCredentialsSnapshot(): Pair<String, String>?
    fun getTokenSnapshot(): Pair<String, Long>?
}

@Singleton
class FeishuAuthStoreImpl
@Inject
constructor(
    @ApplicationContext private val context: Context
) : FeishuAuthStore {
    private val prefs: SharedPreferences? =
        runCatching { openEncryptedPrefs() }.getOrElse { e ->
            Log.w(TAG, "EncryptedSharedPreferences init failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }

    private val stateFlow = MutableStateFlow(FeishuAuthState.DISCONNECTED)

    override val appId: Flow<String?> = keyFlow(KEY_APP_ID, ::identityString)
    override val appSecret: Flow<String?> = keyFlow(KEY_APP_SECRET, ::identityString)
    override val tenantAccessToken: Flow<String?> = keyFlow(KEY_TOKEN, ::identityString)
    override val expiresAt: Flow<Long?> = keyFlow(KEY_EXPIRES) { it?.toLongOrNull() }

    override val authState: StateFlow<FeishuAuthState> = stateFlow.asStateFlow()

    override suspend fun setCredentials(appId: String, appSecret: String) = withContext(Dispatchers.IO) {
        val p = prefs ?: return@withContext
        p.edit()
            .putString(KEY_APP_ID, appId)
            .putString(KEY_APP_SECRET, appSecret)
            .apply()
        if (stateFlow.value !in setOf(FeishuAuthState.CONNECTED, FeishuAuthState.TOKEN_FETCHING)) {
            stateFlow.value = FeishuAuthState.CONFIGURED
        }
    }

    override suspend fun persistToken(token: String, expiresAt: Long) = withContext(Dispatchers.IO) {
        val p = prefs ?: return@withContext
        p.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_EXPIRES, expiresAt.toString())
            .apply()
        stateFlow.value = FeishuAuthState.CONNECTED
    }

    override suspend fun setAuthState(state: FeishuAuthState) {
        stateFlow.value = state
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        val p = prefs ?: return@withContext
        p.edit().clear().apply()
        stateFlow.value = FeishuAuthState.DISCONNECTED
    }

    override fun getCredentialsSnapshot(): Pair<String, String>? {
        val p = prefs ?: return null
        val id = p.getString(KEY_APP_ID, null) ?: return null
        val secret = p.getString(KEY_APP_SECRET, null) ?: return null
        return id to secret
    }

    override fun getTokenSnapshot(): Pair<String, Long>? {
        val p = prefs ?: return null
        val token = p.getString(KEY_TOKEN, null) ?: return null
        val expires = p.getString(KEY_EXPIRES, null)?.toLongOrNull() ?: return null
        return token to expires
    }

    private fun openEncryptedPrefs(): SharedPreferences {
        val masterKey =
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
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
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == key) {
                    trySend(transform(prefs?.getString(key, null)))
                }
            }
        prefs?.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs?.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    private fun identityString(s: String?): String? = s

    companion object {
        private const val TAG = "FeishuAuth"
        internal const val PREFS_FILE = "feishu_oauth_prefs"
        private const val KEY_APP_ID = "feishu_app_id"
        private const val KEY_APP_SECRET = "feishu_app_secret"
        private const val KEY_TOKEN = "feishu_tenant_token"
        private const val KEY_EXPIRES = "feishu_token_expires_at"
    }
}
