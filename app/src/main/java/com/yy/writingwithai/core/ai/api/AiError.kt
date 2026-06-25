package com.yy.writingwithai.core.ai.api

/** AI 调用失败原因(M2 定义:映射 HTTP status / IO 异常 / SSE 解析失败)。 */
sealed interface AiError {
    data class Network(val code: Int, val detail: String) : AiError

    data class Auth(val code: Int, val detail: String) : AiError

    data class InsufficientBalance(val detail: String) : AiError

    data class ContentModeration(val detail: String) : AiError

    data class Timeout(val message: String) : AiError

    data class Deserialization(val message: String) : AiError

    data class Unknown(val code: Int?, val detail: String) : AiError

    /**
     * M4-4 onboarding-consent:用户未同意隐私条款时阻断 AI 调用。
     * spec: openspec/changes/onboarding-consent/specs/ai-actions/spec.md
     * "AiActionViewModel gates AI calls behind user consent"
     */
    data object UserConsentRequired : AiError

    /**
     * M5 polish · provider-real-integration:用户在设置 → 模型管理未配置 provider
     * 或缺失 apikey。UI 区分 UserConsentRequired(跳同意页)vs ProviderNotConfigured
     * (toast"请先在设置 → 模型管理配置")。
     */
    data object ProviderNotConfigured : AiError

    /**
     * onboarding-apikey-prompt:用户尚未阅读/确认 apikey 教育页。
     *
     * spec: openspec/changes/onboarding-apikey-prompt/specs/onboarding-consent/spec.md
     * "AI capability guard on first use"
     */
    data object ApikeyPromptNotAcked : AiError

    /**
     * L5 修:provider 显式返 429(独立于 `Network`,UI 给"限流"专属文案 + 退避提示)。
     */
    data class RateLimited(val retryAfterSeconds: Int) : AiError

    /**
     * L5 修:provider 显式返 5xx(独立于 `Network`,UI 给"服务异常"专属文案)。
     */
    data class ServerError(val code: Int) : AiError

    /**
     * L5 修:用户主动取消(流式 chunk 中途点关闭 / 离开屏幕触发 viewModelScope cancel)。
     * 不算"错误"但仍走 `Failed` 态显示,文案用中性"已取消"避免惊扰。
     */
    data object Cancellation : AiError

    /** 供 AiHistory 落库用的单行摘要。 */
    fun summary(): String = when (this) {
        is Network -> "Network($code): $detail"
        is Auth -> "Auth($code): $detail"
        is InsufficientBalance -> "InsufficientBalance: $detail"
        is ContentModeration -> "ContentModeration: $detail"
        is Timeout -> "Timeout: $message"
        is Deserialization -> "Deserialization: $message"
        is Unknown -> "Unknown($code): $detail"
        is UserConsentRequired -> "UserConsentRequired"
        is ProviderNotConfigured -> "ProviderNotConfigured"
        is ApikeyPromptNotAcked -> "ApikeyPromptNotAcked"
        is RateLimited -> "RateLimited(retryAfter=${retryAfterSeconds}s)"
        is ServerError -> "ServerError($code)"
        is Cancellation -> "Cancellation"
    }
}
