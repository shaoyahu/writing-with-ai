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
 */
object OnboardingEntry {
    /** 同意页路由字符串常量,AppNav / MainActivity / AiwritingEntry 共用。 */
    const val ROUTE_CONSENT: String = "onboarding/consent"

    /** 跳到同意页(同意完成后由 AppNav `LaunchedEffect(consentFlow)` 自动回主路由)。 */
    fun requestConsent(navController: NavController) {
        navController.navigate(ROUTE_CONSENT)
    }
}
