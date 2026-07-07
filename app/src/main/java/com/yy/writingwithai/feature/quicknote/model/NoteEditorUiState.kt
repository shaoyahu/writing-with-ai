package com.yy.writingwithai.feature.quicknote.model

import androidx.compose.runtime.Immutable

/** 编辑屏 UI 状态。 */
@Immutable
data class NoteEditorUiState(
    val isNew: Boolean,
    val isLoaded: Boolean = false,
    val title: String = "",
    val content: String = "",
    val tags: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val tagInputText: String = ""
)
