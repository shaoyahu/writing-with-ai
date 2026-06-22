package com.yy.writingwithai.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    val action by viewModel.action.collectAsState()

    LaunchedEffect(action) {
        if (action == ApikeyPromptViewModel.Action.Finished) {
            viewModel.consumeAction()
            onFinished()
        }
    }

    ApikeyPromptScreen(
        onAck = viewModel::onAck,
        onSkip = viewModel::onSkip
    )
}
