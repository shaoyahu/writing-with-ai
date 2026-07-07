package com.yy.writingwithai.feature.my.entity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.data.db.dao.entity.EntityListRow
import com.yy.writingwithai.core.data.db.dao.entity.NoteEntityDao
import com.yy.writingwithai.core.note.entity.EntityType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        refresh()
        loadTotalCount()
    }

    fun setSearch(query: String) {
        _uiState.value = _uiState.value.copy(search = query)
        refresh()
    }

    fun setTypeFilter(type: EntityType?) {
        _uiState.value = _uiState.value.copy(typeFilter = type)
        refresh()
    }

    fun setSort(sort: SortMode) {
        _uiState.value = _uiState.value.copy(sort = sort)
        refresh()
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

    private fun refresh() {
        val s = _uiState.value
        viewModelScope.launch {
            _uiState.value = s.copy(loading = true)
            val list = withContext(Dispatchers.IO) {
                entityDao.queryEntityList(s.search.takeIf { it.isNotBlank() }, s.typeFilter, s.sort.key)
            }
            _uiState.value = _uiState.value.copy(entities = list, loading = false)
        }
    }

    private fun loadTotalCount() {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) { entityDao.countDistinctEntityKeys() }
            _uiState.value = _uiState.value.copy(totalCount = count)
        }
    }
}
