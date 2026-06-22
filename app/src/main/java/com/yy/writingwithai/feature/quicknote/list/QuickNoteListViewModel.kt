package com.yy.writingwithai.feature.quicknote.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.core.feishu.sync.FeishuRefEntity
import com.yy.writingwithai.core.feishu.sync.FeishuSyncService
import com.yy.writingwithai.feature.quicknote.model.NoteListUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class QuickNoteListViewModel
@Inject
constructor(
    private val repository: NoteRepository,
    private val feishuSyncService: FeishuSyncService
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedTag = MutableStateFlow<String?>(null)

    val uiState: StateFlow<NoteListUiState> =
        combine(
            combine(query, selectedTag) { q, tag -> q to tag }
                // M3 修:`observeAllTags` 提到外层,避免 selectedTag 变化触发 inner combine 重启
                // 导致 allTags 首次 emit [] 引起列表闪烁。
                .flatMapLatest { (q, tag) ->
                    repository.observeNotesWithTags(q, tag)
                        .map { notes -> Triple(notes, q, tag) }
                },
            repository.observeAllTags()
        ) { (notes, q, tag), allTags ->
            if (notes.isEmpty()) {
                NoteListUiState.Empty(query = q, selectedTag = tag)
            } else {
                NoteListUiState.Content(
                    notes = notes,
                    allTags = allTags,
                    query = q,
                    selectedTag = tag
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = NoteListUiState.Loading
        )

    fun setQuery(q: String) {
        query.update { q }
    }

    fun selectTag(tag: String?) {
        selectedTag.update { tag }
    }

    private val _feishuRefs = MutableStateFlow<Map<String, FeishuRefEntity>>(emptyMap())
    val feishuRefs: StateFlow<Map<String, FeishuRefEntity>> = _feishuRefs.asStateFlow()

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                if (state is NoteListUiState.Content && state.notes.isNotEmpty()) {
                    val ids = state.notes.map { it.note.id }
                    _feishuRefs.value = feishuSyncService.getRefsForNotes(ids)
                } else {
                    _feishuRefs.value = emptyMap()
                }
            }
        }
    }
}
