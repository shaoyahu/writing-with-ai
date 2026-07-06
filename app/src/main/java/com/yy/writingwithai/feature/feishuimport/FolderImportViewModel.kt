package com.yy.writingwithai.feature.feishuimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.feishu.sync.FeishuImportService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * feishu-import-from-folder · 从文件夹导入 sub-screen ViewModel。
 */
@HiltViewModel
class FolderImportViewModel
@Inject
constructor(
    private val importService: FeishuImportService
) : ViewModel() {

    sealed class State {
        data object Input : State()
        data object Loading : State()
        data class Loaded(val docs: List<FeishuImportService.DocSummary>) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Input)
    val state: StateFlow<State> = _state.asStateFlow()

    fun onParse(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            _state.value = State.Error("请输入文件夹链接或 token")
            return
        }
        _state.value = State.Loading
        viewModelScope.launch {
            val result = importService.listFolderDocs(trimmed)
            _state.value = result.fold(
                onSuccess = { docs ->
                    if (docs.isEmpty()) State.Error("未找到 docx 文档") else State.Loaded(docs)
                },
                onFailure = { e -> State.Error(e.message ?: "解析失败") }
            )
        }
    }

    suspend fun importSelected(
        tokens: List<String>,
        folderToken: String,
        onProgress: (Int, Int) -> Unit
    ): FeishuImportService.ImportSummary {
        return importService.importFolderDocs(
            folderToken = folderToken,
            docTokens = tokens,
            onProgress = onProgress
        )
    }

    fun resetToInput() {
        _state.value = State.Input
    }
}
