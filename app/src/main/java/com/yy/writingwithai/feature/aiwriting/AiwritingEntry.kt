package com.yy.writingwithai.feature.aiwriting

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.yy.writingwithai.feature.aiwriting.action.ActionSheet
import com.yy.writingwithai.feature.aiwriting.streaming.StreamingPanel

/**
 * `feature/aiwriting` 跨 feature 入口 + 顶层 typealias 暴露 sealed UiState / VM 类型。
 *
 * fix-review-r2-high H4:其他 feature(quicknote 等)**仅** import 此文件 + `AiwritingEntry` object,
 * 不直接 import 内部 streaming/action package，避免 layer 泄漏。
 *
 * hardening-sse-and-widget-init H-2:不再 import `feature.onboarding.OnboardingEntry`。
 * `requestConsent(navController, requestConsent: (NavController) -> Unit)` 接受 lambda 参数，
 * 由 `app/AppNav.kt` 在编排时注入 `OnboardingEntry.requestConsent` 作为实现 —— `aiwriting`
 * feature 真正自包含(只暴露 contract，不依赖其他 feature 内部)。
 */
typealias AiActionUiState = com.yy.writingwithai.feature.aiwriting.streaming.AiActionUiState
typealias AiActionViewModel = com.yy.writingwithai.feature.aiwriting.streaming.AiActionViewModel

object AiwritingEntry {
    /**
     * 详情屏拿 AI 流式 ViewModel 的统一入口。
     *
     * 内部走 `hiltViewModel<AiActionViewModel>()`;`noteId` 透传给 ViewModel 用于
     * `acceptReplace` 写 Note。ViewModel scope 跟随 caller(默认是 NavBackStackEntry)。
     */
    @Composable
    fun rememberAiActionViewModel(noteId: String): AiActionViewModel =
        // L4 修:`hiltViewModel` 默认就是 `LocalViewModelStoreOwner.current`，无需显式传。
        hiltViewModel<AiActionViewModel>()

    /**
     * M4-4:未同意时把详情屏 FAB 唤起改跳同意页(替代原弹 ActionSheet)。
     *
     * hardening H-2:接受 `requestConsent: (NavController) -> Unit` lambda 参数，
     * 由 `app/AppNav.kt` 编排时注入具体实现(caller 持有 `OnboardingEntry` 引用),
     * `aiwriting` feature 不再 import `feature.onboarding`。
     */
    fun requestConsent(navController: NavController, requestConsent: (NavController) -> Unit) {
        requestConsent(navController)
    }

    /**
     * H4 新增:选区操作菜单 wrapper(扩写/润色/整理/复制)。
     * 内部转调 `feature.aiwriting.action.ActionSheet`，其他 feature 仅引用本入口。
     */
    @Composable
    fun ActionSheetRoute(
        expanded: Boolean,
        onDismiss: () -> Unit,
        onExpand: () -> Unit,
        onPolish: () -> Unit,
        onOrganize: () -> Unit,
        onSummarize: () -> Unit,
        onTranslate: () -> Unit,
        onCopy: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        ActionSheet(expanded, onDismiss, onExpand, onPolish, onOrganize, onSummarize, onTranslate, onCopy)
    }

    /**
     * H4 新增:AI 流式面板 wrapper(Idle 不渲染;Streaming / PartialDone / Done /
     * Failed / Replaced 走 ModalBottomSheet)。
     * 内部转调 `feature.aiwriting.streaming.StreamingPanel`。
     *
     * ai-regenerate-versions:`onAccept: (Int) -> Unit` 接收 position 参数(默认 0),
     * `onSelectVersion: (Int) -> Unit` 处理 tab 切换。
     */
    @Composable
    fun StreamingPanelRoute(
        state: AiActionUiState,
        onAccept: (Int) -> Unit,
        onSelectVersion: (Int) -> Unit,
        onReject: () -> Unit,
        onCancel: () -> Unit,
        onRegenerate: () -> Unit,
        onClose: () -> Unit,
        onDismiss: () -> Unit,
        onUndo: () -> Unit = {},
        onDismissReplace: () -> Unit = {},
        onRetry: () -> Unit = {},
        onNavigateToSettings: () -> Unit = {}
    ) {
        StreamingPanel(
            state, onAccept, onSelectVersion, onReject, onCancel, onRegenerate, onClose,
            onDismiss, onUndo, onDismissReplace, onRetry, onNavigateToSettings
        )
    }
}

// M9 修:[AiActionFabState] 挪到 `core/ui/`，这里不再定义(由 caller 直接 import `core.ui.AiActionFabState`)。
