package com.yy.writingwithai.core.ai.provider

import com.yy.writingwithai.core.ai.api.AiCredentials
import com.yy.writingwithai.core.ai.api.AiRequest
import com.yy.writingwithai.core.ai.api.WritingOp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * fix-m5-blockers · 端到端验 [AnthropicCompatibleAdapter] 把真 apikey 落到 HTTP header。
 *
 * 之前 [com.yy.writingwithai.core.ai.CoreAiGateway] 写死 `apikey = "fake-apikey"`,所有真 provider 调用都发假 key。本测试
 * 用 MockWebServer 拦截请求,断言 Authorization / x-api-key / 自定义 header 的值 == caller 传入的 apikey。
 *
 * 不依赖 [com.yy.writingwithai.core.ai.CoreAiGateway] / [com.yy.writingwithai.core.prefs.SecureApiKeyStore] —— 单元测 `AnthropicCompatibleAdapter` 本身
 * 已经断在根因处。
 */
class AnthropicCompatibleAdapterApikeyTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun authorization_header_carries_bearer_apikey() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"type\":\"message_stop\"}\n\ndata: [DONE]\n\n")
        )

        val adapter =
            AnthropicCompatibleAdapter(
                config = baseConfig(authStyle = AuthStyle.AUTHORIZATION),
                client = client
            )

        adapter
            .stream(
                AiRequest(WritingOp.EXPAND, "hello", "model"),
                AiCredentials(apikey = "sk-real-test-123")
            ).first()

        val recorded = server.takeRequest()
        assertEquals(
            "Bearer sk-real-test-123",
            recorded.getHeader("Authorization")
        )
    }

    @Test
    fun x_api_key_header_carries_apikey() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"type\":\"message_stop\"}\n\ndata: [DONE]\n\n")
        )

        val adapter =
            AnthropicCompatibleAdapter(
                config = baseConfig(authStyle = AuthStyle.X_API_KEY),
                client = client
            )

        adapter
            .stream(
                AiRequest(WritingOp.EXPAND, "hello", "model"),
                AiCredentials(apikey = "sk-real-test-123")
            ).first()

        val recorded = server.takeRequest()
        assertEquals(
            "sk-real-test-123",
            recorded.getHeader("x-api-key")
        )
    }

    @Test
    fun custom_header_carries_apikey() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"type\":\"message_stop\"}\n\ndata: [DONE]\n\n")
        )

        val adapter =
            AnthropicCompatibleAdapter(
                config =
                baseConfig(authStyle = AuthStyle.CUSTOM_HEADER)
                    .copy(customAuthHeaderName = "X-Custom-Auth"),
                client = client
            )

        adapter
            .stream(
                AiRequest(WritingOp.EXPAND, "hello", "model"),
                AiCredentials(apikey = "sk-real-test-123")
            ).first()

        val recorded = server.takeRequest()
        assertEquals(
            "sk-real-test-123",
            recorded.getHeader("X-Custom-Auth")
        )
    }

    private fun baseConfig(authStyle: AuthStyle): ProviderConfig = ProviderConfig(
        id = "test",
        displayName = "Test Provider",
        baseUrl = server.url("/").toString().trimEnd('/'),
        endpointPath = "/v1/messages",
        authStyle = authStyle,
        defaultModel = "test-model",
        supportedModels = listOf("test-model")
    )
}
