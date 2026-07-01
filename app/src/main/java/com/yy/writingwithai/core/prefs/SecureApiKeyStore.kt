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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * 用 lifecycle pause 时间戳简化，ShakeDetector 留 M5 polish)。
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

    /** fix-ai-config-ux: 返回当前所有已配置 apikey 的 providerId 集合，含实时监听。 */
    fun observeConfiguredProviders(): Flow<Set<String>>
}

@Singleton
class SecureApiKeyStoreImpl
@Inject
constructor(
    @ApplicationContext private val context: Context
) : SecureApiKeyStore {
    private val prefs: SharedPreferences? =
        runCatching { openEncryptedPrefs() }.getOrElse { e ->
            // KeyStore 损坏(罕见:设备 root 后 / 系统重置):走 fallback，记一行日志
            // (不 log apikey，只 log 类名 + 异常类型)。
            Log.w(TAG, "EncryptedSharedPreferences init failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }

    // F1 fix H5:review r1 lastPauseAt race — 用 Mutex 包读 + 写，@Volatile 不够:
    // onActivityPaused(主线程同步)与 updateRevealState(suspend 协程)分属两条调度路径，
    // 仅靠 volatile 仍有"pause 写入和 expiresAt 计算交错"的窗口(读到的 lastPauseAt 比
    // 实际 pause 时刻晚几 ms,expiresAt 也就短几 ms,UI 倒计时跳秒)。
    // 选 Mutex 而非 AtomicLong 的原因:临界区是复合操作(读 lastPauseAt + 计算 pausedFor),
    // Mutex.withLock 一条锁链搞定;AtomicLong 还要再加一道对 pausedFor 的算术保护，
    // 反而容易写漏。Mutex 在主线程 tryLock 失败时也只是一帧延迟，语义不变。
    private val lifecycleLock = Mutex()
    private var lastPauseAt: Long = 0L

    // review r2 修:mutableMapOf 返回 LinkedHashMap 非线程安全。reveal() 从 Main dispatcher
    // 调用 getOrPut,clear()/clearAll() 从 IO dispatcher 修改 map，并发场景下可能抛
    // ConcurrentModificationException。改为 ConcurrentHashMap。
    private val revealStates = java.util.concurrent.ConcurrentHashMap<String, MutableStateFlow<RevealState>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // 用 ActivityLifecycleCallbacks 跟踪"任意 activity 切到 onPause" → lastPauseAt = now。
        (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) {
                    // F1 fix: 写入 lastPauseAt 走 lifecycleLock，让 updateRevealState 看到一致的快照。
                    // 主线程用 tryLock 不阻塞 lifecycle 调度;失败时 next pause 会覆盖，语义不变。
                    val now = System.currentTimeMillis()
                    if (lifecycleLock.tryLock()) {
                        try {
                            lastPauseAt = now
                        } finally {
                            lifecycleLock.unlock()
                        }
                    }
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
            // fix-2026-06-30-full-review-r1 MEDIUM M1:apply() 异步落盘，app 崩溃可能丢
            // apikey(用户输入的密钥无法恢复)。改 commit() 同步落盘，在 IO 调度器上
            // 不阻塞主线程。clear() / clearAll() 同理。
            p.edit().putString(keyFor(providerId), apikey).commit()
        }
    }

    override suspend fun get(providerId: String): String? = withContext(Dispatchers.IO) {
        prefs?.getString(keyFor(providerId), null)
    }

    override suspend fun has(providerId: String): Boolean = get(providerId) != null

    override suspend fun clear(providerId: String) {
        withContext(Dispatchers.IO) {
            val p = prefs ?: return@withContext
            p.edit().remove(keyFor(providerId)).commit()
            revealStates[providerId]?.value = RevealState.Hidden
        }
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            val p = prefs ?: return@withContext
            p.edit().clear().commit()
            revealStates.values.forEach { it.value = RevealState.Hidden }
        }
    }

    override fun reveal(providerId: String): StateFlow<RevealState> {
        val flow = revealStates.getOrPut(providerId) { MutableStateFlow(RevealState.Hidden) }
        // 触发一次 reveal 计算:非阻塞，fire-and-forget。
        scope.launch { updateRevealState(providerId, flow) }
        return flow.asStateFlow()
    }

    override fun observeConfiguredProviders(): Flow<Set<String>> = callbackFlow {
        val current = prefs?.all?.keys.orEmpty()
            .filter { it.startsWith("apikey_") }
            .map { it.removePrefix("apikey_") }
            .toSet()
        trySend(current)
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key != null && key.startsWith("apikey_")) {
                    val next = prefs?.all?.keys.orEmpty()
                        .filter { it.startsWith("apikey_") }
                        .map { it.removePrefix("apikey_") }
                        .toSet()
                    trySend(next)
                }
            }
        prefs?.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs?.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    private suspend fun updateRevealState(providerId: String, flow: MutableStateFlow<RevealState>) {
        if (prefs == null) {
            flow.value = RevealState.KeystoreFailed
            return
        }
        // fix-2026-06-26-review-r3 H15:reveal() fire-and-forget launch 与 clear() 同步改
        // Hidden 之间有 race。`prefs.getString` IO 期间 clear 可能已先完成 → 协程恢复
        // 后仍 emit Revealed 覆盖 Hidden。这里进入 suspend 前先 snapshot 当前 flow 值，
        // 仅在 snapshot 时仍为 Hidden 才继续 reveal;若已被 clear() 抢先 → 直接退出。
        val snapshotAtStart = flow.value
        if (snapshotAtStart !is RevealState.Hidden) {
            // 已被其他路径(clear / timer 过期 / reveal 续期)写过，不再覆盖。
            return
        }
        // F1 fix: 读 lastPauseAt 走 lifecycleLock.withLock，与 onActivityPaused 写入互斥，
        // pausedFor 计算时 lastPauseAt 不会被并发改写 → expiresAt 偏差消除。
        val now = System.currentTimeMillis()
        val pausedFor = lifecycleLock.withLock {
            if (lastPauseAt == 0L) 0L else now - lastPauseAt
        }
        if (pausedFor >= REVEAL_TIMEOUT_MS) {
            // 再次确认:进入 suspend 期间 clear() 是否抢先置 Hidden。
            if (flow.value is RevealState.Hidden) {
                // 仍是 Hidden → 保留(本来就该 Hidden，无需再写一次)
            }
            return
        }
        val apikey = withContext(Dispatchers.IO) { prefs.getString(keyFor(providerId), null) }
        // H15 fix:IO 期间 clear() 可能已同步将 flow 置 Hidden。这里再次校验 flow.value
        // 仍为 Hidden 才允许 emit Revealed，避免 stale coroutine 覆盖已 clear 的状态。
        if (flow.value !is RevealState.Hidden) {
            return
        }
        if (apikey.isNullOrBlank()) {
            if (flow.value is RevealState.Hidden) {
                // 已是 Hidden，无需再写
            }
            return
        }
        // fix-2026-06-30-full-review-r1 MEDIUM M2:`now` 是 IO 挂起前取的，IO 期间
        // 实际时间已推进 50-200ms,expiresAt 立刻显示为负。改在 IO 之后重算。
        val expiresAt = System.currentTimeMillis() + REVEAL_TIMEOUT_MS
        flow.value = RevealState.Revealed(apikey = apikey, expiresAt = expiresAt)
        // 起一次性 5s timer，过期 emit Hidden。
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
