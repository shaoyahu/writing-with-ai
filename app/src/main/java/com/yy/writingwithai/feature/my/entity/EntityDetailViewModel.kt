package com.yy.writingwithai.feature.my.entity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.data.db.NoteDao
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
class EntityDetailViewModel @Inject constructor(
    private val entityDao: NoteEntityDao,
    private val noteDao: NoteDao
) : ViewModel() {

    data class AssociatedNote(
        val noteId: String,
        val title: String,
        val preview: String,
        val source: String
    )

    data class UiState(
        val entityKey: String = "",
        val surfaceForm: String = "",
        val entityType: EntityType = EntityType.CONCEPT,
        val noteCount: Int = 0,
        val source: String = "AI_EXTRACTED",
        val notes: List<AssociatedNote> = emptyList(),
        val loading: Boolean = true,
        val showDeleteConfirm: Boolean = false,
        val deleted: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun load(entityKey: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(entityKey = entityKey, loading = true)
            val (meta, hits) = withContext(Dispatchers.IO) {
                val list = entityDao.queryEntityList(null, null, "lastExtracted")
                val metaRow = list.firstOrNull { it.entityKey == entityKey } ?: list.firstOrNull()
                val hits = entityDao.queryNotesByEntity(entityKey, 200)
                metaRow to hits
            }
            if (meta != null) {
                _uiState.value = _uiState.value.copy(
                    surfaceForm = meta.surfaceForm,
                    entityType = meta.entityType,
                    noteCount = meta.noteCount,
                    source = hits.firstOrNull()?.source ?: "AI_EXTRACTED"
                )
            }
            val notes = hits.mapNotNull { hit ->
                val note = withContext(Dispatchers.IO) { noteDao.getById(hit.noteId) } ?: return@mapNotNull null
                AssociatedNote(
                    noteId = hit.noteId,
                    title = note.title,
                    preview = buildSnippet(note.title, note.content, hit.spanStart, hit.spanEnd),
                    source = hit.source
                )
            }
            _uiState.value = _uiState.value.copy(notes = notes, loading = false)
        }
    }

    fun requestDelete() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = true)
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = false)
    }

    fun confirmDelete() {
        val key = _uiState.value.entityKey
        viewModelScope.launch {
            withContext(Dispatchers.IO) { entityDao.deleteByEntityKey(key) }
            _uiState.value = _uiState.value.copy(deleted = true, showDeleteConfirm = false)
        }
    }

    private fun buildSnippet(title: String, content: String, spanStart: Int, spanEnd: Int): String {
        if (content.isBlank()) return ""
        val start = spanStart.coerceIn(0, content.length)
        val end = spanEnd.coerceIn(start, content.length)
        if (start >= end) return content.take(80).let { if (it.length < content.length) "$it…" else it }
        val pre = (start - 24).coerceAtLeast(0)
        val post = (end + 24).coerceAtMost(content.length)
        val prefix = if (pre > 0) "…" else ""
        val suffix = if (post < content.length) "…" else ""
        return prefix + content.substring(pre, post) + suffix
    }
}
