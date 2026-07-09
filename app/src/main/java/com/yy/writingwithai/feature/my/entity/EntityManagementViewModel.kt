package com.yy.writingwithai.feature.my.entity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.data.db.dao.entity.EntityListRow
import com.yy.writingwithai.core.data.db.dao.entity.NoteEntityDao
import com.yy.writingwithai.core.note.entity.EntityType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
@HiltViewModel
class EntityManagementViewModel @Inject constructor(
    private val entityDao: NoteEntityDao
) : ViewModel() {

    enum class SortMode(val key: String) {
        NAME("name"),
        NOTE_COUNT("noteCount"),
        LAST_EXTRACTED("lastExtracted")
    }

    data class UiState(
        val search: String = "",
        val typeFilter: EntityType? = null,
        val sort: SortMode = SortMode.NOTE_COUNT,
        val selectionMode: Boolean = false,
        val selectedKeys: Set<String> = emptySet(),
        val entities: List<EntityListRow> = emptyList(),
        val totalCount: Int = 0,
        val loading: Boolean = false,
        val showBatchDeleteConfirm: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // fix M52 (full-review):搜索输入 debounce 300ms。setSearch 写入 search 字段立刻刷新 UI(文本),
        // 但真正查表的 performRefresh() 延后 300ms 才触发,避免快速键入 N 个字符触发 N 个并发 IO 查询。
        // typeFilter / sort 变化频率低,直接同步触发 performRefresh。
        _uiState
            .drop(1)
            .debounce(300)
            .onEach { performRefresh() }
            .launchIn(viewModelScope)
        refresh()
        loadTotalCount()
    }

    fun setSearch(query: String) {
        // 仅更新 search 字段并立刻切 loading;真实 IO 由 debounce collector 触发。
        _uiState.value = _uiState.value.copy(search = query, loading = true)
    }

    fun setTypeFilter(type: EntityType?) {
        _uiState.value = _uiState.value.copy(typeFilter = type)
        viewModelScope.launch { performRefresh() }
    }

    fun setSort(sort: SortMode) {
        _uiState.value = _uiState.value.copy(sort = sort)
        viewModelScope.launch { performRefresh() }
    }

    fun toggleSelection(entityKey: String) {
        val current = _uiState.value
        val newSelection = if (entityKey in current.selectedKeys) {
            current.selectedKeys - entityKey
        } else {
            current.selectedKeys + entityKey
        }
        _uiState.value = current.copy(
            selectedKeys = newSelection,
            selectionMode = newSelection.isNotEmpty()
        )
    }

    fun exitSelectionMode() {
        _uiState.value = _uiState.value.copy(selectionMode = false, selectedKeys = emptySet())
    }

    fun requestBatchDelete() {
        if (_uiState.value.selectedKeys.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(showBatchDeleteConfirm = true)
        }
    }

    fun cancelBatchDelete() {
        _uiState.value = _uiState.value.copy(showBatchDeleteConfirm = false)
    }

    fun confirmBatchDelete() {
        val keys = _uiState.value.selectedKeys.toList()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { entityDao.deleteByEntityKeys(keys) }
            _uiState.value = _uiState.value.copy(
                showBatchDeleteConfirm = false,
                selectionMode = false,
                selectedKeys = emptySet()
            )
            refresh()
            loadTotalCount()
        }
    }

    /**
     * 立即触发刷新(setTypeFilter / setSort / 批量删除完成后)。
     * setSearch 不走这条路径,改由 [debounce] collector 触发。
     */
    private fun refresh() {
        viewModelScope.launch { performRefresh() }
    }

    private suspend fun performRefresh() {
        val s = _uiState.value
        _uiState.value = s.copy(loading = true)
        val list = withContext(Dispatchers.IO) {
            entityDao.queryEntityList(s.search.takeIf { it.isNotBlank() }, s.typeFilter, s.sort.key)
        }
        _uiState.value = _uiState.value.copy(entities = list, loading = false)
    }

    private fun loadTotalCount() {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) { entityDao.countDistinctEntityKeys() }
            _uiState.value = _uiState.value.copy(totalCount = count)
        }
    }
}
