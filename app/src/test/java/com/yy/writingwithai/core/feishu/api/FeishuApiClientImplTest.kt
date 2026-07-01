package com.yy.writingwithai.core.feishu.api

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * feishu-oauth-flow · FeishuApiClientImpl 单测。
 *
 * spec: openspec/changes/feishu-oauth-flow/specs/feishu-api-client/spec.md
 * "Error classification" / "Tenant token re-fetch on 401"
 *
 * 用 mockwebserver 模拟飞书响应，验证 HTTP 状态码 + body code 映射 FeishuError。
 */
class FeishuApiClientImplTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var api: FeishuApiClientImpl

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
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
        api = FeishuApiClientImpl(client)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getBlocks returns raw body on success`() = runTest {
        val body = """{"code":0,"msg":"ok","data":{"items":[{"block_id":"b1"}]}}"""
        server.enqueue(MockResponse().setBody(body))
        val result = api.getBlocks("doc123")
        assertEquals(body, result)
    }

    @Test
    fun `getBlocks throws BadRequest when feishu body code != 0`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"code":10003,"msg":"invalid app_id","data":{}}""")
        )
        val ex = assertThrows(FeishuError.BadRequest::class.java) {
            kotlinx.coroutines.runBlocking { api.getBlocks("doc123") }
        }
        assertEquals(10003, ex.code)
        assertEquals("invalid app_id", ex.msg)
    }

    @Test
    fun `getBlocks throws TokenInvalid when code == 99991663`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"code":99991663,"msg":"token invalid","data":{}}""")
        )
        assertThrows(FeishuError.TokenInvalid::class.java) {
            kotlinx.coroutines.runBlocking { api.getBlocks("doc123") }
        }
    }

    @Test
    fun `getBlocks throws NotFound on HTTP 404`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        val ex = assertThrows(FeishuError.NotFound::class.java) {
            kotlinx.coroutines.runBlocking { api.getBlocks("doc123") }
        }
        assertTrue(ex.resource.contains("doc123"))
    }

    @Test
    fun `getBlocks throws RateLimited on HTTP 429 with Retry-After`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "5").setBody("rate limited")
        )
        val ex = assertThrows(FeishuError.RateLimited::class.java) {
            kotlinx.coroutines.runBlocking { api.getBlocks("doc123") }
        }
        assertEquals(5, ex.retryAfterSeconds)
    }

    @Test
    fun `getBlocks throws ServerError on HTTP 502`() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("bad gateway"))
        val ex = assertThrows(FeishuError.ServerError::class.java) {
            kotlinx.coroutines.runBlocking { api.getBlocks("doc123") }
        }
        assertEquals(502, ex.code)
    }

    @Test
    fun `getBlocks throws Forbidden on HTTP 403`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("forbidden"))
        assertThrows(FeishuError.Forbidden::class.java) {
            kotlinx.coroutines.runBlocking { api.getBlocks("doc123") }
        }
    }

    @Test
    fun `createDocument parses document_id and url`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                // 飞书 docx create API 响应
                """{"code":0,"msg":"ok","data":{"document":{"document_id":"docXYZ","url":"https://f.cn/d"}}}"""
            )
        )
        val result = api.createDocument("title")
        assertEquals("docXYZ", result.docId)
        assertEquals("https://f.cn/d", result.docUrl)
    }

    @Test
    fun `createDocument synthesizes url when missing`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"msg":"ok","data":{"document":{"document_id":"docXYZ"}}}"""
            )
        )
        val result = api.createDocument("title")
        assertEquals("docXYZ", result.docId)
        assertTrue(result.docUrl.contains("docXYZ"))
    }

    /**
     * fix-2026-06-26-review-r3 HIGH H9:1 MiB cap 必须流式截断，不能先把整段 body 缓存到 heap。
     * 之前 `source.request(Long.MAX_VALUE)` 把整段 body 拉进 buffer 再判断截断 — 已经 OOM 了。
     * 修后用 `readByteArray(MAX_BODY)` — okio 在达到上限后停止从 socket 读。
     *
     * 截断后 body 很可能不再是有效 JSON，所以应抛 NetworkError("JSON parse failed")—
     * 这正是保护性截断的语义:超大响应直接拒收，而不是把残缺数据返回给 caller。
     */
    @Test
    fun `H9 large body is stream-truncated to 1 MiB cap`() = runTest {
        // 拼一个 > 1 MiB 的 body,JSON 中段是 1.5 MiB padding 让截断点刚好切在 padding 中
        val huge = "{\"code\":0,\"msg\":\"ok\",\"data\":{\"items\":[\""
        val padding = "x".repeat((2 * 1024 * 1024) - huge.length - 20) // 总长 ~ 2 MiB
        val tail = "\"}}]}}"
        val fullBody = huge + padding + tail
        server.enqueue(MockResponse().setBody(fullBody))

        // 截断 + 后续 JSON parse 失败 → NetworkError。这是保护性截断的正确语义。
        val ex = assertThrows(FeishuError.NetworkError::class.java) {
            kotlinx.coroutines.runBlocking { api.getBlocks("doc123") }
        }
        assertTrue(
            ex.detail?.contains("JSON parse failed") == true,
            "expected JSON parse failure from truncation, got: ${ex.detail}"
        )
    }

    /**
     * fix-2026-06-26-review-r3 HIGH(re-scan):200 响应的 `data` 字段不是 JsonObject 时
     * 的强转异常也应包装为 NetworkError，而不是让 IllegalArgumentException 逃逸
     * 破坏调用方异常处理。
     */
    @Test
    fun `H-extra data cast failure on 200 response is wrapped as NetworkError`() = runTest {
        // emit valid 200 + body code=0 + data 是 string(不是 object)→ jsonObject 强转抛
        server.enqueue(
            MockResponse().setBody("""{"code":0,"msg":"ok","data":"not-an-object"}""")
        )
        val ex = assertThrows(FeishuError.NetworkError::class.java) {
            kotlinx.coroutines.runBlocking { api.getDocument("doc-parse-fail") }
        }
        assertTrue(
            ex.detail?.contains("data is not a JSON object") == true,
            "expected data-cast-failure detail, got: ${ex.detail}"
        )
    }
}
