package com.yy.writingwithai.core.ai.api

import androidx.annotation.StringRes
import com.yy.writingwithai.R

/**
 * real-provider-integration · AiError → 用户可读字符串资源。
 *
 * 单一职责:13 个 AiError 变体映射到 10 个 stringRes(ContentModeration / Deserialization /
 * ApikeyPromptNotAcked 三个细节错误合并到 [R.string.ai_error_unknown]，详情由
 * [AiError.summary] 落 `ai_history` 表，不直接展示给终端用户)。
 *
 * 调用方在 Composable 里 `stringResource(AiErrorLocalizedMapper.localize(error))` 取文案，
 * 不在 VM 里持有 Context / 字符串。
 */
object AiErrorLocalizedMapper {
    @StringRes
    fun localize(error: AiError): Int = when (error) {
        is AiError.Network -> R.string.ai_error_network
        is AiError.Auth -> R.string.ai_error_auth
        is AiError.InsufficientBalance -> R.string.ai_error_insufficient_balance
        is AiError.RateLimited -> R.string.ai_error_rate_limited
        is AiError.ServerError -> R.string.ai_error_server_error
        is AiError.Timeout -> R.string.ai_error_timeout
        is AiError.UserConsentRequired -> R.string.ai_error_user_consent_required
        is AiError.ProviderNotConfigured -> R.string.ai_error_provider_not_configured
        is AiError.Cancellation -> R.string.ai_error_cancellation
        is AiError.ContentModeration,
        is AiError.Deserialization,
        is AiError.ApikeyPromptNotAcked,
        is AiError.Unknown -> R.string.ai_error_unknown
    }
}
