package com.yy.writingwithai.feature.settings.prompt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.ai.prompt.DefaultPrompts
import com.yy.writingwithai.core.prefs.PromptTemplateStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * fix-ai-config-ux · PromptTemplate 编辑屏 ViewModel。
 *
 * 改动:
 * - init 默认填 DefaultPrompts.forOp(op)(store 仍空,只 drafts 层先填)
 * - onPromptChange 只改 drafts + pendingSave(不立即写 store)
 * - 新增 save(op) 显式写 store + 清 dirty
 * - resetToDefault 写 store + 清 drafts + 清 dirty
 *
 * spec: openspec/changes/fix-ai-config-ux/specs/custom-prompt-template/spec.md
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
            ),
        val pendingSave: Set<WritingOp> = emptySet()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // fix-ai-config-ux: store 空时默认填 DefaultPrompts,不改 store
        viewModelScope.launch {
            val expand = promptTemplateStore.getForOp(WritingOp.EXPAND)
                ?: DefaultPrompts.forOp(WritingOp.EXPAND)
            val polish = promptTemplateStore.getForOp(WritingOp.POLISH)
                ?: DefaultPrompts.forOp(WritingOp.POLISH)
            val organize = promptTemplateStore.getForOp(WritingOp.ORGANIZE)
                ?: DefaultPrompts.forOp(WritingOp.ORGANIZE)
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

    /** 改草稿 + 标 pendingSave,不立即写 store。fix-ai-config-ux: 废除旧"自动保存"行为。 */
    fun onPromptChange(op: WritingOp, value: String) {
        _uiState.update {
            it.copy(
                drafts = it.drafts.toMutableMap().apply { put(op, value) },
                pendingSave = it.pendingSave + op
            )
        }
    }

    fun onTabSwitch(op: WritingOp) {
        _uiState.update { it.copy(currentOp = op) }
    }

    /** 显式保存:写 store + 清 dirty。 */
    fun save(op: WritingOp) {
        val draft = _uiState.value.drafts[op] ?: return
        viewModelScope.launch {
            promptTemplateStore.setForOp(op, draft)
        }
        _uiState.update {
            it.copy(pendingSave = it.pendingSave - op)
        }
    }

    /** 恢复默认:写 store 空字符串 + 清 drafts + 清 dirty(下次 init 重填默认)。 */
    fun resetToDefault(op: WritingOp) {
        viewModelScope.launch {
            promptTemplateStore.resetToDefault(op)
        }
        _uiState.update {
            it.copy(
                drafts = it.drafts.toMutableMap().apply { put(op, "") },
                pendingSave = it.pendingSave - op
            )
        }
    }
}
