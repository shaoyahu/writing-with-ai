package com.yy.writingwithai.feature.aiwriting.action

import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.ui.AiActionFabState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 详情屏选区状态 ViewModel(M3)。
 *
 * 持有 `MutableStateFlow<TextRange>`,由 detail 屏 BasicTextField 的 `onValueChange`
 * 调 [onSelectionChange] 推入;`fabState` 是 `selection` 的派生,投影为
 * [AiActionFabState] 给 detail 屏决定渲染 Share / AutoAwesome FAB。
 *
 * 选区状态由 ViewModel 持有,跨重组 / config change / 进 background 不丢(参见
 * quick-note spec "选中文本持久到 ViewModel")。
 */
@HiltViewModel
class ActionSelectionViewModel
@Inject
constructor() : ViewModel() {
    private val _selection = MutableStateFlow(TextRange.Zero)
    val selection: StateFlow<TextRange> = _selection.asStateFlow()

    val fabState: StateFlow<AiActionFabState> =
        _selection
            .map { AiActionFabState.fromSelection(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = AiActionFabState.DEFAULT
            )

    fun onSelectionChange(range: TextRange) {
        _selection.value = range
    }
}
