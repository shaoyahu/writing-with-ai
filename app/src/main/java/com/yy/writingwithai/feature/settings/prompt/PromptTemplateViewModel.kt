package com.yy.writingwithai.feature.settings.prompt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.prefs.PromptTemplateStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * custom-prompt-template · PromptTemplate 编辑屏 ViewModel。
 *
 * spec: openspec/changes/custom-prompt-template/specs/custom-prompt-template/spec.md
 * "PromptTemplateScreen provides 3-tab edit UI"
 *
 * 状态机:
 * - `currentOp` 当前 Tab
 * - `drafts[op]` 3 op 各自草稿(草稿层,UI 写入路径)
 *
 * 保存策略:每次 `onPromptChange` 立即写(简化版;切 Tab 也立即写)。
 */
@HiltViewModel
class PromptTemplateViewModel
@Inject
constructor(
    private val promptTemplateStore: PromptTemplateStore
) : ViewModel() {
    data class UiState(
        val currentOp: WritingOp = WritingOp.EXPAND,
        val drafts: Map<WritingOp, String> =
            mapOf(
                WritingOp.EXPAND to "",
                WritingOp.POLISH to "",
                WritingOp.ORGANIZE to ""
            )
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // 初始化草稿:读 store 当前 3 op 值(空 → 走 fallback 默认)
        viewModelScope.launch {
            val expand = promptTemplateStore.getForOp(WritingOp.EXPAND) ?: ""
            val polish = promptTemplateStore.getForOp(WritingOp.POLISH) ?: ""
            val organize = promptTemplateStore.getForOp(WritingOp.ORGANIZE) ?: ""
            _uiState.update {
                it.copy(
                    drafts = mapOf(
                        WritingOp.EXPAND to expand,
                        WritingOp.POLISH to polish,
                        WritingOp.ORGANIZE to organize
                    )
                )
            }
        }
    }

    fun onPromptChange(op: WritingOp, value: String) {
        _uiState.update {
            it.copy(drafts = it.drafts.toMutableMap().apply { put(op, value) })
        }
        viewModelScope.launch {
            promptTemplateStore.setForOp(op, value)
        }
    }

    fun onTabSwitch(op: WritingOp) {
        _uiState.update { it.copy(currentOp = op) }
    }

    fun resetToDefault(op: WritingOp) {
        viewModelScope.launch {
            promptTemplateStore.resetToDefault(op)
        }
        _uiState.update {
            it.copy(drafts = it.drafts.toMutableMap().apply { put(op, "") })
        }
    }
}
