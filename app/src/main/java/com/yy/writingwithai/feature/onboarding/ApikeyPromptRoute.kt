package com.yy.writingwithai.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * onboarding-apikey-prompt · ApikeyPrompt Route(Wire VM → Screen)。
 *
 * 监听 `viewModel.action`,`Finished` 时调外层 `onFinished()` 让 AppNav
 * navigate 出 apikey-prompt 路由。
 *
 * spec: openspec/changes/onboarding-apikey-prompt/specs/onboarding-consent/spec.md
 * "Apikey prompt screen shown after consent"
 */
@Composable
fun ApikeyPromptRoute(onFinished: () -> Unit, viewModel: ApikeyPromptViewModel = hiltViewModel()) {
    // fix-2026-06-30-full-review-r1 LOW L3:action 改 SharedFlow,
    // LaunchedEffect(Unit) + collect 替代 collectAsState + LaunchedEffect(action)。
    LaunchedEffect(Unit) {
        viewModel.action.collect { action ->
            when (action) {
                ApikeyPromptViewModel.Action.Finished -> onFinished()
                ApikeyPromptViewModel.Action.Reset -> { /* no navigation needed */ }
            }
        }
    }

    ApikeyPromptScreen(
        onAck = viewModel::onAck,
        onSkip = viewModel::onSkip
    )
}
