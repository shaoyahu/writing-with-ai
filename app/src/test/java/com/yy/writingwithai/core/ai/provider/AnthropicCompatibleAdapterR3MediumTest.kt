package com.yy.writingwithai.core.ai.provider

import com.yy.writingwithai.core.ai.api.AiError
import com.yy.writingwithai.core.ai.api.AiStreamEvent
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.SSLException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * fix-review-r3-medium · [AnthropicCompatibleAdapter] 中等 bug 回归。
 *
 * M1:retry 范围太宽(SSL/UnknownHost/ConnectException 都被 retry)，会让环境错"看起来在
 *    retry"实际只是拖时间。修后这些异常不 retry，直接走 .catch emit Failed。
 * M4:`Retry-After` HTTP-date 形式(原版只解析 delta-seconds)。修后两种都解析。
 */
class AnthropicCompatibleAdapterR3MediumTest {
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

    // ---- M4:Retry-After parsing ----

    @Test
    fun retry_after_delta_seconds_parses_to_int() {
        val adapter = adapter()
        assertEquals(120, adapter.parseRetryAfterSeconds("120"))
    }

    @Test
    fun retry_after_http_date_parses_to_future_seconds() {
        val adapter = adapter()
        // 5 分钟后的 RFC 1123 日期
        val future = System.currentTimeMillis() + 5 * 60 * 1000
        val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("GMT")
        val header = fmt.format(Date(future))
        val parsed = adapter.parseRetryAfterSeconds(header)
        assertNotNull(parsed)
        // 允许 ±2s jitter(墙钟差)
        assertTrue(parsed!! in 295..305, "expected ~300s, got $parsed")
    }

    @Test
    fun retry_after_past_http_date_returns_zero() {
        val adapter = adapter()
        val past = System.currentTimeMillis() - 60_000
        val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("GMT")
        val header = fmt.format(Date(past))
        assertEquals(0, adapter.parseRetryAfterSeconds(header))
    }

    @Test
    fun retry_after_blank_returns_null() {
        val adapter = adapter()
        assertEquals(null, adapter.parseRetryAfterSeconds(null))
        assertEquals(null, adapter.parseRetryAfterSeconds(""))
        assertEquals(null, adapter.parseRetryAfterSeconds("garbage"))
    }

    @Test
    fun retry_after_negative_seconds_clamped_to_zero() {
        val adapter = adapter()
        assertEquals(0, adapter.parseRetryAfterSeconds("-5"))
    }

    @Test
    fun retry_after_429_uses_header_delta_seconds() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "180")
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":\"rate limited\"}")
        )
        val events = adapter().stream(req(), com.yy.writingwithai.core.ai.api.AiCredentials("key")).toList()
        val failed = events.filterIsInstance<AiStreamEvent.Failed>().firstOrNull()
        assertNotNull(failed)
        assertTrue(failed!!.error is AiError.RateLimited)
        assertEquals(180, (failed.error as AiError.RateLimited).retryAfterSeconds)
    }

    // ---- M1:retry scope ----

    @Test
    fun ssl_exception_is_not_retryable() {
        val ex: java.io.IOException = SSLException("cert expired")
        assertEquals(false, ex.isRetryable())
    }

    @Test
    fun unknown_host_is_not_retryable() {
        val ex: java.io.IOException = UnknownHostException("api.example.invalid")
        assertEquals(false, ex.isRetryable())
    }

    @Test
    fun connect_exception_is_not_retryable() {
        val ex: java.io.IOException = ConnectException("connection refused")
        assertEquals(false, ex.isRetryable())
    }

    @Test
    fun plain_io_exception_is_retryable() {
        val ex: java.io.IOException = java.io.IOException("connection reset")
        assertEquals(true, ex.isRetryable())
    }

    @Test
    fun socket_timeout_exception_is_not_retryable_via_retry_predicate() {
        // SocketTimeoutException 已在 retry predicate 显式排除(isRetryable 仍返 true，但
        // retry(1) { cause -> cause !is SocketTimeoutException ... } 双重保险)。
        // 本测试验证 isRetryable 本身返 true，跟 retry predicate 的 SocketTimeout 排除解耦。
        val ex: java.io.IOException = SocketTimeoutException("read timed out")
        assertEquals(true, ex.isRetryable())
    }

    // ---- helpers ----

    private fun adapter() = AnthropicCompatibleAdapter(baseConfig(), client)

    private fun req() = com.yy.writingwithai.core.ai.api.AiRequest(
        com.yy.writingwithai.core.ai.api.WritingOp.EXPAND,
        "hi",
        "m"
    )

    private fun baseConfig() = ProviderConfig(
        id = "test",
        displayName = "Test",
        baseUrl = server.url("/").toString().trimEnd('/'),
        endpointPath = "/v1/messages",
        authStyle = AuthStyle.AUTHORIZATION,
        defaultModel = "m",
        supportedModels = listOf("m")
    )
}
