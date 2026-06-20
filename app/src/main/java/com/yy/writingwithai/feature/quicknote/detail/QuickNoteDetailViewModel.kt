package com.yy.writingwithai.feature.quicknote.detail

import androidx.compose.ui.text.TextRange
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.feature.aiwriting.AiActionFabState
import com.yy.writingwithai.feature.quicknote.model.NoteDetailUiState
import com.yy.writingwithai.feature.quicknote.model.ReadingTime
import com.yy.writingwithai.feature.quicknote.model.WordCount
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class QuickNoteDetailViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NoteRepository
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
                            readMinutes = ReadingTime.minutesOf(item.note.content)
                        )
                    }
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000L),
                    initialValue = NoteDetailUiState.Loading
                )
        }

    /** M3:BasicTextField 选区状态(M3 §7)。 */
    private val _selection = MutableStateFlow(TextRange.Zero)
    val selection: StateFlow<TextRange> = _selection.asStateFlow()

    /** M3:选区状态投影成 FAB 状态(Share / AutoAwesome 二选一)。 */
    val fabState: StateFlow<AiActionFabState> =
        _selection
            .map { AiActionFabState.fromSelection(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = AiActionFabState.DEFAULT
            )

    /** M3:`lastAiOp` / `lastAiAt` 投影给 detail 屏顶部 metadata 行。 */
    val aiMetaDisplay: StateFlow<AiMetaDisplay?> =
        uiState
            .map { current ->
                val note = (current as? NoteDetailUiState.Content)?.note?.note
                if (note == null) return@map null
                val op = note.lastAiOp ?: return@map null
                val at = note.lastAiAt ?: return@map null
                AiMetaDisplay(opKey = op, opAt = formatLocalDateTime(at))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = null
            )

    fun onSelectionChange(range: TextRange) {
        _selection.value = range
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

/** M3:详情屏顶部"上次 AI 操作"行投影 — `opKey` 是 aiwriting op 名("expand"/"polish"/"organize"),UI 层 `stringResource` 翻译。 */
data class AiMetaDisplay(val opKey: String, val opAt: String)

// M3 修:`SimpleDateFormat` hoist 到顶层,避免每次 `map { formatLocalDateTime(...) }` 重建。
// 本身非线程安全;`map` 单线程 + Compose 端 `WhileSubscribed(5s)` 重订阅安全。
private val DATE_TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

private fun formatLocalDateTime(epochMs: Long): String = DATE_TIME_FORMAT.format(Date(epochMs))
