package com.yy.writingwithai.feature.onboarding

import androidx.navigation.NavController

/**
 * M4-4 onboarding-consent · 跨 feature 入口。
 *
 * spec: openspec/changes/onboarding-consent/specs/onboarding-consent/spec.md
 * "OnboardingScreen shown on first launch" + ai-actions spec
 * "AiActionViewModel gates AI calls behind user consent" 引用。
 *
 * 引用方只允许 `requestConsent(navController)`,不进 OnboardingRoute 内部。
 *
 * onboarding-apikey-prompt 扩展:增加 `ROUTE_APIKEY_PROMPT` 路由常量,AppNav
 * 在 consent 通过后 + apikey ack=false 时 navigate 此路由。
 */
object OnboardingEntry {
    /** 同意页路由字符串常量,AppNav / MainActivity / AiwritingEntry 共用。 */
    const val ROUTE_CONSENT: String = "onboarding/consent"

    /** Apikey 教育页路由字符串常量,AppNav 统一引用。 */
    const val ROUTE_APIKEY_PROMPT: String = "onboarding/apikey-prompt"

    /** 跳到同意页(同意完成后由 AppNav `LaunchedEffect(consentFlow)` 自动回主路由)。 */
    fun requestConsent(navController: NavController) {
        navController.navigate(ROUTE_CONSENT)
    }

    /** 跳到 Apikey 教育页(ack 完成后由 Route 的 `onFinished()` 回主路由)。 */
    fun requestApikeyPrompt(navController: NavController) {
        navController.navigate(ROUTE_APIKEY_PROMPT)
    }
}
