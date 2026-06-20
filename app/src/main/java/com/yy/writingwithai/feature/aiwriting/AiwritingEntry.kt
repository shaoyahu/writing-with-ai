package com.yy.writingwithai.feature.aiwriting

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextRange
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.yy.writingwithai.feature.aiwriting.streaming.AiActionViewModel
import com.yy.writingwithai.feature.onboarding.OnboardingEntry

/**
 * `feature/aiwriting` 跨 feature 入口。
 *
 * 详情屏 **仅** import 此 object,不允许 import 内部 file(参见 ai-actions spec
 * "package layout follows feature self-containment" + quick-note spec "Detail
 * screen integrates ai-actions via feature Entry")。
 */
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
}

/**
 * 详情屏 FAB 状态投影:selectionEmpty = true → Share FAB;false → AutoAwesome FAB。
 *
 * 由详情屏 Composable 根据 `TextFieldValue.selection` 是否空来计算,避免 detail 屏
 * import 内部 `ActionSelectionViewModel`。
 */
data class AiActionFabState(
    val selectionEmpty: Boolean
) {
    companion object {
        val DEFAULT = AiActionFabState(selectionEmpty = true)

        fun fromSelection(selection: TextRange): AiActionFabState =
            AiActionFabState(selectionEmpty = selection.collapsed || selection.length == 0)
    }
}
