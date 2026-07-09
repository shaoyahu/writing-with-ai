package com.yy.writingwithai.feature.my.entity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.dao.entity.EntityAliasDao
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
    private val aliasDao: EntityAliasDao,
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
                // fix-full-review MEDIUM:只取 entityKey 精确匹配的行，不再 fallback 到
                // list.firstOrNull()——静默返回错误实体比显示空更危险。
                val metaRow = list.firstOrNull { it.entityKey == entityKey }
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
            val notes = withContext(Dispatchers.IO) {
                // fix M39 (full-review):之前 hits.mapNotNull 内每个 hit 调一次
                // noteDao.getById → N+1 SQL 查询,hits 上限 200 时对 UI 延迟 + Room IO
                // 都有负担。改用新增的 NoteDao.getByIds IN(...) 一次拿,本地 Map 查表。
                val ids = hits.map { it.noteId }
                val byId = noteDao.getByIds(ids).associateBy { it.id }
                hits.mapNotNull { hit ->
                    val note = byId[hit.noteId] ?: return@mapNotNull null
                    AssociatedNote(
                        noteId = hit.noteId,
                        title = note.title,
                        preview = buildSnippet(note.title, note.content, hit.spanStart, hit.spanEnd),
                        source = hit.source
                    )
                }
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
        val state = _uiState.value
        val key = state.entityKey
        val entityType = state.entityType
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                entityDao.deleteByEntityKey(key)
                // fix M11 (full-review):同时清 entity_aliases 别名行(aliasKey 或
                // canonicalEntityKey = 该 entity),防止孤儿 alias 污染反向链接。
                aliasDao.deleteByEntityKey(entityType, key)
            }
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
