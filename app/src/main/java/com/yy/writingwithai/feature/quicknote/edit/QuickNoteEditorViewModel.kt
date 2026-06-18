package com.yy.writingwithai.feature.quicknote.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.feature.quicknote.model.NoteEditorUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class QuickNoteEditorViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: NoteRepository,
    ) : ViewModel() {
        private val routeId: String? = savedStateHandle.get<String>("id")
        private val isNew: Boolean = routeId.isNullOrBlank() || routeId == NEW_SENTINEL
        private val noteId: String = if (isNew) UUID.randomUUID().toString() else routeId!!

        private val titleFlow = MutableStateFlow("")
        private val contentFlow = MutableStateFlow("")
        private val tagsFlow = MutableStateFlow<List<String>>(emptyList())
        private val savingFlow = MutableStateFlow(false)
        private val loadedFlow = MutableStateFlow(false)

        init {
            if (!isNew) {
                viewModelScope.launch {
                    // H2 修:记录用户是否已抢先输入;若是,init 不回填,避免覆盖。
                    val hadUserInput =
                        titleFlow.value.isNotEmpty() ||
                            contentFlow.value.isNotEmpty() ||
                            tagsFlow.value.isNotEmpty()
                    val existing = repository.getNote(noteId)
                    if (existing != null && !hadUserInput) {
                        titleFlow.value = existing.title
                        contentFlow.value = existing.content
                        // H1 修:用 .first() 一次性读 tags,不再持续订阅导致覆盖用户编辑。
                        val item = repository.observeNoteWithTags(existing.id).first()
                        if (item != null) tagsFlow.value = item.tags
                    }
                    loadedFlow.value = true
                }
            } else {
                loadedFlow.value = true
            }
        }

        val uiState: StateFlow<NoteEditorUiState> =
            combine(titleFlow, contentFlow, tagsFlow, savingFlow, loadedFlow) {
                    title, content, tags, saving, loaded ->
                NoteEditorUiState(
                    isNew = isNew,
                    title = title,
                    content = content,
                    tags = tags,
                    isSaving = saving,
                    isLoaded = loaded,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue =
                    NoteEditorUiState(
                        isNew = isNew,
                        title = "",
                        content = "",
                        tags = emptyList(),
                        isLoaded = false,
                    ),
            )

        fun setTitle(s: String) {
            titleFlow.update { s }
        }

        fun setContent(s: String) {
            contentFlow.update { s }
        }

        fun addTag(tag: String) {
            val trimmed = tag.trim()
            if (trimmed.isEmpty()) return
            tagsFlow.update { current -> if (trimmed in current) current else current + trimmed }
        }

        fun removeTag(tag: String) {
            tagsFlow.update { current -> current.filterNot { it == tag } }
        }

        fun save(onSaved: (id: String) -> Unit) {
            if (savingFlow.value) return
            savingFlow.update { true }
            viewModelScope.launch {
                val now = System.currentTimeMillis()
                val existing = if (isNew) null else repository.getNote(noteId)
                val note =
                    (
                        existing ?: Note(
                            id = noteId,
                            title = "",
                            content = "",
                            createdAt = now,
                            updatedAt = now,
                            isPinned = false,
                            lastAiOp = null,
                            lastAiAt = null,
                        )
                    ).copy(
                        title = titleFlow.value,
                        content = contentFlow.value,
                        updatedAt = now,
                    )
                repository.upsert(note, tagsFlow.value)
                savingFlow.update { false }
                onSaved(note.id)
            }
        }

        private companion object {
            const val NEW_SENTINEL = "NEW"
        }
    }
