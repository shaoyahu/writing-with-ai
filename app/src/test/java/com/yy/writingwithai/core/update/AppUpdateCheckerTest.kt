package com.yy.writingwithai.core.update

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * app-self-hosted-update · AppUpdateChecker 单测。
 *
 * 用 mockwebserver 模拟服务端响应，验证:
 * - 200 + 新版本 → fetch 返回 Success(AppUpdateManifest)
 * - 500 → fetch 返回 Failure(Http(500))
 * - JSON 损坏 → fetch 返回 Failure(Parse)
 * - 缺字段 → 走默认值
 */
class AppUpdateCheckerTest {

    private lateinit var server: MockWebServer
    private lateinit var checker: AppUpdateChecker

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val http = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val orig = chain.request()
                val rewritten = orig.newBuilder()
                    .url(
                        orig.url.newBuilder()
                            .host(server.hostName)
                            .port(server.port)
                            .scheme("http")
                            .build()
                    ).build()
                chain.proceed(rewritten)
            }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        checker = AppUpdateChecker(http, Json { ignoreUnknownKeys = true })
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `200 with valid manifest returns manifest`() = runTest {
        val body = """
            {
              "versionCode": 12,
              "versionName": "0.5.0",
              "apkUrl": "https://example.com/writing-with-ai-12.apk",
              "apkSize": 12345678,
              "apkSha256": "abc1234567890def",
              "releaseNotes": "fix stuff",
              "releasedAt": "2026-06-24T10:00:00Z",
              "minSupportedVersionCode": 1,
              "mandatory": false
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))

        val result = checker.fetch()
        assertTrue(result.isSuccess)
        val manifest = result.getOrThrow()
        assertEquals(12, manifest.versionCode)
        assertEquals("0.5.0", manifest.versionName)
        assertEquals("https://example.com/writing-with-ai-12.apk", manifest.apkUrl)
        assertEquals("abc1234567890def", manifest.apkSha256)
    }

    @Test
    fun `500 returns Http error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))

        val result = checker.fetch()
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex is UpdateError.Http, "expected Http but got ${ex?.javaClass}")
        assertEquals(500, (ex as UpdateError.Http).code)
    }

    @Test
    fun `200 with malformed JSON returns Parse error`() = runTest {
        server.enqueue(MockResponse().setBody("{not json").setResponseCode(200))

        val result = checker.fetch()
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is UpdateError.Parse, "expected Parse but got ${ex?.javaClass}")
    }

    @Test
    fun `200 with missing fields uses defaults`() = runTest {
        val body = """{"versionCode": 1, "versionName": "0.1", "apkUrl": "u", "apkSize": 1, "apkSha256": "x"}"""
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))

        val result = checker.fetch()
        assertTrue(result.isSuccess)
        val manifest = result.getOrThrow()
        assertEquals("", manifest.releaseNotes)
        assertEquals("", manifest.releasedAt)
        assertEquals(1, manifest.minSupportedVersionCode)
        assertEquals(false, manifest.mandatory)
    }

    @Test
    fun `empty body returns Parse error`() = runTest {
        server.enqueue(MockResponse().setBody("").setResponseCode(200))

        val result = checker.fetch()
        assertTrue(result.isFailure)
        assertNull(result.getOrNull())
    }
}
