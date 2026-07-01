package com.yy.writingwithai.feature.aiwriting.error

import com.yy.writingwithai.R
import com.yy.writingwithai.core.ai.api.AiError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 纯 JVM 单测:断言 `toDisplayMessageRes()` 把每种 [AiError] 映射到正确的 string 资源 ID。
 *
 * 不再走 Robolectric + `Context.getString()`，因为 `testOptions.unitTests.isReturnDefaultValues = true`
 * 会让 `Resources.getString(R.string.X)` 返回 `null`，无法解析真实文案。改成断言资源 ID 之后
 * 测试既能跑通，又不需要任何 Android shadow / RuntimeEnvironment。
 */
class AiErrorDisplayTest {
    @Test
    fun network_maps_to_network_string_res() {
        assertEquals(
            R.string.aiwriting_error_network,
            AiError.Network(code = 500, detail = "timeout").toDisplayMessageRes()
        )
    }

    @Test
    fun network_401_maps_to_network_string_res() {
        // 401 通常归 `Auth`，但若 provider 错误地把它当网络层失败 → 至少 Network 路径
        // 走的是 `aiwriting_error_network`，验证"网络层失败"兜底文案。
        assertEquals(
            R.string.aiwriting_error_network,
            AiError.Network(code = 401, detail = "").toDisplayMessageRes()
        )
    }

    @Test
    fun auth_maps_to_auth_string_res() {
        assertEquals(
            R.string.aiwriting_error_auth,
            AiError.Auth(code = 401, detail = "bad key").toDisplayMessageRes()
        )
    }

    @Test
    fun insufficient_balance_maps_to_balance_string_res() {
        assertEquals(
            R.string.aiwriting_error_balance,
            AiError.InsufficientBalance(detail = "no money").toDisplayMessageRes()
        )
    }

    @Test
    fun timeout_maps_to_timeout_string_res() {
        assertEquals(
            R.string.aiwriting_error_timeout,
            AiError.Timeout(message = "30s").toDisplayMessageRes()
        )
    }

    @Test
    fun unknown_maps_to_unknown_string_res() {
        assertEquals(
            R.string.aiwriting_error_unknown,
            AiError.Unknown(code = null, detail = "weird").toDisplayMessageRes()
        )
    }

    @Test
    fun deserialization_maps_to_unknown_string_res() {
        assertEquals(
            R.string.aiwriting_error_unknown,
            AiError.Deserialization(message = "bad json").toDisplayMessageRes()
        )
    }

    @Test
    fun content_moderation_maps_to_unknown_string_res() {
        assertEquals(
            R.string.aiwriting_error_unknown,
            AiError.ContentModeration(detail = "blocked").toDisplayMessageRes()
        )
    }

    @Test
    fun user_consent_required_maps_to_onboarding_required_res() {
        assertEquals(
            R.string.onboarding_required,
            AiError.UserConsentRequired.toDisplayMessageRes()
        )
    }

    @Test
    fun provider_not_configured_maps_to_provider_not_configured_res() {
        assertEquals(
            R.string.ai_provider_not_configured,
            AiError.ProviderNotConfigured.toDisplayMessageRes()
        )
    }

    @Test
    fun apikey_prompt_not_acked_maps_to_onboarding_required_res() {
        assertEquals(
            R.string.onboarding_required,
            AiError.ApikeyPromptNotAcked.toDisplayMessageRes()
        )
    }

    @Test
    fun all_variants_map_to_known_res_ids() {
        // smoke:全部 AiError 子类型都能映射到 `R.string` 已存在的 ID(非 0 / 非负数)。
        val cases: List<AiError> = listOf(
            AiError.Network(code = 500, detail = ""),
            AiError.Auth(code = 401, detail = ""),
            AiError.InsufficientBalance(detail = ""),
            AiError.Timeout(message = ""),
            AiError.ContentModeration(detail = ""),
            AiError.Deserialization(message = ""),
            AiError.Unknown(code = null, detail = ""),
            AiError.UserConsentRequired,
            AiError.ProviderNotConfigured,
            AiError.ApikeyPromptNotAcked
        )
        cases.forEach { err ->
            val resId = err.toDisplayMessageRes()
            assert(resId > 0) { "$err 映射到非法资源 ID:$resId" }
        }
    }
}
