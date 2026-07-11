package com.yy.writingwithai.feature.quicknote.graph

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.note.graph.ForceLayout
import com.yy.writingwithai.core.note.graph.GraphDataLoader
import com.yy.writingwithai.core.note.graph.GraphSnapshot
import com.yy.writingwithai.core.note.graph.LayoutCache
import com.yy.writingwithai.core.note.graph.NodeCoords
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * note-graph-view · 图屏 ViewModel + 状态机(tasks §4)。
 *
 * 状态机(GraphUiState):
 * - `Empty`:`snapshot.nodes.size <= 1`(无 1-hop / 2-hop / entity)
 * - `Loading`:首次进入 / refresh
 * - `Loaded(snapshot, coords)`:渲染
 * - `Error(message)`:catch 到任何异常
 *
 * 流程:
 * 1. `loader.load(noteId)` 拉 [GraphSnapshot]
 * 2. 若 [GraphSnapshot.nodes] ≤ 1 → emit Empty
 * 3. 读 [LayoutCache.get](centerNoteId),缓存命中就用;否则走 [ForceLayout.converge]
 *    - 收敛成功 → emit Loaded
 *    - 收敛失败 → 走 [ForceLayout.fallback](circular),仍 emit Loaded(coords=circular)
 * 4. [LayoutCache.put] 写回(成功 / 失败都写,保 Self-persist)
 */
@HiltViewModel
class NoteGraphViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val loader: GraphDataLoader,
    private val layoutCache: LayoutCache,
    private val forceLayout: ForceLayout
) : ViewModel() {

    private val _uiState = MutableStateFlow<GraphUiState>(GraphUiState.Loading)
    val uiState: StateFlow<GraphUiState> = _uiState.asStateFlow()

    /** 同一 noteId 只触发一次(Compose `remember(noteId)` 兜底,这里再加 idempotency)。 */
    fun loadSnapshot(noteId: String) {
        if (noteId.isBlank()) {
            _uiState.value = GraphUiState.Error("noteId is blank")
            return
        }
        if (_uiState.value is GraphUiState.Loaded &&
            noteIdOfState(_uiState.value) == noteId
        ) {
            return
        }
        viewModelScope.launch {
            _uiState.value = GraphUiState.Loading
            try {
                val snap = withContext(Dispatchers.IO) { loader.load(noteId) }
                if (snap.nodes.size <= 1) {
                    _uiState.value = GraphUiState.Empty
                    return@launch
                }
                val cached = withContext(Dispatchers.IO) { layoutCache.get(noteId) }
                val coords: Map<String, NodeCoords> = if (
                    cached != null && cached.keys.containsAll(snap.nodes.map { it.noteId })
                ) {
                    cached
                } else {
                    val result = withContext(Dispatchers.Default) {
                        forceLayout.converge(snap)
                    }
                    if (result.success) result.coords else forceLayout.fallback(snap)
                }
                withContext(Dispatchers.IO) { layoutCache.put(noteId, coords) }
                _uiState.value = GraphUiState.Loaded(snap, coords)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = GraphUiState.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun noteIdOfState(state: GraphUiState): String? = when (state) {
        is GraphUiState.Loaded -> state.snapshot.centerNodeId
        else -> null
    }
}

/** note-graph-view · 图屏 UI 状态。 */
sealed interface GraphUiState {
    data object Empty : GraphUiState
    data object Loading : GraphUiState
    data class Loaded(
        val snapshot: GraphSnapshot,
        val coords: Map<String, NodeCoords>
    ) : GraphUiState
    data class Error(val message: String) : GraphUiState
}
