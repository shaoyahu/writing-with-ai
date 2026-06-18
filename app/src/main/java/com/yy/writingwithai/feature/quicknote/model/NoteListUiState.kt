package com.yy.writingwithai.feature.quicknote.model

import com.yy.writingwithai.core.data.model.NoteWithTags

/**
 * 列表屏 UI 状态(spec §"List ordering" + "Search" + "Tag many-to-many")。
 */
sealed interface NoteListUiState {
    val query: String
    val selectedTag: String?

    data object Loading : NoteListUiState {
        override val query: String = ""
        override val selectedTag: String? = null
    }

    data class Empty(
        override val query: String,
        override val selectedTag: String?,
    ) : NoteListUiState

    data class Content(
        val notes: List<NoteWithTags>,
        val allTags: List<String>,
        override val query: String,
        override val selectedTag: String?,
    ) : NoteListUiState
}
