package com.yy.writingwithai.feature.onboarding

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * M4-4 OnboardingRoute — 同意门入口。
 *
 * 同意流程:
 * 1. UI 渲染 [OnboardingScreen];用户滚动到底 + 点"同意"
 * 2. `OnboardingViewModel.accept()` 写 ConsentStore
 * 3. AppNav 的 `LaunchedEffect(consentState)` 监听到 `accepted && version >= CURRENT`
 *    → navigate 主路由(单向门) + 处理 widgetPendingRoute 回放
 *
 * 拒绝:`OnboardingViewModel.reject()` → Action.ExitApp → 调 Activity.finishAffinity()
 *
 * r1 L1 修:删 `consentStore` 死形参(navigate 由 AppNav 统一负责,本 Route 不再
 * collectAsState)。
 *
 * fix-2026-06-30-full-review-r1 MEDIUM M11:全部改 collectAsStateWithLifecycle,
 * app 后台时不再继续 collect,LaunchedEffect 也不会在 Stopped 状态触发。
 *
 * L3:action 改 SharedFlow,LaunchedEffect 用 collect 收一次性事件。
 */
@Composable
fun OnboardingRoute(onExitApp: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val scrolledToBottom by viewModel.scrolledToBottom.collectAsStateWithLifecycle()

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.action.collect { action ->
            when (action) {
                OnboardingViewModel.Action.ExitApp -> {
                    (context as? Activity)?.finishAffinity()
                    onExitApp()
                }
                null -> { /* StateFlow 初始 / consumeAction 后的 null,跳过 */ }
            }
        }
    }

    OnboardingScreen(
        scrolledToBottom = scrolledToBottom,
        onScrolledToBottomChange = viewModel::setScrolledToBottom,
        onAccept = viewModel::accept,
        onReject = viewModel::reject
    )
}
