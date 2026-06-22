package com.yy.writingwithai.core.feishu.auth

import com.yy.writingwithai.core.feishu.api.FeishuError
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * feishu-oauth-flow · TenantTokenProvider 单测(design D2)。
 *
 * spec: openspec/changes/feishu-oauth-flow/specs/feishu-auth/spec.md
 * "Token storage and lifecycle"
 *
 * 通过 [StubTokenProvider] 子类覆盖 httpClient,指向 mockwebserver URL(原 impl 写死
 * `https://open.feishu.cn/...`,stub 用一个 interceptor 把 host 改写到 mockwebserver)。
 */
class TenantTokenProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var store: FakeFeishuAuthStore
    private lateinit var client: OkHttpClient
    private lateinit var provider: TenantTokenProvider

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = FakeFeishuAuthStore()
        client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val rewritten = original.newBuilder()
                    .url(
                        original.url.newBuilder()
                            .host(server.hostName)
                            .port(server.port)
                            .scheme("http")
                            .build()
                    )
                    .build()
                chain.proceed(rewritten)
            }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        provider = StubTokenProvider(store, client)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getToken returns from store snapshot when valid`() = runTest {
        store.seedToken("t-from-store", System.currentTimeMillis() + 60 * 60 * 1000L)
        val token = provider.getToken()
        assertEquals("t-from-store", token)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `getToken fetches from server when store empty and credentials present`() = runTest {
        store.seedCredentials("cli_test", "secret_test")
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"msg":"ok","tenant_access_token":"t-new","expire":7200}"""
            )
        )
        val token = provider.getToken()
        assertEquals("t-new", token)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `getToken throws NotAuthorized when no credentials and no token`() = runTest {
        assertThrows(FeishuError.NotAuthorized::class.java) {
            kotlinx.coroutines.runBlocking { provider.getToken() }
        }
        assertEquals(0, server.requestCount)
        assertEquals(FeishuAuthState.FAILED, store.authState.value)
    }

    @Test
    fun `getToken throws BadRequest on feishu business error`() = runTest {
        store.seedCredentials("cli_test", "secret_test")
        server.enqueue(
            MockResponse().setBody(
                """{"code":10003,"msg":"invalid app_id","tenant_access_token":"","expire":0}"""
            )
        )
        val ex = assertThrows(FeishuError.BadRequest::class.java) {
            kotlinx.coroutines.runBlocking { provider.getToken() }
        }
        assertEquals(10003, ex.code)
        assertEquals(FeishuAuthState.FAILED, store.authState.value)
    }

    @Test
    fun `invalidate clears in-memory cache and forces refetch`() = runTest {
        store.seedCredentials("cli_test", "secret_test")
        server.enqueue(
            MockResponse().setBody("""{"code":0,"msg":"ok","tenant_access_token":"t-1","expire":7200}""")
        )
        val first = provider.getToken()
        assertEquals("t-1", first)

        server.enqueue(
            MockResponse().setBody("""{"code":0,"msg":"ok","tenant_access_token":"t-2","expire":7200}""")
        )
        provider.invalidate()
        val second = provider.getToken()
        assertEquals("t-2", second)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `getToken persists fetched token to store`() = runTest {
        store.seedCredentials("cli_test", "secret_test")
        server.enqueue(
            MockResponse().setBody("""{"code":0,"msg":"ok","tenant_access_token":"t-persist","expire":7200}""")
        )
        provider.getToken()
        val persisted = store.getTokenSnapshot()
        assertEquals("t-persist", persisted?.first)
        assertEquals(FeishuAuthState.CONNECTED, store.authState.value)
    }
}

/** 测试子类:注入 mockwebserver 的 OkHttpClient。 */
private class StubTokenProvider(
    store: FeishuAuthStore,
    client: OkHttpClient
) : TenantTokenProvider(store) {
    override val httpClient: OkHttpClient = client
}

/** 测试用 fake store:直接改 in-memory 值,不依赖 EncryptedSharedPreferences。 */
private class FakeFeishuAuthStore : FeishuAuthStore {
    private var credId: String? = null
    private var credSecret: String? = null
    private var tok: String? = null
    private var exp: Long? = null
    override val authState = MutableStateFlow(FeishuAuthState.DISCONNECTED)

    override val appId: Flow<String?> = flowOf(credId)
    override val appSecret: Flow<String?> = flowOf(credSecret)
    override val tenantAccessToken: Flow<String?> = flowOf(tok)
    override val expiresAt: Flow<Long?> = flowOf(exp)

    fun seedCredentials(id: String, secret: String) {
        credId = id
        credSecret = secret
        authState.value = FeishuAuthState.CONFIGURED
    }

    fun seedToken(t: String, e: Long) {
        tok = t
        exp = e
        authState.value = FeishuAuthState.CONNECTED
    }

    override suspend fun setCredentials(appId: String, appSecret: String) {
        credId = appId
        credSecret = appSecret
    }

    override suspend fun persistToken(token: String, expiresAt: Long) {
        tok = token
        exp = expiresAt
        authState.value = FeishuAuthState.CONNECTED
    }

    override suspend fun setAuthState(state: FeishuAuthState) {
        authState.value = state
    }

    override suspend fun clearAll() {
        credId = null
        credSecret = null
        tok = null
        exp = null
        authState.value = FeishuAuthState.DISCONNECTED
    }

    override fun getCredentialsSnapshot(): Pair<String, String>? {
        return if (credId != null && credSecret != null) credId!! to credSecret!! else null
    }

    override fun getTokenSnapshot(): Pair<String, Long>? {
        return if (tok != null && exp != null) tok!! to exp!! else null
    }
}
