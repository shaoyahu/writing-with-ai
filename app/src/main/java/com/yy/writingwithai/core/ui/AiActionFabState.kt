package com.yy.writingwithai.core.ui

import androidx.compose.ui.text.TextRange

/**
 * 详情屏 FAB 状态投影:selectionEmpty = true → Share FAB;false → AutoAwesome FAB。
 *
 * polish-review-r2 M9:从 `feature/aiwriting/AiwritingEntry.kt` 底部挪到 `core/ui/`。
 *
 * 该类型是 `Selection → FAB enum` 纯投影，无 aiwriting 业务逻辑;`feature/quicknote` 与
 * `feature/aiwriting` 都引用，放 `core/ui/` 是恰当的跨 feature 共享层。
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
