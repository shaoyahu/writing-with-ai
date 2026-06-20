package com.yy.writingwithai.core.prefs

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M4-4 secure-prefs · AI provider apikey 加密存储。
 *
 * 走 `EncryptedSharedPreferences` + Tink AES256_GCM(M4-4 design D2 拍板);
 * 文件名 `writingwithai_secure_prefs.xml`;key 集合按 provider 命名 `apikey_<providerId>`。
 *
 * spec: openspec/changes/onboarding-consent/specs/secure-prefs/spec.md
 * "SecureApiKeyStore persists apikeys via EncryptedSharedPreferences"
 *
 * 5s 自动清屏走 ActivityLifecycleCallbacks 跟踪 lastPauseAt(M4-4 design D5,
 * 用 lifecycle pause 时间戳简化,ShakeDetector 留 M5 polish)。
 */
sealed interface RevealState {
    data object Hidden : RevealState

    data class Revealed(
        val apikey: String,
        val expiresAt: Long
    ) : RevealState

    data object KeystoreFailed : RevealState
}

interface SecureApiKeyStore {
    suspend fun save(providerId: String, apikey: String)

    suspend fun get(providerId: String): String?

    suspend fun has(providerId: String): Boolean

    suspend fun clear(providerId: String)

    suspend fun clearAll()

    fun reveal(providerId: String): StateFlow<RevealState>
}

@Singleton
class SecureApiKeyStoreImpl
@Inject
constructor(
    @ApplicationContext private val context: Context
) : SecureApiKeyStore {
    private val prefs: SharedPreferences? =
        runCatching { openEncryptedPrefs() }.getOrElse { e ->
            // KeyStore 损坏(罕见:设备 root 后 / 系统重置):走 fallback,记一行日志
            // (不 log apikey,只 log 类名 + 异常类型)。
            Log.w(TAG, "EncryptedSharedPreferences init failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }

    @Volatile
    private var lastPauseAt: Long = 0L
    private val revealStates = mutableMapOf<String, MutableStateFlow<RevealState>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // 用 ActivityLifecycleCallbacks 跟踪"任意 activity 切到 onPause" → lastPauseAt = now。
        (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) {
                    lastPauseAt = System.currentTimeMillis()
                }

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }

    override suspend fun save(providerId: String, apikey: String) {
        withContext(Dispatchers.IO) {
            val p = prefs ?: return@withContext
            p.edit().putString(keyFor(providerId), apikey).apply()
        }
    }

    override suspend fun get(providerId: String): String? = withContext(Dispatchers.IO) {
        prefs?.getString(keyFor(providerId), null)
    }

    override suspend fun has(providerId: String): Boolean = get(providerId) != null

    override suspend fun clear(providerId: String) {
        withContext(Dispatchers.IO) {
            val p = prefs ?: return@withContext
            p.edit().remove(keyFor(providerId)).apply()
            revealStates[providerId]?.value = RevealState.Hidden
        }
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            val p = prefs ?: return@withContext
            p.edit().clear().apply()
            revealStates.values.forEach { it.value = RevealState.Hidden }
        }
    }

    override fun reveal(providerId: String): StateFlow<RevealState> {
        val flow = revealStates.getOrPut(providerId) { MutableStateFlow(RevealState.Hidden) }
        // 触发一次 reveal 计算:非阻塞,fire-and-forget。
        scope.launch { updateRevealState(providerId, flow) }
        return flow.asStateFlow()
    }

    private suspend fun updateRevealState(providerId: String, flow: MutableStateFlow<RevealState>) {
        if (prefs == null) {
            flow.value = RevealState.KeystoreFailed
            return
        }
        val now = System.currentTimeMillis()
        val pausedFor = if (lastPauseAt == 0L) 0L else now - lastPauseAt
        if (pausedFor >= REVEAL_TIMEOUT_MS) {
            flow.value = RevealState.Hidden
            return
        }
        val apikey = withContext(Dispatchers.IO) { prefs.getString(keyFor(providerId), null) }
        if (apikey.isNullOrBlank()) {
            flow.value = RevealState.Hidden
            return
        }
        val expiresAt = now + REVEAL_TIMEOUT_MS
        flow.value = RevealState.Revealed(apikey = apikey, expiresAt = expiresAt)
        // 起一次性 5s timer,过期 emit Hidden。
        scope.launch {
            delay(REVEAL_TIMEOUT_MS)
            if (flow.value is RevealState.Revealed) {
                flow.value = RevealState.Hidden
            }
        }
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

    private fun keyFor(providerId: String): String = "apikey_$providerId"

    companion object {
        private const val TAG = "SecurePrefs"
        internal const val PREFS_FILE = "writingwithai_secure_prefs"
        internal const val REVEAL_TIMEOUT_MS = 5_000L
    }
}
