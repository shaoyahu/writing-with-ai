package com.yy.writingwithai.feature.quicknote.detail

import androidx.compose.ui.text.TextRange
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.core.feishu.api.FeishuError
import com.yy.writingwithai.core.feishu.sync.FeishuRefEntity
import com.yy.writingwithai.core.feishu.sync.FeishuRefStatus
import com.yy.writingwithai.core.feishu.sync.FeishuSyncService
import com.yy.writingwithai.core.ui.AiActionFabState
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
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val repository: NoteRepository,
    private val feishuSyncService: FeishuSyncService,
    private val refDao: com.yy.writingwithai.core.feishu.sync.FeishuRefDao,
    private val noteAttachmentDao: com.yy.writingwithai.core.data.db.dao.NoteAttachmentDao,
    private val attachmentStore: com.yy.writingwithai.core.media.AttachmentStore,
    private val imageCompressor: com.yy.writingwithai.core.media.ImageCompressor
) : ViewModel() {
    // H3 修:`requireNotNull` 在 process-death / 深链 / saved state 跨版本场景会 IAE crash。
    // 改为可空,缺失时直接进入 NotFound,避免进程崩溃。
    private val noteId: String? = savedStateHandle.get<String>("id")

    private val _uiState = MutableStateFlow<NoteDetailUiState>(NoteDetailUiState.Loading)
    val uiState: StateFlow<NoteDetailUiState> = _uiState.asStateFlow()

    init {
        if (noteId.isNullOrBlank()) {
            _uiState.value = NoteDetailUiState.NotFound
        } else {
            // H8 修:删 `noteUpdateEvents` push 强刷路径(原 `delay(100)` hack + 双 launch 写同一 _uiState race);
            // Room Flow 是 single source of truth,`NonCancellable { upsert }` 退栈时 invalidation 已传播,
            // 主路径 Flow 自然收到新值(配合 AiActionViewModel.acceptReplace 删 `delay(150)` + `tryEmit`)。
            viewModelScope.launch {
                repository.observeNoteWithTags(noteId)
                    .collect { item ->
                        _uiState.value = if (item == null) {
                            NoteDetailUiState.NotFound
                        } else {
                            NoteDetailUiState.Content(
                                note = item,
                                wordCount = WordCount.of(item.note.content),
                                readMinutes = ReadingTime.minutesOf(item.note.content)
                            )
                        }
                    }
            }
        }
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
                started = SharingStarted.Eagerly,
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

    // feishu-bidir-sync:同步状态
    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _syncLoading = MutableStateFlow(false)
    val syncLoading: StateFlow<Boolean> = _syncLoading.asStateFlow()

    private val _feishuRef = MutableStateFlow<FeishuRefEntity?>(null)
    val feishuRef: StateFlow<FeishuRefEntity?> = _feishuRef.asStateFlow()

    init {
        // feishu-bidir-sync:加载 feishu_ref 状态
        noteId?.let { id ->
            viewModelScope.launch {
                _feishuRef.value = feishuSyncService.getRef(id)
            }
        }
    }

    fun pushToFeishu() {
        val id = noteId ?: return
        viewModelScope.launch {
            _syncLoading.value = true
            _syncMessage.value = null
            try {
                val msg = feishuSyncService.push(id)
                _syncMessage.value = msg
                _feishuRef.value = feishuSyncService.getRef(id)
            } catch (e: FeishuError) {
                _syncMessage.value = "同步失败: ${e.message}"
            } catch (e: Throwable) {
                _syncMessage.value = "同步失败: ${e.message}"
            } finally {
                _syncLoading.value = false
            }
        }
    }

    fun pullFromFeishu(docUrl: String) {
        viewModelScope.launch {
            _syncLoading.value = true
            _syncMessage.value = null
            try {
                val docId = extractDocId(docUrl)
                val msg = feishuSyncService.pull(docId, docUrl)
                _syncMessage.value = msg
            } catch (e: FeishuError) {
                _syncMessage.value = "拉取失败: ${e.message}"
            } catch (e: Throwable) {
                _syncMessage.value = "拉取失败: ${e.message}"
            } finally {
                _syncLoading.value = false
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    // feishu-bidir-sync:conflict detection + resolution
    private val _showConflictDialog = MutableStateFlow(false)
    val showConflictDialog: StateFlow<Boolean> = _showConflictDialog.asStateFlow()

    fun resolveConflictKeepLocal() {
        val ref = _feishuRef.value ?: return
        viewModelScope.launch {
            refDao.upsert(ref.copy(status = FeishuRefStatus.DIRTY))
            _feishuRef.value = refDao.getByNoteId(ref.noteId)
            _showConflictDialog.value = false
            pushToFeishu()
        }
    }

    fun resolveConflictKeepRemote() {
        val ref = _feishuRef.value ?: return
        viewModelScope.launch {
            // 保留飞书版本:重新 pull(覆盖本地)
            try {
                val titleHint = (uiState.value as? NoteDetailUiState.Content)?.note?.note?.title ?: "来自飞书"
                feishuSyncService.pull(ref.docId, ref.docUrl, titleHint)
                _feishuRef.value = refDao.getByNoteId(ref.noteId)
                _showConflictDialog.value = false
                _syncMessage.value = "已采用飞书版本"
            } catch (e: FeishuError) {
                _syncMessage.value = "解决冲突失败: ${e.message}"
            }
        }
    }

    fun cancelConflictResolution() {
        _showConflictDialog.value = false
    }

    // feishu-bidir-sync:远程已删恢复 —— 删旧 ref + push 创新文档
    fun recreateFeishuDoc() {
        val ref = _feishuRef.value ?: return
        viewModelScope.launch {
            refDao.deleteByNoteId(ref.noteId)
            _feishuRef.value = null
            pushToFeishu()
        }
    }

    private fun extractDocId(url: String): String {
        // 从飞书文档 URL 提取 docId
        // 格式:https://bytedance.feishu.cn/docx/{docId}?...
        val regex = Regex("""/docx?/([A-Za-z0-9_-]+)""")
        return regex.find(url)?.groupValues?.get(1) ?: url
    }
    fun getExportMarkdown(): String? {
        val note = (uiState.value as? NoteDetailUiState.Content)?.note?.note ?: return null
        return buildString {
            if (note.title.isNotBlank()) {
                append("# ")
                append(note.title)
                append("\n\n")
            }
            append(note.content)
        }
    }

    fun getExportFilename(extension: String): String {
        val note = (uiState.value as? NoteDetailUiState.Content)?.note?.note ?: return "note.$extension"
        return note.title.ifBlank { note.id } + ".$extension"
    }

    // media-attachment-infrastructure · 附件观察 + 添加
    fun observeAttachments():
        kotlinx.coroutines.flow.Flow<List<com.yy.writingwithai.core.data.db.entity.NoteAttachmentEntity>> {
        val id = noteId ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return noteAttachmentDao.observeForNote(id)
    }

    fun addAttachment(uri: android.net.Uri) {
        val id = noteId ?: return
        viewModelScope.launch {
            try {
                val attachmentId = java.util.UUID.randomUUID().toString()
                val sourceFile = java.io.File(appContext.cacheDir, "tmp_$attachmentId.jpg")
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    sourceFile.outputStream().use { output -> input.copyTo(output) }
                } ?: return@launch
                val destFile = attachmentStore.getAttachmentFile(id, attachmentId, "jpg")
                imageCompressor.compress(sourceFile, destFile)
                sourceFile.delete()
                noteAttachmentDao.insert(
                    com.yy.writingwithai.core.data.db.entity.NoteAttachmentEntity(
                        id = attachmentId,
                        noteId = id,
                        mimeType = "image/jpeg",
                        localPath = destFile.absolutePath,
                        fileSize = destFile.length(),
                        createdAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                if (com.yy.writingwithai.BuildConfig.DEBUG) {
                    android.util.Log.e("DetailVM", "addAttachment failed", e)
                }
            }
        }
    }
}

/** M3:详情屏顶部"上次 AI 操作"行投影 — `opKey` 是 aiwriting op 名("expand"/"polish"/"organize"),UI 层 `stringResource` 翻译。 */
data class AiMetaDisplay(val opKey: String, val opAt: String)

// M3 修:SimpleDateFormat hoist 到顶层,避免每次 map { formatLocalDateTime(...) } 重建。
private fun formatLocalDateTime(epochMillis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}
