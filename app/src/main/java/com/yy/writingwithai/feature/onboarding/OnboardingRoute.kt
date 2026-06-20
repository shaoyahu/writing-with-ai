package com.yy.writingwithai.feature.onboarding

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel

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
 */
@Composable
fun OnboardingRoute(onExitApp: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val scrolledToBottom by viewModel.scrolledToBottom.collectAsState()
    val action by viewModel.action.collectAsState()

    // 拒绝 → 退出 App。
    val context = LocalContext.current
    LaunchedEffect(action) {
        when (action) {
            OnboardingViewModel.Action.ExitApp -> {
                viewModel.consumeAction()
                (context as? Activity)?.finishAffinity()
                onExitApp()
            }
            null -> Unit
        }
    }

    OnboardingScreen(
        scrolledToBottom = scrolledToBottom,
        onScrolledToBottomChange = viewModel::setScrolledToBottom,
        onAccept = viewModel::accept,
        onReject = viewModel::reject
    )
}
