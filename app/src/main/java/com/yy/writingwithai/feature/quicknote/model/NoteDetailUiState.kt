package com.yy.writingwithai.feature.quicknote.model

import com.yy.writingwithai.core.data.model.NoteWithTags

/** 详情屏 UI 状态。 */
sealed interface NoteDetailUiState {
    data object Loading : NoteDetailUiState

    data object NotFound : NoteDetailUiState

    data class Content(
        val note: NoteWithTags,
        val wordCount: Int,
        val readMinutes: Int
    ) : NoteDetailUiState
}
