package com.yy.writingwithai.feature.quicknote.list

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.core.feishu.sync.FeishuImportService
import com.yy.writingwithai.core.feishu.sync.FeishuRefEntity
import com.yy.writingwithai.core.feishu.sync.FeishuSyncService
import com.yy.writingwithai.core.prefs.SearchHistoryStoreImpl
import com.yy.writingwithai.feature.quicknote.model.NoteListUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import kotlinx.coroutines.withContext

private const val STOP_TIMEOUT_MS = 5_000L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class QuickNoteListViewModel
@Inject
constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: NoteRepository,
    private val feishuSyncService: FeishuSyncService,
    // feishu-import-from-folder:列表页导入入口用
    private val feishuImportService: FeishuImportService,
    // fix M14 (full-review):Hilt 注入 SearchHistoryStoreImpl,避免 object + Context 传参
    private val searchHistoryStore: SearchHistoryStoreImpl
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedTag = MutableStateFlow<String?>(null)

    // review r2 修:暴露 StateFlow 而非 MutableStateFlow，防止外部直接写入。
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()
    private val _isSelectMode = MutableStateFlow(false)
    val isSelectMode: StateFlow<Boolean> = _isSelectMode.asStateFlow()

    val uiState: StateFlow<NoteListUiState> =
        combine(
            combine(query, selectedTag) { q, tag -> q to tag }
                // M3 修:`observeAllTags` 提到外层，避免 selectedTag 变化触发 inner combine 重启
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
                searchHistoryStore.add(q.trim())
            }
        }
    }

    /** fix M14 (full-review):Screen 层通过 VM 间接访问 store(避免屏直接 new infrastructure)。 */
    suspend fun getSearchHistory(): List<String> = searchHistoryStore.getAll()

    /** fix M14 (full-review):Screen 层通过 VM 间接 remove + 读,UI 立刻刷新。 */
    suspend fun removeSearchHistory(query: String): List<String> {
        searchHistoryStore.remove(query)
        return searchHistoryStore.getAll()
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

    // ---- note-list-card-actions · 长按菜单 / 左滑背景按钮共用入口 ----

    /**
     * note-list-card-actions · 切换置顶状态。失败 Log.w，不传播(列表屏无 Snackbar)。
     */
    fun togglePinned(noteId: String, currentPinned: Boolean) {
        viewModelScope.launch {
            try {
                repository.setPinned(noteId, !currentPinned)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.w("QuickNoteListVM", "togglePinned failed for noteId=$noteId", e)
            }
        }
    }

    /**
     * note-list-card-actions · 删除笔记(走 NonCancellable，详情页 delete 的同款反 race 保护)。
     * 列表删除不需要 back 回调:observeAll 自动从列表移除，UI 自动刷新。
     */
    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            withContext(kotlinx.coroutines.NonCancellable) {
                try {
                    repository.delete(noteId)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    android.util.Log.w("QuickNoteListVM", "deleteNote failed for noteId=$noteId", e)
                }
            }
        }
    }

    /**
     * note-list-card-actions · 长按菜单「添加已有标签」触发。
     * 挂已有 tag(IGNORE 策略，重复挂 no-op)。
     */
    fun addExistingTag(noteId: String, tag: String) {
        viewModelScope.launch {
            try {
                repository.addTagToNote(noteId, tag)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.w("QuickNoteListVM", "addExistingTag failed for noteId=$noteId", e)
            }
        }
    }

    private val _feishuRefs = MutableStateFlow<Map<String, FeishuRefEntity>>(emptyMap())
    val feishuRefs: StateFlow<Map<String, FeishuRefEntity>> = _feishuRefs.asStateFlow()

    init {
        // fix-2026-06-26-review-r3 H22:原 `uiState.collect` 在每次 query/tag/notes 变化时
        // 立即触发 `getRefsForNotes`，搜索抖动时连发，加重 Room IO。
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

    // ---- feishu-import-from-folder:列表页导入入口 ----

    /**
     * feishu-import-from-folder:授权状态请求结果。
     *
     * 用户点击 dropdown item 时 Screen 调 [requestFeishuAuthCheck],授权结果
     * 通过本事件回给 Screen(`Authorized` → 弹 dialog / 跳 sub-screen;
     * `Unauthorized` → 弹未授权 dialog)。改 fire-and-forget 而非 suspend:
     * Screen 端不持有 coroutine scope,屏幕旋转不会丢回调。
     */
    sealed class AuthCheckResult {
        data object Authorized : AuthCheckResult()
        data object Unauthorized : AuthCheckResult()
    }

    private val _importResultEvents = kotlinx.coroutines.flow.MutableSharedFlow<FeishuImportService.ImportResult>(
        replay = 0,
        extraBufferCapacity = 8
    )
    val importResultEvents: SharedFlow<FeishuImportService.ImportResult> = _importResultEvents.asSharedFlow()

    private val _authCheckEvents = kotlinx.coroutines.flow.MutableSharedFlow<AuthCheckResult>(
        replay = 0,
        extraBufferCapacity = 4
    )
    val authCheckEvents: kotlinx.coroutines.flow.SharedFlow<AuthCheckResult> = _authCheckEvents.asSharedFlow()

    /**
     * feishu-import-from-folder:用户点 dropdown item 触发授权检查。
     * 异步返回结果通过 [authCheckEvents] 通知 Screen。
     */
    fun requestFeishuAuthCheck() {
        viewModelScope.launch {
            val result = try {
                feishuImportService.ensureAuthorized()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) {
                false
            }
            _authCheckEvents.tryEmit(
                if (result) AuthCheckResult.Authorized else AuthCheckResult.Unauthorized
            )
        }
    }

    /**
     * 单文档导入。fire-and-forget,结果通过 [importResultEvents] 通知。
     *
     * catch 兜底:Throwable → Failure 但保留 e.message 给用户,同时 Log.w 写堆栈到 logcat
     * (review 2026-07-07 Finding #10:原版只用 javaClass.simpleName,丢 e.message 和堆栈)。
     * 与既有 togglePinned / deleteNote 约定对齐:`Log.w` 写日志,UI 显示可读错误。
     */
    fun importSingleDoc(input: String) {
        viewModelScope.launch {
            val result = try {
                feishuImportService.importSingleDoc(input)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "importSingleDoc failed for input=$input", e)
                val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                FeishuImportService.ImportResult.Failure("导入失败: $msg")
            }
            _importResultEvents.tryEmit(result)
        }
    }

    private companion object {
        // 搜索抖动期间聚合多次 query 变化，只在用户停手 300ms 后才查 ref 表。
        const val REFS_DEBOUNCE_MS = 300L

        // review 2026-07-07 Finding #10:catch Throwable 时 logcat tag,排查导入失败用
        const val TAG = "QuickNoteListVM"
    }
}
