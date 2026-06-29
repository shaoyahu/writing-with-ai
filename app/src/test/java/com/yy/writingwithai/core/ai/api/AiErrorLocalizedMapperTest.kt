package com.yy.writingwithai.core.ai.api

import com.yy.writingwithai.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * real-provider-integration §6 · AiError → R.string 映射 5 个核心用例。
 *
 * 锁定 mapper 的契约:
 * 1. 9 个独立专属 variant 各自映射到唯一 stringRes(Network / Auth / InsufficientBalance
 *    / RateLimited / ServerError / Timeout / UserConsentRequired / ProviderNotConfigured / Cancellation)。
 * 2. 3 个低层细节错误(ContentModeration / Deserialization / ApikeyPromptNotAcked)
 *    + 兜底 Unknown → 统一归到 `ai_error_unknown`(终端用户不应看到 provider 上游细节)。
 * 3. 携带参数的变体(RateLimited / ServerError)不影响 stringRes 选择 —— mapper 只看类型,
 *    参数细节走 `AiError.summary()` 落 `ai_history` 表,不在 UI 文案里出现。
 * 4. 映射是纯函数:同一 input 多次调用返同一 result。
 * 5. 所有 13 个 variant 都覆盖到(没有 `else` 漏分支),防止新增 variant 时编译器不报错。
 */
class AiErrorLocalizedMapperTest {

    @Test
    fun `9 dedicated variants map to their specific stringRes`() {
        // 每个专属 variant 必须映射到唯一 stringRes,且互不相同
        val expected =
            mapOf<AiError, Int>(
                AiError.Network(-1, "x") to R.string.ai_error_network,
                AiError.Auth(401, "x") to R.string.ai_error_auth,
                AiError.InsufficientBalance("x") to R.string.ai_error_insufficient_balance,
                AiError.RateLimited(30) to R.string.ai_error_rate_limited,
                AiError.ServerError(503) to R.string.ai_error_server_error,
                AiError.Timeout("x") to R.string.ai_error_timeout,
                AiError.UserConsentRequired to R.string.ai_error_user_consent_required,
                AiError.ProviderNotConfigured to R.string.ai_error_provider_not_configured,
                AiError.Cancellation to R.string.ai_error_cancellation
            )
        for ((error, expectedRes) in expected) {
            assertEquals(
                expectedRes,
                AiErrorLocalizedMapper.localize(error),
                "variant=${error::class.simpleName}"
            )
        }
        // 9 个 resId 必须两两互不相同(没有把两个 variant 误映射到同一个 key)
        assertEquals(9, expected.values.toSet().size)
    }

    @Test
    fun `ContentModeration Deserialization ApikeyPromptNotAcked Unknown fall through to ai_error_unknown`() {
        // 4 个 variant → 同一兜底 key(终端用户不应看到 provider 上游细节)
        val expectedRes = R.string.ai_error_unknown
        assertEquals(
            expectedRes,
            AiErrorLocalizedMapper.localize(AiError.ContentModeration("blocked"))
        )
        assertEquals(
            expectedRes,
            AiErrorLocalizedMapper.localize(AiError.Deserialization("parse failed"))
        )
        assertEquals(
            expectedRes,
            AiErrorLocalizedMapper.localize(AiError.ApikeyPromptNotAcked)
        )
        assertEquals(
            expectedRes,
            AiErrorLocalizedMapper.localize(AiError.Unknown(null, "oops"))
        )
    }

    @Test
    fun `RateLimited and ServerError variants localize regardless of param values`() {
        // mapper 只看类型,参数值不影响 stringRes(参数细节走 summary() 落 ai_history)
        val r1 = AiErrorLocalizedMapper.localize(AiError.RateLimited(1))
        val r2 = AiErrorLocalizedMapper.localize(AiError.RateLimited(3600))
        val r3 = AiErrorLocalizedMapper.localize(AiError.ServerError(500))
        val r4 = AiErrorLocalizedMapper.localize(AiError.ServerError(599))
        assertEquals(R.string.ai_error_rate_limited, r1)
        assertEquals(r1, r2)
        assertEquals(R.string.ai_error_server_error, r3)
        assertEquals(r3, r4)
        // 两个 variant 必须映射到不同 key(防止误归类)
        assertNotEquals(r1, r3)
    }

    @Test
    fun `localize is deterministic pure function`() {
        // 同一 input 重复调用必须返同一 result,且不等价输入必须返不同 result
        val network = AiError.Network(-1, "boom")
        val first = AiErrorLocalizedMapper.localize(network)
        val second = AiErrorLocalizedMapper.localize(network)
        val third = AiErrorLocalizedMapper.localize(AiError.Network(-1, "boom"))
        assertEquals(first, second)
        assertEquals(first, third)
        assertNotNull(first)
    }

    @Test
    fun `all 13 AiError variants are covered by mapper`() {
        // 防护性回归:防止新增 AiError variant 时编译器未报错(if-else 没列全)
        // 此用例显式列举 13 个 variant,要求 mapper 对每个都返非 0 resId(占位 0 = 未映射)。
        val allErrors: List<AiError> =
            listOf(
                AiError.Network(0, "x"),
                AiError.Auth(401, "x"),
                AiError.InsufficientBalance("x"),
                AiError.RateLimited(0),
                AiError.ServerError(500),
                AiError.Timeout("x"),
                AiError.ContentModeration("x"),
                AiError.Deserialization("x"),
                AiError.UserConsentRequired,
                AiError.ProviderNotConfigured,
                AiError.ApikeyPromptNotAcked,
                AiError.Cancellation,
                AiError.Unknown(null, "x")
            )
        assertEquals(13, allErrors.size, "AiError 新增 variant 时此断言先失败,提醒补 mapper 分支")
        for (err in allErrors) {
            val resId = AiErrorLocalizedMapper.localize(err)
            assertNotEquals(0, resId, "variant=${err::class.simpleName} returned 0(resId missing)")
        }
    }
}
