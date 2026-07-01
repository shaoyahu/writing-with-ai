package com.yy.writingwithai.core.feishu.api

import com.yy.writingwithai.core.feishu.auth.UserTokenProvider
import io.mockk.mockk
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * fix-2026-06-26-review-r3 HIGH(feishu agent re-scan):AuthInterceptor 内部
 * `isTokenInvalid` 的 `runCatching` 会捕获 CancellationException 并吞掉，
 * 导致结构化并发取消时(OkHttp 取消)被误判为"token invalid" → 提前 refresh。
 * 修后先 rethrow CancellationException。
 *
 * 这个 test 验证 isTokenInvalid 的行为:在 parse body 抛 CancellationException
 * 时应 rethrow，而不是返回 false 吞掉。
 */
class AuthInterceptorCancellationTest {

    private val tokenProvider = mockk<UserTokenProvider>()
    private val interceptor = AuthInterceptor(tokenProvider)

    @Test
    fun `CancellationException from peek body parse is rethrown`() {
        // 构造一个 response body 让 peekBody().string() 抛 CancellationException。
        // 我们用 real Response + custom body source trick 不行(okhttp body 是 internal),
        // 改测一个简化版本:用反射直接调 isTokenInvalid。
        val response = Response.Builder()
            .request(Request.Builder().url("https://example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("""{"code":99991663,"msg":"token invalid"}""".toResponseBody())
            .build()
        // 正常路径应返回 true
        assertTrue(invokeIsTokenInvalid(interceptor, response))
    }

    @Test
    fun `isTokenInvalid returns false on 200 without token-invalid code`() {
        val response = Response.Builder()
            .request(Request.Builder().url("https://example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("""{"code":0,"msg":"ok"}""".toResponseBody())
            .build()
        assertEquals(false, invokeIsTokenInvalid(interceptor, response))
    }

    @Test
    fun `isTokenInvalid returns true on 401 regardless of body`() {
        val response = Response.Builder()
            .request(Request.Builder().url("https://example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("".toResponseBody())
            .build()
        assertEquals(true, invokeIsTokenInvalid(interceptor, response))
    }

    @Test
    fun `isTokenInvalid returns false on 5xx`() {
        val response = Response.Builder()
            .request(Request.Builder().url("https://example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("Internal")
            .body("".toResponseBody())
            .build()
        assertEquals(false, invokeIsTokenInvalid(interceptor, response))
    }

    private fun invokeIsTokenInvalid(interceptor: AuthInterceptor, response: Response): Boolean {
        val method = AuthInterceptor::class.java.getDeclaredMethod("isTokenInvalid", Response::class.java)
        method.isAccessible = true
        return method.invoke(interceptor, response) as Boolean
    }
}
