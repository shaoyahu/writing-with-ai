package com.yy.writingwithai.feature.quicknote.detail

import androidx.compose.ui.text.TextRange
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.common.mapToUserMessage
import com.yy.writingwithai.core.data.db.dao.entity.NoteEntityDao
import com.yy.writingwithai.core.data.db.entity.LinkType
import com.yy.writingwithai.core.data.db.entity.entity.NoteEntityRow
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.core.feishu.api.FeishuError
import com.yy.writingwithai.core.feishu.sync.FeishuRefEntity
import com.yy.writingwithai.core.feishu.sync.FeishuRefStatus
import com.yy.writingwithai.core.feishu.sync.FeishuSyncService
import com.yy.writingwithai.core.feishu.sync.FolderMigrationChoice
import com.yy.writingwithai.core.note.NoteLinker
import com.yy.writingwithai.core.note.RelatedNote
import com.yy.writingwithai.core.note.entity.EntityExtractor
import com.yy.writingwithai.core.note.entity.NoteEntityMatcher
import com.yy.writingwithai.core.prefs.SecureApiKeyStore
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
import kotlinx.coroutines.flow.first
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
    private val imageCompressor: com.yy.writingwithai.core.media.ImageCompressor,
    private val entityExtractor: EntityExtractor,
    private val noteLinker: NoteLinker,
    private val entityDao: NoteEntityDao,
    // entity-management-and-ai-decompose §2.4:打开笔记时自动匹配已有实体
    private val entityMatcher: NoteEntityMatcher,
    // entity-management-and-ai-decompose §2.7:拆解前 API key 检查
    private val secureApiKeyStore: SecureApiKeyStore
) : ViewModel() {
    // H3 修:`requireNotNull` 在 process-death / 深链 / saved state 跨版本场景会 IAE crash。
    // 改为可空，缺失时直接进入 NotFound，避免进程崩溃。
    private val noteId: String? = savedStateHandle.get<String>("id")

    private val _uiState = MutableStateFlow<NoteDetailUiState>(NoteDetailUiState.Loading)
    val uiState: StateFlow<NoteDetailUiState> = _uiState.asStateFlow()

    init {
        if (noteId.isNullOrBlank()) {
            _uiState.value = NoteDetailUiState.NotFound
        } else {
            // H8 修:删 `noteUpdateEvents` push 强刷路径(原 `delay(100)` hack + 双 launch 写同一 _uiState race);
            // Room Flow 是 single source of truth,`NonCancellable { upsert }` 退栈时 invalidation 已传播，
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
                started = SharingStarted.WhileSubscribed(5_000L),
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
            // M6 修:用户点确认后立刻 back 退出 detail → viewModelScope 被取消，
            // 删除协程中断，笔记没被删。包 NonCancellable 强制执行。
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
    // CR-FIX-M6:结构化 sealed 替代 String?,UI 不再 startsWith("同步完成:") 解析。
    private val _syncMessage = MutableStateFlow<SyncMessage?>(null)
    val syncMessage: StateFlow<SyncMessage?> = _syncMessage.asStateFlow()

    private val _syncLoading = MutableStateFlow(false)
    val syncLoading: StateFlow<Boolean> = _syncLoading.asStateFlow()

    // D5 · feishu-sync-feedback · 记录最后一次同步动作(push/pull) + 所需参数,
    // 供 retryLastSync() 在 Network/Server 失败时复用。私有状态,不外露。
    private val _lastSyncAction = MutableStateFlow<SyncAction>(SyncAction.None)
    val lastSyncAction: StateFlow<SyncAction> = _lastSyncAction.asStateFlow()

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
        // D5 · 记录最后一次同步动作,供 retryLastSync() 复用
        _lastSyncAction.value = SyncAction.PUSH
        viewModelScope.launch {
            _syncLoading.value = true
            _syncMessage.value = null
            try {
                val ref = feishuSyncService.push(id)
                val noteTitle = (uiState.value as? NoteDetailUiState.Content)?.note?.note?.title ?: "笔记"
                _syncMessage.value = SyncMessage.Success(noteTitle = noteTitle, docUrl = ref.docUrl)
                _feishuRef.value = feishuSyncService.getRef(id)
            } catch (e: FeishuError.Conflict) {
                _feishuRef.value = feishuSyncService.getRef(id)
                _showConflictDialog.value = true
                _syncMessage.value = SyncMessage.Failure.Conflict(
                    noteId = id,
                    docId = _feishuRef.value?.docId ?: "",
                    docUrl = _feishuRef.value?.docUrl ?: ""
                )
            } catch (e: FeishuError.FolderTokenMismatch) {
                _feishuRef.value = feishuSyncService.getRef(id)
                _folderMigrationInfo.value = e
                _showFolderMigrationDialog.value = true
                _syncMessage.value = SyncMessage.Failure.FolderMigration(
                    noteId = id,
                    docId = e.docId,
                    docUrl = e.docUrl,
                    currentFolderToken = e.currentFolderToken,
                    refFolderToken = e.refFolderToken
                )
            } catch (e: FeishuError.NotFound) {
                val ref = _feishuRef.value
                _syncMessage.value = SyncMessage.Failure.RemoteDeleted(
                    noteId = id,
                    docId = ref?.docId ?: e.resource,
                    docUrl = ref?.docUrl ?: ""
                )
            } catch (e: FeishuError.NetworkError) {
                _syncMessage.value = SyncMessage.Failure.Network(e.detail)
            } catch (e: FeishuError.ServerError) {
                _syncMessage.value = SyncMessage.Failure.Server(e.code)
            } catch (e: FeishuError.RateLimited) {
                _syncMessage.value = SyncMessage.Failure.RateLimited(e.retryAfterSeconds)
            } catch (e: FeishuError.BadRequest) {
                _syncMessage.value = SyncMessage.Failure.Empty
            } catch (e: FeishuError) {
                _syncMessage.value = SyncMessage.Failure.Unknown(e.message ?: e.javaClass.simpleName)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _syncMessage.value = SyncMessage.Failure.Unknown(e.message ?: e.javaClass.simpleName)
            } finally {
                _syncLoading.value = false
            }
        }
    }

    fun pullFromFeishu(docUrl: String) {
        // D5 · 记录 pull 动作 + docUrl,供 retryLastSync() 复用
        _lastSyncAction.value = SyncAction.PULL(docUrl)
        viewModelScope.launch {
            _syncLoading.value = true
            _syncMessage.value = null
            try {
                val docId = extractDocId(docUrl)
                feishuSyncService.pull(docId, docUrl)
                val noteTitle = (uiState.value as? NoteDetailUiState.Content)?.note?.note?.title ?: "笔记"
                _syncMessage.value = SyncMessage.Success(noteTitle = noteTitle, docUrl = docUrl)
            } catch (e: FeishuError.Conflict) {
                noteId?.let { _feishuRef.value = feishuSyncService.getRef(it) }
                _showConflictDialog.value = true
                _syncMessage.value = SyncMessage.Failure.Conflict(
                    noteId = noteId ?: "",
                    docId = noteId?.let { feishuSyncService.getRef(it)?.docId } ?: "",
                    docUrl = docUrl
                )
            } catch (e: FeishuError.NetworkError) {
                _syncMessage.value = SyncMessage.Failure.Network(e.detail)
            } catch (e: FeishuError.ServerError) {
                _syncMessage.value = SyncMessage.Failure.Server(e.code)
            } catch (e: FeishuError.RateLimited) {
                _syncMessage.value = SyncMessage.Failure.RateLimited(e.retryAfterSeconds)
            } catch (e: FeishuError.BadRequest) {
                _syncMessage.value = SyncMessage.Failure.Empty
            } catch (e: FeishuError.NotFound) {
                _syncMessage.value = SyncMessage.Failure.RemoteDeleted(
                    noteId = noteId ?: "",
                    docId = "",
                    docUrl = docUrl
                )
            } catch (e: FeishuError) {
                _syncMessage.value = SyncMessage.Failure.Unknown(e.message ?: e.javaClass.simpleName)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _syncMessage.value = SyncMessage.Failure.Unknown(e.message ?: e.javaClass.simpleName)
            } finally {
                _syncLoading.value = false
            }
        }
    }

    /**
     * D5 · feishu-sync-feedback · 重试上一次同步动作。
     *
     * push 失败重试 push;pull 失败重试 pull(用上次保存的 docUrl)。
     * 上一次动作不存在(no-op)或 syncMessage 不在 Failure 状态都不重试。
     */
    fun retryLastSync() {
        when (val action = _lastSyncAction.value) {
            SyncAction.None -> { /* no-op:还没同步过 */ }
            SyncAction.PUSH -> pushToFeishu()
            is SyncAction.PULL -> pullFromFeishu(action.docUrl)
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
        // D5 · 走 push 路径,标记 lastSyncAction
        _lastSyncAction.value = SyncAction.PUSH
        viewModelScope.launch {
            _syncLoading.value = true
            _syncMessage.value = null
            try {
                refDao.upsert(ref.copy(status = FeishuRefStatus.DIRTY))
                _feishuRef.value = refDao.getByNoteId(ref.noteId)
                _showConflictDialog.value = false
                // H7 fix:inline push logic instead of calling pushToFeishu() which launches
                // a nested viewModelScope.launch, causing concurrent writes to _feishuRef / _syncLoading.
                val updatedRef = feishuSyncService.push(ref.noteId)
                val noteTitle = (uiState.value as? NoteDetailUiState.Content)?.note?.note?.title ?: "笔记"
                _syncMessage.value = SyncMessage.Success(noteTitle = noteTitle, docUrl = updatedRef.docUrl)
                _feishuRef.value = feishuSyncService.getRef(ref.noteId)
            } catch (e: FeishuError.NetworkError) {
                _showConflictDialog.value = false
                _syncMessage.value = SyncMessage.Failure.Network(e.detail)
            } catch (e: FeishuError.ServerError) {
                _showConflictDialog.value = false
                _syncMessage.value = SyncMessage.Failure.Server(e.code)
            } catch (e: FeishuError.RateLimited) {
                _showConflictDialog.value = false
                _syncMessage.value = SyncMessage.Failure.RateLimited(e.retryAfterSeconds)
            } catch (e: FeishuError.BadRequest) {
                _showConflictDialog.value = false
                _syncMessage.value = SyncMessage.Failure.Empty
            } catch (e: FeishuError.NotFound) {
                _showConflictDialog.value = false
                _syncMessage.value = SyncMessage.Failure.RemoteDeleted(
                    noteId = ref.noteId,
                    docId = ref.docId,
                    docUrl = ref.docUrl
                )
            } catch (e: FeishuError) {
                _showConflictDialog.value = false
                _syncMessage.value = SyncMessage.Failure.Unknown(e.message ?: e.javaClass.simpleName)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _showConflictDialog.value = false
                _syncMessage.value = SyncMessage.Failure.Unknown(e.message ?: e.javaClass.simpleName)
            } finally {
                _syncLoading.value = false
            }
        }
    }

    fun resolveConflictKeepRemote() {
        val ref = _feishuRef.value ?: return
        // D5 · 走 pull 路径
        _lastSyncAction.value = SyncAction.PULL(ref.docUrl)
        viewModelScope.launch {
            _syncLoading.value = true
            _syncMessage.value = null
            try {
                val titleHint = (uiState.value as? NoteDetailUiState.Content)?.note?.note?.title ?: "来自飞书"
                feishuSyncService.pull(ref.docId, ref.docUrl, titleHint)
                _feishuRef.value = refDao.getByNoteId(ref.noteId)
                _showConflictDialog.value = false
                val noteTitle = (uiState.value as? NoteDetailUiState.Content)?.note?.note?.title ?: "笔记"
                _syncMessage.value = SyncMessage.Success(noteTitle = noteTitle, docUrl = ref.docUrl)
            } catch (e: FeishuError.NetworkError) {
                _showConflictDialog.value = false
                _syncMessage.value = SyncMessage.Failure.Network(e.detail)
            } catch (e: FeishuError.ServerError) {
                _showConflictDialog.value = false
                _syncMessage.value = SyncMessage.Failure.Server(e.code)
            } catch (e: FeishuError.RateLimited) {
                _showConflictDialog.value = false
                _syncMessage.value = SyncMessage.Failure.RateLimited(e.retryAfterSeconds)
            } catch (e: FeishuError.BadRequest) {
                _showConflictDialog.value = false
                _syncMessage.value = SyncMessage.Failure.Empty
            } catch (e: FeishuError) {
                _showConflictDialog.value = false
                _syncMessage.value = SyncMessage.Failure.Unknown(e.message ?: e.javaClass.simpleName)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _showConflictDialog.value = false
                _syncMessage.value = SyncMessage.Failure.Unknown(e.message ?: e.javaClass.simpleName)
            } finally {
                _syncLoading.value = false
            }
        }
    }

    fun cancelConflictResolution() {
        _showConflictDialog.value = false
    }

    // feishu-folder-migration:folder token 变更迁移
    private val _showFolderMigrationDialog = MutableStateFlow(false)
    val showFolderMigrationDialog: StateFlow<Boolean> = _showFolderMigrationDialog.asStateFlow()

    private val _folderMigrationInfo = MutableStateFlow<FeishuError.FolderTokenMismatch?>(null)
    val folderMigrationInfo: StateFlow<FeishuError.FolderTokenMismatch?> = _folderMigrationInfo.asStateFlow()

    fun resolveFolderMigrationDeleteAndRecreate() = resolveFolderMigration(FolderMigrationChoice.DELETE_AND_RECREATE)

    fun resolveFolderMigrationUpdateInPlace() = resolveFolderMigration(FolderMigrationChoice.UPDATE_IN_PLACE)

    /**
     * feishu-folder-migration · folder token 迁移对话框的统一处理入口。
     *
     * 把 pushWithFolderMigration 的成功/失败结果映射到现有的 _syncMessage + _feishuRef +
     * _showFolderMigrationDialog 状态机,避免 DELETE_AND_RECREATE / UPDATE_IN_PLACE 两个
     * 入口复制 22 行 try/catch(过去类似 resolveConflictKeepLocal / KeepRemote 也有复制,
     * 留到下个 polish change 统一抽 helper)。
     *
     * 错误处理约定:catch FeishuError(业务错) + catch Throwable(兜底),中间 rethrow
     * CancellationException(防止协程取消被吞)。
     */
    private fun resolveFolderMigration(choice: FolderMigrationChoice) {
        val id = noteId ?: return
        // D5 · 走 push 路径
        _lastSyncAction.value = SyncAction.PUSH
        viewModelScope.launch {
            _syncLoading.value = true
            _syncMessage.value = null
            try {
                val ref = feishuSyncService.pushWithFolderMigration(id, choice)
                val noteTitle = (uiState.value as? NoteDetailUiState.Content)?.note?.note?.title ?: "笔记"
                _syncMessage.value = SyncMessage.Success(noteTitle = noteTitle, docUrl = ref.docUrl)
                _feishuRef.value = feishuSyncService.getRef(id)
                _showFolderMigrationDialog.value = false
            } catch (e: FeishuError.NetworkError) {
                _showFolderMigrationDialog.value = false
                _syncMessage.value = SyncMessage.Failure.Network(e.detail)
            } catch (e: FeishuError.ServerError) {
                _showFolderMigrationDialog.value = false
                _syncMessage.value = SyncMessage.Failure.Server(e.code)
            } catch (e: FeishuError.RateLimited) {
                _showFolderMigrationDialog.value = false
                _syncMessage.value = SyncMessage.Failure.RateLimited(e.retryAfterSeconds)
            } catch (e: FeishuError.BadRequest) {
                _showFolderMigrationDialog.value = false
                _syncMessage.value = SyncMessage.Failure.Empty
            } catch (e: FeishuError.NotFound) {
                _showFolderMigrationDialog.value = false
                _syncMessage.value = SyncMessage.Failure.RemoteDeleted(
                    noteId = id,
                    docId = "",
                    docUrl = ""
                )
            } catch (e: FeishuError) {
                _showFolderMigrationDialog.value = false
                _syncMessage.value = SyncMessage.Failure.Unknown(e.message ?: e.javaClass.simpleName)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _showFolderMigrationDialog.value = false
                _syncMessage.value = SyncMessage.Failure.Unknown(e.message ?: e.javaClass.simpleName)
            } finally {
                _syncLoading.value = false
            }
        }
    }

    fun cancelFolderMigration() {
        _showFolderMigrationDialog.value = false
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
            // fix-2026-06-27-review-r4 M14:文件 IO(copy/compress/length/delete)
            // 从 Main dispatcher 移到 IO dispatcher，避免 ANR。
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                var sourceFile: java.io.File? = null
                try {
                    val attachmentId = java.util.UUID.randomUUID().toString()
                    sourceFile = java.io.File(appContext.cacheDir, "tmp_$attachmentId.jpg")
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        sourceFile!!.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@withContext
                    val destFile = attachmentStore.getAttachmentFile(id, attachmentId, "jpg")
                    imageCompressor.compress(sourceFile, destFile)
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
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (com.yy.writingwithai.BuildConfig.DEBUG) {
                        android.util.Log.e("DetailVM", "addAttachment failed", e)
                    }
                } finally {
                    // fix-2026-06-26-review-r3 H19:compress() 抛异常时也要删 sourceFile,
                    // 否则 cache 目录累积 tmp_*.jpg。
                    sourceFile?.takeIf { it.exists() }?.delete()
                }
            }
        }
    }

    /** 删除附件（删除数据库记录 + 本地文件）。 */
    fun deleteAttachment(attachmentId: String) {
        val id = noteId ?: return
        viewModelScope.launch {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val attachment = noteAttachmentDao.getForNote(id).find { it.id == attachmentId }
                    if (attachment != null) {
                        // 删除本地文件
                        java.io.File(attachment.localPath).takeIf { it.exists() }?.delete()
                        // 删除数据库记录
                        noteAttachmentDao.delete(attachment)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (com.yy.writingwithai.BuildConfig.DEBUG) {
                        android.util.Log.e("DetailVM", "deleteAttachment failed", e)
                    }
                }
            }
        }
    }

    /** 把用户选中的文字手动加入 note_entities。 */
    fun addEntityFromSelection(surface: String, spanStart: Int, spanEnd: Int) {
        val id = noteId ?: return
        if (surface.isBlank()) return
        viewModelScope.launch {
            val entityKey = surface.trim().lowercase()
            val row = NoteEntityRow(
                noteId = id,
                entityType = com.yy.writingwithai.core.note.entity.EntityType.CONCEPT,
                entityKey = entityKey,
                surfaceForm = surface,
                spanStart = spanStart,
                spanEnd = spanEnd,
                lastExtractedAt = System.currentTimeMillis(),
                source = "USER_ADDED"
            )
            entityDao.upsertAll(listOf(row))
            // 刷新本地 entity 列表
            _entityRows.value = entityDao.getByNoteId(id)
            // 撤回：不再在 callback 后清选区，用户手动清除 selection 时 toolbar 自然消失
        }
    }

    // note-decompose-highlight · 拆解状态
    private val _decomposeState = MutableStateFlow<DecomposeState>(DecomposeState.Idle)
    val decomposeState: StateFlow<DecomposeState> = _decomposeState.asStateFlow()

    private val _entityRows = MutableStateFlow<List<NoteEntityRow>>(emptyList())
    val entityRows: StateFlow<List<NoteEntityRow>> = _entityRows.asStateFlow()

    // note-detail-polish: 关联笔记全量缓存,UI 层(详情页 + 实体卡片)复用同一份数据,
    // 避免 NoteLinker 多次 IO 查询。
    private val _related = MutableStateFlow<List<RelatedNote>>(emptyList())
    val related: StateFlow<List<RelatedNote>> = _related.asStateFlow()

    /** 加载关联笔记缓存（详情屏 + 实体卡片共享）。 */
    fun loadRelated() {
        val id = noteId ?: return
        viewModelScope.launch {
            _related.value = noteLinker.getRelated(id)
        }
    }

    /** M4 fix:获取指定实体的关联笔记（过滤 ENTITY_HIT + evidence 含 entityKey）。 */
    fun getRelatedByEntity(entityKey: String): List<RelatedNote> {
        return _related.value.filter {
            it.signals.contains(LinkType.ENTITY_HIT) &&
                it.evidence?.contains(entityKey) == true
        }
    }

    /** 加载已缓存的实体（进入详情页时调用）。 */
    fun loadCachedEntities() {
        val id = noteId ?: return
        viewModelScope.launch {
            // entity-management-and-ai-decompose §2.4:打开笔记时先做一次本地已有实体匹配
            // 不调 AI,纯 SQL LIKE/索引查询,影响忽略不计
            val current = repository.observeNoteWithTags(id).first()?.note ?: return@launch
            entityMatcher.matchAndPersist(id, current.content)
            val rows = entityDao.getByNoteId(id)
            // M5 fix:合并为单次更新，避免 _entityRows 和 _decomposeState 间出现不一致中间态
            _entityRows.value = rows
            _decomposeState.value = if (rows.isNotEmpty()) {
                DecomposeState.Decomposed(rows.size)
            } else {
                _decomposeState.value // 保持当前状态（Idle 或其他）
            }
            // note-detail-polish: 关联笔记同步加载,供详情页 + 实体卡片共用
            _related.value = noteLinker.getRelated(id)
        }
    }

    /** 把 DecomposeState 重置回 Idle(用于 ApiKeyMissing 弹窗关闭后)。 */
    fun resetDecomposeState() {
        if (_decomposeState.value is DecomposeState.ApiKeyMissing ||
            _decomposeState.value is DecomposeState.Error
        ) {
            _decomposeState.value = DecomposeState.Idle
        }
    }

    /** 触发拆解：先检查 API key 配置,再 AI 抽取实体 → 重算关联 → 刷新实体列表。 */
    fun decompose(forceReExtract: Boolean = false) {
        val id = noteId ?: return
        if (_decomposeState.value is DecomposeState.Loading) return
        viewModelScope.launch {
            // entity-management-and-ai-decompose §2.7:拆解前检查 API key
            val providers = secureApiKeyStore.observeConfiguredProviders().first()
            val hasProvider = providers.isNotEmpty() || com.yy.writingwithai.BuildConfig.DEBUG
            if (!hasProvider) {
                _decomposeState.value = DecomposeState.ApiKeyMissing
                return@launch
            }
            _decomposeState.value = DecomposeState.Loading
            try {
                // entity-management-and-ai-decompose §2.5:重新拆解先清掉旧实体
                if (forceReExtract) {
                    entityDao.deleteByNoteId(id)
                }
                val count = entityExtractor.extractAndPersist(id)
                noteLinker.recomputeForNote(id)
                // M5 fix:合并为单次更新，避免 _entityRows 和 _decomposeState 间出现不一致中间态
                val rows = entityDao.getByNoteId(id)
                _entityRows.value = rows
                // note-detail-polish: 拆解完刷新关联笔记缓存(新实体可能产生新 ENTITY_HIT 链接)
                _related.value = noteLinker.getRelated(id)
                _decomposeState.value = if (count > 0) {
                    DecomposeState.Decomposed(count)
                } else {
                    DecomposeState.Idle
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _decomposeState.value = DecomposeState.Error(mapToUserMessage(e))
            }
        }
    }
}

/** M3:详情屏顶部"上次 AI 操作"行投影 — `opKey` 是 aiwriting op 名("expand"/"polish"/"organize"),UI 层 `stringResource` 翻译。 */
data class AiMetaDisplay(val opKey: String, val opAt: String)

// H6 fix:SimpleDateFormat hoist 到 file-level lazy，避免每次 map { formatLocalDateTime(...) } 重建。
// fix-2026-06-26-review-r3 LOW:Locale.getDefault() 在某些 locale(阿拉伯/泰语)下产出非 ASCII 数字，
// 日期显示应 fallback 到 Locale.ROOT 保证可读性。
private val dateTimeFormat: SimpleDateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd HH:mm", safeLocale())
}

private fun formatLocalDateTime(epochMillis: Long): String {
    return dateTimeFormat.format(Date(epochMillis))
}

/**
 * fix-2026-06-26-review-r3 LOW:优先用用户 locale 格式化日期，但排除产出非 ASCII 数字的 locale
 * (阿拉伯/泰语/孟加拉等)，这些 locale 下日期对中文用户不可读，fallback 到 Locale.ROOT。
 */
private fun safeLocale(): Locale {
    val default = Locale.getDefault()
    val test = java.text.NumberFormat.getInstance(default).format(0)
    return if (test.all { it.isDigit() || it == '-' || it == ',' || it == '.' }) {
        default
    } else {
        Locale.ROOT
    }
}

/**
 * CR-FIX-M6 · 飞书 push/pull 同步结果的结构化 sealed 事件。
 *
 * - [Success] 携带 noteTitle + docUrl,UI 用 Snackbar 渲染,文案走 `feishu_sync_success_fmt`。
 * - [Failure] 嵌套 sealed,8 个子类型与 [FeishuError] 一一映射,UI 按子类型渲染不同 Dialog。
 *
 * 替代原 `String?` 同步消息 + 单一 `Failure(reason: String)`,UI 不再做字符串前缀嗅探或
 * 折叠成一行错误文案。
 */
sealed interface SyncMessage {
    data class Success(val noteTitle: String, val docUrl: String) : SyncMessage
    sealed interface Failure : SyncMessage {
        data class Conflict(val noteId: String, val docId: String, val docUrl: String) : Failure
        data class FolderMigration(
            val noteId: String,
            val docId: String,
            val docUrl: String,
            val currentFolderToken: String?,
            val refFolderToken: String?
        ) : Failure
        data class RemoteDeleted(val noteId: String, val docId: String, val docUrl: String) : Failure
        data object Empty : Failure
        data class Network(val detail: String) : Failure
        data class Server(val code: Int) : Failure
        data class RateLimited(val retryAfterSeconds: Int) : Failure
        data class Unknown(val cause: String) : Failure
    }
}

/**
 * D5 · feishu-sync-feedback · 上次同步动作记录。
 *
 * - [None]:还没同步过(或同步结束太久了已被消费),retry 是 no-op
 * - [PUSH]:上次 push(同步到飞书),retryLastSync() 重跑 pushToFeishu()
 * - [PULL]:上次 pull(从飞书拉取),docUrl 字段保留,retryLastSync() 重跑 pullFromFeishu(docUrl)
 */
sealed interface SyncAction {
    data object None : SyncAction
    data object PUSH : SyncAction
    data class PULL(val docUrl: String) : SyncAction
}

/** note-decompose-highlight · 拆解状态。 */
sealed interface DecomposeState {
    data object Idle : DecomposeState
    data object Loading : DecomposeState

    // entity-management-and-ai-decompose §2.7:未配置 API key 时进入此态,UI 弹错误对话框
    data object ApiKeyMissing : DecomposeState
    data class Decomposed(val entityCount: Int) : DecomposeState
    data class Error(val message: String) : DecomposeState
}
