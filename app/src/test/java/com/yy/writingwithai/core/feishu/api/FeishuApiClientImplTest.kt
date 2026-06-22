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
 * 用 mockwebserver 模拟飞书响应,验证 HTTP 状态码 + body code 映射 FeishuError。
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
                """{"code":0,"msg":"ok","data":{"document_id":"docXYZ","url":"https://feishu.cn/docx/docXYZ"}}"""
            )
        )
        val result = api.createDocument("title")
        assertEquals("docXYZ", result.docId)
        assertEquals("https://feishu.cn/docx/docXYZ", result.docUrl)
    }

    @Test
    fun `createDocument synthesizes url when missing`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"msg":"ok","data":{"document_id":"docXYZ"}}"""
            )
        )
        val result = api.createDocument("title")
        assertEquals("docXYZ", result.docId)
        assertTrue(result.docUrl.contains("docXYZ"))
    }
}
