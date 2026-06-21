package com.yy.writingwithai.feature.quicknote.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.feature.quicknote.model.NoteEditorUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class QuickNoteEditorViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NoteRepository
) : ViewModel() {
    private val routeId: String? = savedStateHandle.get<String>("id")
    private val isNew: Boolean = routeId.isNullOrBlank() || routeId == NEW_SENTINEL
    private val noteId: String = if (isNew) UUID.randomUUID().toString() else routeId!!

    private val titleFlow = MutableStateFlow("")
    private val contentFlow = MutableStateFlow("")
    private val tagsFlow = MutableStateFlow<List<String>>(emptyList())
    private val tagInputFlow = MutableStateFlow("")
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

    // combine 最多 5 个 Flow,6 个时嵌套一层
    private data class PartialState(
        val title: String,
        val content: String,
        val tags: List<String>,
        val tagInput: String,
        val saving: Boolean
    )

    val uiState: StateFlow<NoteEditorUiState> =
        combine(
            combine(titleFlow, contentFlow, tagsFlow, tagInputFlow, savingFlow) { t, c, ts, ti, s ->
                PartialState(t, c, ts, ti, s)
            },
            loadedFlow
        ) { p, loaded ->
            NoteEditorUiState(
                isNew = isNew,
                title = p.title,
                content = p.content,
                tags = p.tags,
                tagInputText = p.tagInput,
                isSaving = p.saving,
                isLoaded = loaded
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
                tagInputText = "",
                isLoaded = false
            )
        )

    fun setTitle(s: String) {
        titleFlow.update { s }
    }

    fun setContent(s: String) {
        contentFlow.update { s }
    }

    // fix-quicknote-tags-and-search · "已挂 #a #b" 副文案用
    val tagsSummary: StateFlow<String> =
        tagsFlow
            .map { list -> list.joinToString(" ") { "#$it" } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = ""
            )

    fun addTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return
        tagsFlow.update { current -> if (trimmed in current) current else current + trimmed }
    }

    fun removeTag(tag: String) {
        tagsFlow.update { current -> current.filterNot { it == tag } }
    }

    fun setTagInput(value: String) {
        tagInputFlow.update { value }
    }

    fun save(onSaved: (id: String) -> Unit) {
        if (savingFlow.value) return
        // 先消费 TagInputRow 中待提交的输入文本(用户未按逗号/回车就直接点保存的情况)
        val pendingTag = tagInputFlow.value.trim()
        if (pendingTag.isNotEmpty()) {
            addTag(pendingTag)
            tagInputFlow.update { "" }
        }
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
                        lastAiAt = null
                    )
                    ).copy(
                    title = titleFlow.value,
                    content = contentFlow.value,
                    updatedAt = now
                )
            val tagsToSave = tagsFlow.value
            android.util.Log.d("EditorVM", "save noteId=${note.id} tags=$tagsToSave")
            repository.upsert(note, tagsToSave)
            savingFlow.update { false }
            onSaved(note.id)
        }
    }

    private companion object {
        const val NEW_SENTINEL = "NEW"
    }
}
