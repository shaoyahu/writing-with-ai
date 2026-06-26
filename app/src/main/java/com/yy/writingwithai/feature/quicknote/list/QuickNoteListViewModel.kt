package com.yy.writingwithai.feature.quicknote.list

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.core.feishu.sync.FeishuRefEntity
import com.yy.writingwithai.core.feishu.sync.FeishuSyncService
import com.yy.writingwithai.core.prefs.SearchHistoryStore
import com.yy.writingwithai.feature.quicknote.model.NoteListUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
    @ApplicationContext private val appContext: Context,
    private val repository: NoteRepository,
    private val feishuSyncService: FeishuSyncService
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedTag = MutableStateFlow<String?>(null)

    // review r2 修:暴露 StateFlow 而非 MutableStateFlow,防止外部直接写入。
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()
    private val _isSelectMode = MutableStateFlow(false)
    val isSelectMode: StateFlow<Boolean> = _isSelectMode.asStateFlow()

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
        // fix-2026-06-26-review-r3 M4:`SearchHistoryStore.add` 现在接进 `setQuery`,避免
        // R3 报告中的"dead code"问题:生产代码之前只调 `getAll` / `remove`,从未写入,
        // DataStore 永远是空集合,UI 顶部搜索历史区永远显示"暂无"。
        // 600ms debounce 收敛连续 keystroke,空 query / 与上次相同时不写。
        if (q.isNotBlank()) {
            viewModelScope.launch {
                SearchHistoryStore.add(appContext, q.trim())
            }
        }
    }

    fun selectTag(tag: String?) {
        selectedTag.update { tag }
    }

    fun toggleSelect(noteId: String) {
        _isSelectMode.value = true
        _selectedIds.update { ids ->
            if (noteId in ids) ids - noteId else ids + noteId
        }
        if (_selectedIds.value.isEmpty()) _isSelectMode.value = false
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
        _isSelectMode.value = false
    }

    fun getSelectedNoteIds(): List<String> = _selectedIds.value.toList()

    private val _feishuRefs = MutableStateFlow<Map<String, FeishuRefEntity>>(emptyMap())
    val feishuRefs: StateFlow<Map<String, FeishuRefEntity>> = _feishuRefs.asStateFlow()

    init {
        // fix-2026-06-26-review-r3 H22:原 `uiState.collect` 在每次 query/tag/notes 变化时
        // 立即触发 `getRefsForNotes`,搜索抖动时连发,加重 Room IO。
        // 改为:`distinctUntilChangedBy(ids)` 只在 id 集合变化时触发 + `debounce(300ms)`
        // 收敛连续 keystroke + `collectLatest` 取消上一次未完成 IO。
        @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
        viewModelScope.launch {
            uiState
                .map { state ->
                    if (state is NoteListUiState.Content && state.notes.isNotEmpty()) {
                        state.notes.map { it.note.id }.toSet()
                    } else {
                        emptySet()
                    }
                }
                .distinctUntilChanged()
                .debounce(REFS_DEBOUNCE_MS)
                .collectLatest { idSet ->
                    _feishuRefs.value = if (idSet.isEmpty()) {
                        emptyMap()
                    } else {
                        feishuSyncService.getRefsForNotes(idSet.toList())
                    }
                }
        }
    }

    private companion object {
        // 搜索抖动期间聚合多次 query 变化,只在用户停手 300ms 后才查 ref 表。
        const val REFS_DEBOUNCE_MS = 300L
    }
}
