package com.yy.writingwithai.feature.aiwriting.error

import androidx.annotation.StringRes
import com.yy.writingwithai.R
import com.yy.writingwithai.core.ai.api.AiError

/**
 * 把 M2 的 [AiError] 映射到 `R.string.aiwriting_error_*` 用户可见文案的资源 ID。
 *
 * - 返回 `@StringRes Int`，由调用方在持有 `Context` 的位置(如 Composable)用
 *   `context.getString(...)` 解析。这样映射逻辑可在纯 JVM 单测里直接断言资源 ID,
 *   不必走 Robolectric 模拟真 Context。
 * - 错误降级:UI 在 `Failed` 态必显示该文案 + "关闭" 按钮(ai-actions spec
 *   "Error fallback never leaves a blank UI")
 * - 兜底:`ContentModeration` / `Deserialization` / `Unknown` 全部走 `unknown`
 */
@StringRes
fun AiError.toDisplayMessageRes(): Int = when (this) {
    is AiError.Network -> R.string.aiwriting_error_network
    is AiError.Auth -> R.string.aiwriting_error_auth
    is AiError.InsufficientBalance -> R.string.aiwriting_error_balance
    is AiError.Timeout -> R.string.aiwriting_error_timeout
    is AiError.ContentModeration -> R.string.aiwriting_error_unknown
    is AiError.Deserialization -> R.string.aiwriting_error_unknown
    is AiError.Unknown -> R.string.aiwriting_error_unknown
    is AiError.UserConsentRequired -> R.string.onboarding_required
    is AiError.ProviderNotConfigured -> R.string.ai_provider_not_configured
    is AiError.ApikeyPromptNotAcked -> R.string.onboarding_required
    // R5 review 新增:限流 / 5xx / 取消走专属文案
    is AiError.RateLimited -> R.string.aiwriting_error_rate_limited
    is AiError.ServerError -> R.string.aiwriting_error_server
    is AiError.Cancellation -> R.string.aiwriting_error_cancelled
}
