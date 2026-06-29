package com.yy.writingwithai.feature.quicknote.edit

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.data.db.dao.NoteAttachmentDao
import com.yy.writingwithai.core.data.db.entity.NoteAttachmentEntity
import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.core.media.AttachmentStore
import com.yy.writingwithai.core.media.ImageCompressor
import com.yy.writingwithai.feature.quicknote.model.NoteEditorUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class QuickNoteEditorViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val repository: NoteRepository,
    private val noteAttachmentDao: NoteAttachmentDao,
    private val attachmentStore: AttachmentStore,
    private val imageCompressor: ImageCompressor
) : ViewModel() {
    private val routeId: String? = savedStateHandle.get<String>("id")
    private val isNew: Boolean = routeId.isNullOrBlank() || routeId == NEW_SENTINEL
    private val noteId: String = if (isNew) UUID.randomUUID().toString() else routeId!!

    private val titleFlow = MutableStateFlow("")
    private val contentFlow = MutableStateFlow("")
    private val tagsFlow = MutableStateFlow<List<String>>(emptyList())
    private val tagInputFlow = MutableStateFlow("")
    private val savingFlow = MutableStateFlow(false)
    private val loadedFlow = MutableStateFlow(false)

    // ux-2026-06-28 #8:新建笔记时 noteId 是 random UUID,note 行尚未入库,
    // NoteAttachmentEntity 有 FK → 必须等 repository.upsert(note) 成功后才能 insert 附件。
    // 这里只缓存 Uri;save() 内 upsert 后再压缩 + 写库(详见 save 末尾)。
    private val pendingAttachmentUrisFlow = MutableStateFlow<List<android.net.Uri>>(emptyList())

    init {
        if (!isNew) {
            viewModelScope.launch {
                // H2 修:记录用户是否已抢先输入;若是,init 不回填,避免覆盖。
                val hadUserInput =
                    titleFlow.value.isNotEmpty() ||
                        contentFlow.value.isNotEmpty() ||
                        tagsFlow.value.isNotEmpty()
                val existing = repository.getNote(noteId)
                if (existing != null && !hadUserInput) {
                    titleFlow.value = existing.title
                    contentFlow.value = existing.content
                    // H1 修:用 .first() 一次性读 tags,不再持续订阅导致覆盖用户编辑。
                    val item = repository.observeNoteWithTags(existing.id).first()
                    if (item != null) tagsFlow.value = item.tags
                }
                loadedFlow.value = true
            }
        } else {
            loadedFlow.value = true
        }
    }

    // combine 最多 5 个 Flow,6 个时嵌套一层
    private data class PartialState(
        val title: String,
        val content: String,
        val tags: List<String>,
        val tagInput: String,
        val saving: Boolean
    )

    val uiState: StateFlow<NoteEditorUiState> =
        combine(
            combine(titleFlow, contentFlow, tagsFlow, tagInputFlow, savingFlow) { t, c, ts, ti, s ->
                PartialState(t, c, ts, ti, s)
            },
            loadedFlow
        ) { p, loaded ->
            NoteEditorUiState(
                isNew = isNew,
                title = p.title,
                content = p.content,
                tags = p.tags,
                tagInputText = p.tagInput,
                isSaving = p.saving,
                isLoaded = loaded
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue =
            NoteEditorUiState(
                isNew = isNew,
                title = "",
                content = "",
                tags = emptyList(),
                tagInputText = "",
                isLoaded = false
            )
        )

    fun setTitle(s: String) {
        titleFlow.update { s }
    }

    fun setContent(s: String) {
        contentFlow.update { s }
    }

    // fix-quicknote-tags-and-search · "已挂 #a #b" 副文案用
    val tagsSummary: StateFlow<String> =
        tagsFlow
            .map { list -> list.joinToString(" ") { "#$it" } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = ""
            )

    fun addTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return
        tagsFlow.update { current -> if (trimmed in current) current else current + trimmed }
    }

    fun removeTag(tag: String) {
        tagsFlow.update { current -> current.filterNot { it == tag } }
    }

    fun setTagInput(value: String) {
        tagInputFlow.update { value }
    }

    // ux-2026-06-28 #8:暂存待处理的图片 Uri;save() 内 upsert 后才压缩 + insert。
    fun addAttachment(uri: android.net.Uri) {
        pendingAttachmentUrisFlow.update { current -> current + uri }
    }

    fun removePendingAttachment(uri: android.net.Uri) {
        pendingAttachmentUrisFlow.update { current -> current.filterNot { it == uri } }
    }

    /** ux-2026-06-28 #8:暴露给 UI 渲染待处理列表(新建笔记时还没落库,只能展示计数 + 移除)。 */
    val pendingAttachmentUris: StateFlow<List<android.net.Uri>> = pendingAttachmentUrisFlow.asStateFlow()

    /** ux-2026-06-28 #8:已存在的笔记(走路由 id)直接 observe;新建笔记没附件行,返回空列表。 */
    fun observeAttachments(): kotlinx.coroutines.flow.Flow<List<NoteAttachmentEntity>> =
        noteAttachmentDao.observeForNote(noteId)

    fun save(onSaved: (id: String) -> Unit) {
        if (savingFlow.value) return
        // 先消费 TagInputRow 中待提交的输入文本(用户未按逗号/回车就直接点保存的情况)
        val pendingTag = tagInputFlow.value.trim()
        if (pendingTag.isNotEmpty()) {
            addTag(pendingTag)
            tagInputFlow.update { "" }
        }
        savingFlow.update { true }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = if (isNew) null else repository.getNote(noteId)
            val note =
                (
                    existing ?: Note(
                        id = noteId,
                        title = "",
                        content = "",
                        createdAt = now,
                        updatedAt = now,
                        isPinned = false,
                        lastAiOp = null,
                        lastAiAt = null
                    )
                    ).copy(
                    title = titleFlow.value,
                    content = contentFlow.value,
                    updatedAt = now
                )
            val tagsToSave = tagsFlow.value
            // fix-2026-06-26-review-r3 H26:删 debug 日志。原 log 把 note.id(用户 UUID)+ tags
            // 打到 logcat,在 debug 包也被 ADB / 抓 log 工具捕获,违反 CLAUDE.md "不放用户
            // 数据到 logcat"原则。后续要排查 EditorVM 用 Android Studio debugger,不开 log。
            repository.upsert(note, tagsToSave)
            // ux-2026-06-28 #8:upsert 成功 → note 行已落库,FK 满足,现在可以 commit 待处理附件。
            // 任一 Uri 失败不阻断其它;失败记 debug 日志(用户 UUID 仅在 DEBUG 包出现,符合 CLAUDE.md)。
            val pending = pendingAttachmentUrisFlow.value
            if (pending.isNotEmpty()) {
                pendingAttachmentUrisFlow.value = emptyList()
                for (uri in pending) {
                    commitAttachment(uri, noteId)
                }
            }
            savingFlow.update { false }
            onSaved(note.id)
        }
    }

    /**
     * ux-2026-06-28 #8:把单张图片 Uri 压缩 + 写入附件存储 + insert NoteAttachmentEntity。
     * 复用 [QuickNoteDetailViewModel.addAttachment] 的实现策略,确保行为一致。
     * 失败时只在 DEBUG 包 Log.e,避免 release 包污染 logcat。
     */
    private fun commitAttachment(uri: android.net.Uri, noteId: String) {
        viewModelScope.launch {
            var sourceFile: java.io.File? = null
            try {
                val attachmentId = UUID.randomUUID().toString()
                sourceFile = java.io.File(appContext.cacheDir, "tmp_$attachmentId.jpg")
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    sourceFile!!.outputStream().use { output -> input.copyTo(output) }
                } ?: return@launch
                val destFile = attachmentStore.getAttachmentFile(noteId, attachmentId, "jpg")
                imageCompressor.compress(sourceFile, destFile)
                noteAttachmentDao.insert(
                    NoteAttachmentEntity(
                        id = attachmentId,
                        noteId = noteId,
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
                    android.util.Log.e("EditorVM", "commitAttachment failed", e)
                }
            } finally {
                sourceFile?.takeIf { it.exists() }?.delete()
            }
        }
    }

    private companion object {
        const val NEW_SENTINEL = "NEW"
    }
}
