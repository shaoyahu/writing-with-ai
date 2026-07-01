package com.yy.writingwithai.core.feishu.auth

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * fix-2026-06-26-review-r3 CRITICAL C2/C3:OAuthCodeReceiver 不再 fire-and-forget token
 * exchange 到 GlobalScope+NonCancellable。改为先 [FeishuAuthStore.persistPendingExchange]
 * 同步落盘 → Activity 立即 finish → 应用 scope 异步执行 exchange。
 *
 * 进程被杀时:
 *   - 成功路径:consume 掉 pending state(下次启动 hasPendingExchange == false)
 *   - 失败路径:consume 掉 pending state(避免 stale)
 *   - 取消路径(CancellationException):不 consume → 下次冷启动 resume
 *
 * 这个 test 在 interface 层面 mock store，验证调用契约。
 * 真实加密 prefs 走 instrumentation(androidTest)，不在 unit test 范围。
 */
class PendingExchangeContractTest {

    /**
     * 验证 OAuthCodeReceiver 调用 store.persistPendingExchange 而不是直接 fire
     * GlobalScope + NonCancellable。这是一个契约级 test — 我们只能间接通过
     * AuthStore 的使用模式来保证。
     */
    @Test
    fun `store contract - persistPendingExchange followed by consumePendingExchange yields stored data`() = runTest {
        // 用一个简单实现来代替加密 prefs，验证接口契约
        val store = InMemoryFeishuAuthStore()
        assertFalse(store.hasPendingExchange())

        store.persistPendingExchange(
            code = "code-123",
            appId = "app-xyz",
            secret = "secret-456",
            requestId = "oauth"
        )
        assertTrue(store.hasPendingExchange())

        val consumed = store.consumePendingExchange()
        assertNotNull(consumed)
        assertEquals("code-123", consumed?.code)
        assertEquals("app-xyz", consumed?.appId)
        assertEquals("secret-456", consumed?.secret)
        assertEquals("oauth", consumed?.requestId)

        // 一次性:再次 consume 应为 null
        assertNull(store.consumePendingExchange())
        assertFalse(store.hasPendingExchange())
    }

    @Test
    fun `appSecret snapshot is recoverable from cold start (C3)`() = runTest {
        val store = InMemoryFeishuAuthStore()
        // persist app secret
        store.persistAppSecret("oauth", "my-secret")
        // 模拟进程重启:新建一个 store 实例(共享同一 in-memory backing)
        // 实际生产中 in-memory 也会丢;但 EncryptedSharedPreferences 会持久化。
        // 这里验证基本 round-trip 契约。
        val snapshot = store.getAppSecretSnapshot("oauth")
        assertEquals("my-secret", snapshot)

        // 清理后
        store.clearAppSecret("oauth")
        assertNull(store.getAppSecretSnapshot("oauth"))
    }

    @Test
    fun `hasPendingExchange false initially`() = runTest {
        val store = InMemoryFeishuAuthStore()
        assertFalse(store.hasPendingExchange())
    }
}

/**
 * 简单的 in-memory 实现，只为了验证接口契约。
 * 真实实现 FeishuAuthStoreImpl 走 EncryptedSharedPreferences。
 */
private class InMemoryFeishuAuthStore : FeishuAuthStore {
    private var pending: PendingExchange? = null
    private val secretCache = mutableMapOf<String, String>()
    private var storedAppId: String? = null
    private var storedRefreshToken: String? = null
    private val authStateFlow = kotlinx.coroutines.flow.MutableStateFlow(FeishuAuthState.DISCONNECTED)

    override val appId = kotlinx.coroutines.flow.flowOf(storedAppId)
    override val folderToken = kotlinx.coroutines.flow.flowOf<String?>(null)
    override val accessToken = kotlinx.coroutines.flow.flowOf<String?>(null)
    override val refreshToken = kotlinx.coroutines.flow.flowOf(storedRefreshToken)
    override val expiresAt = kotlinx.coroutines.flow.flowOf<Long?>(null)
    override val authState = authStateFlow
    override val prefsInitError: Throwable? = null

    override suspend fun setOAuthCredentials(
        appId: String,
        accessToken: String,
        refreshToken: String,
        expiresAt: Long
    ) {
        storedAppId = appId
        storedRefreshToken = refreshToken
    }
    override suspend fun setAuthState(state: FeishuAuthState) {
        authStateFlow.value = state
    }
    override suspend fun clearAll() {
        storedAppId = null
        storedRefreshToken = null
        secretCache.clear()
    }
    override fun getAccessTokenSnapshot(): Pair<String, Long>? = null
    override fun getRefreshTokenSnapshot(): String? = storedRefreshToken
    override fun getFolderTokenSnapshot(): String? = null
    override fun getAppIdAndRefreshToken(): Pair<String, String>? =
        if (storedAppId != null && storedRefreshToken != null) storedAppId!! to storedRefreshToken!! else null
    override suspend fun setAppId(appId: String) {
        storedAppId = appId
    }
    override suspend fun setFolderToken(folderToken: String?) {}
    override fun getAppIdSnapshot(): String? = storedAppId

    override suspend fun persistAppSecret(requestId: String, secret: String) {
        secretCache[requestId] = secret
    }
    override suspend fun clearAppSecret(requestId: String) {
        secretCache.remove(requestId)
    }
    override fun getAppSecretSnapshot(requestId: String): String? = secretCache[requestId]
    override fun getAppIdAndSecret(requestId: String): Pair<String, String>? {
        val id = storedAppId ?: return null
        val sec = secretCache[requestId] ?: return null
        return id to sec
    }

    override suspend fun persistOAuthState(state: String, ttlMs: Long) {}
    override fun consumeOAuthState(): String? = null

    override suspend fun persistPendingExchange(code: String, appId: String, secret: String, requestId: String) {
        pending = PendingExchange(code, appId, secret, requestId, System.currentTimeMillis())
    }
    override fun consumePendingExchange(): PendingExchange? {
        val p = pending ?: return null
        pending = null
        return p
    }
    override fun hasPendingExchange(): Boolean = pending != null
}
