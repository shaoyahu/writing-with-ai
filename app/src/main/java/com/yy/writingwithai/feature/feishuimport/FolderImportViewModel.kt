package com.yy.writingwithai.feature.feishuimport

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.R
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

        /**
         * fix M36 (full-review):错误用 @StringRes + 参数,不在 VM 里硬编码中文。
         * VM 没 Context 拿不到 stringResource,旧版只能塞中文 literal 进 State,
         * i18n / 测试都不能覆盖。Screen 端用 stringResource(id, *args) 渲染。
         * 保留 rawMessage 给未知 exception(i18n 管不到的服务端错误)。
         */
        data class Error(
            @StringRes val resId: Int,
            val args: List<Any> = emptyList(),
            val rawMessage: String? = null
        ) : State()
    }

    private val _state = MutableStateFlow<State>(State.Input)
    val state: StateFlow<State> = _state.asStateFlow()

    fun onParse(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            _state.value = State.Error(R.string.folder_import_error_empty_input)
            return
        }
        _state.value = State.Loading
        viewModelScope.launch {
            val result = importService.listFolderDocs(trimmed)
            _state.value = result.fold(
                onSuccess = { docs ->
                    if (docs.isEmpty()) {
                        State.Error(R.string.folder_import_error_no_docs)
                    } else {
                        State.Loaded(docs)
                    }
                },
                onFailure = { e ->
                    val msg = e.message?.takeIf { it.isNotBlank() }
                    if (msg != null) {
                        State.Error(
                            resId = R.string.folder_import_error_parse_failed,
                            args = listOf(msg),
                            rawMessage = msg
                        )
                    } else {
                        State.Error(R.string.folder_import_error_parse_failed_unknown)
                    }
                }
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
