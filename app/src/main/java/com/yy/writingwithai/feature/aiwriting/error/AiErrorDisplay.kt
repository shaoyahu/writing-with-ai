package com.yy.writingwithai.feature.aiwriting.error

import android.content.Context
import com.yy.writingwithai.R
import com.yy.writingwithai.core.ai.api.AiError

/**
 * 把 M2 的 [AiError] 映射到 `R.string.aiwriting_error_*` 用户可见文案。
 *
 * - 错误降级:UI 在 `Failed` 态必显示该文案 + "关闭" 按钮(ai-actions spec
 *   "Error fallback never leaves a blank UI")
 * - 兜底:`ContentModeration` / `Deserialization` / `Unknown` 全部走 `unknown`
 */
fun AiError.toDisplayMessage(context: Context): String = when (this) {
    is AiError.Network -> context.getString(R.string.aiwriting_error_network)
    is AiError.Auth -> context.getString(R.string.aiwriting_error_auth)
    is AiError.InsufficientBalance -> context.getString(R.string.aiwriting_error_balance)
    is AiError.Timeout -> context.getString(R.string.aiwriting_error_timeout)
    is AiError.ContentModeration -> context.getString(R.string.aiwriting_error_unknown)
    is AiError.Deserialization -> context.getString(R.string.aiwriting_error_unknown)
    is AiError.Unknown -> context.getString(R.string.aiwriting_error_unknown)
    is AiError.UserConsentRequired -> context.getString(R.string.onboarding_required)
    is AiError.ProviderNotConfigured -> context.getString(R.string.ai_provider_not_configured)
}
