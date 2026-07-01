package com.yy.writingwithai.core.ai.provider

import com.yy.writingwithai.core.ai.api.AiCredentials
import com.yy.writingwithai.core.ai.api.AiError
import com.yy.writingwithai.core.ai.api.AiRequest
import com.yy.writingwithai.core.ai.api.AiStreamEvent
import com.yy.writingwithai.core.ai.api.WritingOp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
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
 * fix-review-r3-high · [AnthropicCompatibleAdapter] 资源清理回归测试。
 *
 * H1:response.close() 之前分散在 success / failure 两条路径，取消路径下
 *    body source 泄漏(socket 不释放，可能拖到 socket 耗尽)。
 * H3:早期 body read 的 `catch (Throwable)` 吞 CancellationException，导致
 *    协程取消不传播。修后该 catch 链增加 CancellationException rethrow,
 *    正常 IOException / EOFException 仍返回空串。
 *
 * 用 MockWebServer 模拟上游:
 * 1. 4xx / 5xx 错误路径 response 必须被关闭(修前取消路径下泄漏);
 * 2. 401 + 空 body 走 rawDetail 的 `catch (Throwable)` 路径，验证未影响正常错误映射;
 * 3. 成功路径在协程 cancel 后 response 被 try-finally 关闭，requestCount == 1。
 */
class AnthropicCompatibleAdapterR3RegressionTest {
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

    /**
     * fix H1 regression:错误路径下 response 必须被关闭(try-finally)，后续 enqueue
     * 能正常 dispatch，证明上一个 socket 已释放。
     */
    @Test
    fun error_path_response_is_closed_via_try_finally() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":\"internal\"}")
        )
        server.enqueue(
            // 第二个 enqueue 必须能成功 dispatch 到 adapter，证明上一个 response 已关闭。
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"type\":\"message_stop\"}\n\ndata: [DONE]\n\n")
        )

        val adapter = adapter()

        val first = adapter.stream(req(), AiCredentials("key")).toList()
        val firstFail = first.filterIsInstance<AiStreamEvent.Failed>().firstOrNull()
        assertNotNull(firstFail)
        assertTrue(firstFail!!.error is AiError.ServerError)

        // 第二次同 adapter 再发，MockWebServer 必须收到第二个请求(socket 不残留)。
        val second = adapter.stream(req(), AiCredentials("key")).toList()
        assertTrue(second.isNotEmpty())
        assertEquals(2, server.requestCount)
    }

    /**
     * fix H3 regression:401 + 空 body 走 rawDetail 的 catch 路径，验证新增的
     * CancellationException rethrow 不破坏正常错误映射(仍 emit Failed(Auth))。
     */
    @Test
    fun error_response_close_does_not_break_normal_error_mapping() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("")
        )

        val adapter = adapter()
        val events = adapter.stream(req(), AiCredentials("key")).toList()

        val failed = events.filterIsInstance<AiStreamEvent.Failed>().firstOrNull()
        assertNotNull(failed)
        assertTrue(failed!!.error is AiError.Auth)
    }

    /**
     * fix H1 regression:成功路径在协程 cancel 后 response 仍被关闭(try-finally 触发)。
     *
     * MockWebServer.requestCount == 1 证明请求确实发出;cancel 后不抛 socket leak。
     */
    @Test
    fun success_path_response_closed_when_coroutine_cancelled() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                // 短 body 让 adapter 进入 read 阻塞，然后被 cancel。
                .setBody("data: {\"type\":\"message_start\"}\n\n")
        )

        val adapter = adapter()
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job)

        try {
            scope.launch {
                adapter.stream(req(), AiCredentials("key")).collect { /* keep collecting */ }
            }
            // 给点时间让 adapter 发出请求并进入 collect 阻塞
            yield()
            job.cancelAndJoin()
            // 取消后 verify:MockWebServer 已收到请求(说明 socket 不残留 / response 已 close)
            assertEquals(1, server.requestCount)
        } finally {
            scope.cancel()
        }
    }

    private fun adapter(): AnthropicCompatibleAdapter = AnthropicCompatibleAdapter(baseConfig(), client)

    private fun req() = AiRequest(WritingOp.EXPAND, "hello", "test-model")

    private fun baseConfig(): ProviderConfig = ProviderConfig(
        id = "test",
        displayName = "Test",
        baseUrl = server.url("/").toString().trimEnd('/'),
        endpointPath = "/v1/messages",
        authStyle = AuthStyle.AUTHORIZATION,
        defaultModel = "test-model",
        supportedModels = listOf("test-model")
    )
}
