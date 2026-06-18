package com.yy.writingwithai.feature.quicknote.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.feature.quicknote.model.NoteDetailUiState
import com.yy.writingwithai.feature.quicknote.model.ReadingTime
import com.yy.writingwithai.feature.quicknote.model.WordCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class QuickNoteDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: NoteRepository,
    ) : ViewModel() {
        // H3 修:`requireNotNull` 在 process-death / 深链 / saved state 跨版本场景会 IAE crash。
        // 改为可空,缺失时直接进入 NotFound,避免进程崩溃。
        private val noteId: String? = savedStateHandle.get<String>("id")

        val uiState: StateFlow<NoteDetailUiState> =
            if (noteId.isNullOrBlank()) {
                MutableStateFlow(NoteDetailUiState.NotFound).asStateFlow()
            } else {
                repository.observeNoteWithTags(noteId)
                    .map { item ->
                        if (item == null) {
                            NoteDetailUiState.NotFound
                        } else {
                            NoteDetailUiState.Content(
                                note = item,
                                wordCount = WordCount.of(item.note.content),
                                readMinutes = ReadingTime.minutesOf(item.note.content),
                            )
                        }
                    }
                    .stateIn(
                        scope = viewModelScope,
                        started = SharingStarted.WhileSubscribed(5_000L),
                        initialValue = NoteDetailUiState.Loading,
                    )
            }

        fun delete(onDeleted: () -> Unit) {
            val id = noteId ?: return
            viewModelScope.launch {
                // M6 修:用户点确认后立刻 back 退出 detail → viewModelScope 被取消,
                // 删除协程中断,笔记没被删。包 NonCancellable 强制执行。
                withContext(kotlinx.coroutines.NonCancellable) {
                    repository.delete(id)
                }
                onDeleted()
            }
        }

        fun togglePinned() {
            val current = (uiState.value as? NoteDetailUiState.Content)?.note ?: return
            viewModelScope.launch {
                repository.setPinned(current.note.id, !current.note.isPinned)
            }
        }
    }
