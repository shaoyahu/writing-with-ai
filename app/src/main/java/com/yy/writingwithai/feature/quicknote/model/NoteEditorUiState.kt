package com.yy.writingwithai.feature.quicknote.model

/** 编辑屏 UI 状态。 */
data class NoteEditorUiState(
    val isNew: Boolean,
    val isLoaded: Boolean = false,
    val title: String = "",
    val content: String = "",
    val tags: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val tagInputText: String = ""
)
