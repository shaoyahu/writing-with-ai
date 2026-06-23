package com.yy.writingwithai.feature.aiwriting

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.yy.writingwithai.feature.aiwriting.action.ActionSheet
import com.yy.writingwithai.feature.aiwriting.streaming.StreamingPanel
import com.yy.writingwithai.feature.onboarding.OnboardingEntry

/**
 * `feature/aiwriting` 跨 feature 入口 + 顶层 typealias 暴露 sealed UiState / VM 类型。
 *
 * fix-review-r2-high H4:其他 feature(quicknote 等)**仅** import 此文件 + `AiwritingEntry` object,
 * 不直接 import 内部 streaming/action package,避免 layer 泄漏。
 *
 * Kotlin typealias 不能 nested(K1+K2 限制)→ 放顶层让 caller 用 `import com.yy.writingwithai.feature.aiwriting.AiActionUiState`
 * 直接拿 sealed UiState 类型,无须知悉 streaming 子包。
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
        // L4 修:`hiltViewModel` 默认就是 `LocalViewModelStoreOwner.current`,无需显式传。
        hiltViewModel<AiActionViewModel>()

    /**
     * M4-4:未同意时把详情屏 FAB 唤起改跳同意页(替代原弹 ActionSheet)。
     * 包装 [OnboardingEntry.requestConsent] 是为了保持 aiwriting 自包含
     * (只 import `OnboardingEntry`,不直接 import 内部)。
     */
    fun requestConsent(navController: NavController) {
        OnboardingEntry.requestConsent(navController)
    }

    /**
     * H4 新增:选区操作菜单 wrapper(扩写/润色/整理/复制)。
     * 内部转调 `feature.aiwriting.action.ActionSheet`,其他 feature 仅引用本入口。
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
     * H4 新增:AI 流式面板 wrapper(Idle 不渲染;Streaming / Done / Failed / Replaced 走 ModalBottomSheet)。
     * 内部转调 `feature.aiwriting.streaming.StreamingPanel`。
     */
    @Composable
    fun StreamingPanelRoute(
        state: AiActionUiState,
        onAccept: () -> Unit,
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
            state, onAccept, onReject, onCancel, onRegenerate, onClose,
            onDismiss, onUndo, onDismissReplace, onRetry, onNavigateToSettings
        )
    }

    /**
     * H4 新增:系统剪贴板 wrapper(走 ClipboardManager)。
     * 不走 AiGateway(非 AI 操作,系统 API)。
     */
    fun copyToClipboard(context: Context, text: String) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        manager.setPrimaryClip(ClipData.newPlainText("writing-with-ai", text))
    }
}

// M9 修:[AiActionFabState] 挪到 `core/ui/`,这里不再定义(由 caller 直接 import `core.ui.AiActionFabState`)。
