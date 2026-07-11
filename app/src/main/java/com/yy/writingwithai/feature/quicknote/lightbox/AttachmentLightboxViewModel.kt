package com.yy.writingwithai.feature.quicknote.lightbox

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.data.db.dao.NoteAttachmentDao
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * fix-review-r1 F2:全屏附件 lightbox 的 ViewModel。
 *
 * - 从 [NoteAttachmentDao.observeById] 取一次 attachment 元数据(Flow.first());
 * - 在 IO 上验证 [java.io.File.exists] + [java.io.File.canRead],确认文件可读;
 * - 暴露 [LightboxState] 给 UI。
 */
@HiltViewModel
class AttachmentLightboxViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val noteAttachmentDao: NoteAttachmentDao
) : ViewModel() {

    private val attachmentId: String =
        savedStateHandle.get<String>(ATTACHMENT_ID_ARG).orEmpty()

    private val _state = MutableStateFlow<LightboxState>(LightboxState.Loading)
    val state: StateFlow<LightboxState> = _state.asStateFlow()

    init {
        if (attachmentId.isBlank()) {
            _state.value = LightboxState.NotFound
        } else {
            load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            val entity = withContext(Dispatchers.IO) {
                runCatching { noteAttachmentDao.observeById(attachmentId).first() }
                    .getOrNull()
            }
            if (entity == null || entity.id.isBlank()) {
                _state.value = LightboxState.NotFound
                return@launch
            }
            val file = withContext(Dispatchers.IO) {
                File(entity.localPath).takeIf { it.exists() && it.isFile && it.canRead() }
            }
            _state.value = LightboxState.Ready(
                attachmentId = entity.id,
                localPath = entity.localPath,
                displayName = File(entity.localPath).name,
                mimeType = entity.mimeType,
                fileSizeBytes = file?.length() ?: entity.fileSize,
                file = file
            )
        }
    }

    companion object {
        /** 与 `AppNav.kt` route path arg 名称一致。 */
        const val ATTACHMENT_ID_ARG = "attachmentId"
    }
}

sealed class LightboxState {
    data object Loading : LightboxState()
    data object NotFound : LightboxState()
    data class Ready(
        val attachmentId: String,
        val localPath: String,
        val displayName: String,
        val mimeType: String,
        val fileSizeBytes: Long,
        val file: File?
    ) : LightboxState()
}
