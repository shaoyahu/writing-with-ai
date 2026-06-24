package com.yy.writingwithai.feature.my

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.update.ApkDownloader
import com.yy.writingwithai.core.update.AppUpdateChecker
import com.yy.writingwithai.core.update.AppUpdateManifest
import com.yy.writingwithai.core.update.UpdateError
import com.yy.writingwithai.core.update.UpdateManifestStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * app-self-hosted-update · 「我的」→「关于」状态机。
 *
 * 状态:
 * - Idle:无操作
 * - Checking:正在拉 manifest
 * - Available:有新版(remote.versionCode > local),带 manifest
 * - UpToDate:远 versionCode <= 本地
 * - Downloading:DownloadManager enqueue 中(持有 downloadId)
 * - Failed:网络/解析/HTTP 错(带 UpdateError)
 */
@HiltViewModel
class AboutViewModel @Inject constructor(
    private val checker: AppUpdateChecker,
    private val downloader: ApkDownloader,
    private val manifestStore: UpdateManifestStore
) : ViewModel() {

    private val _state = MutableStateFlow<AboutUiState>(AboutUiState.Idle)
    val state: StateFlow<AboutUiState> = _state.asStateFlow()

    fun checkForUpdate(localVersionCode: Int) {
        if (_state.value is AboutUiState.Checking) return
        _state.value = AboutUiState.Checking
        viewModelScope.launch {
            checker.fetch().fold(
                onSuccess = { manifest ->
                    _state.update {
                        if (manifest.versionCode > localVersionCode) {
                            AboutUiState.Available(manifest)
                        } else {
                            AboutUiState.UpToDate(manifest.versionName)
                        }
                    }
                },
                onFailure = { e ->
                    val err = e as? UpdateError ?: UpdateError.Network(e)
                    _state.value = AboutUiState.Failed(err)
                }
            )
        }
    }

    fun startDownload() {
        val available = _state.value as? AboutUiState.Available ?: return
        val manifest = available.manifest
        val downloadId = downloader.start(manifest)
        manifestStore.put(downloadId, manifest)
        _state.value = AboutUiState.Downloading(downloadId, manifest.versionName)
    }

    fun dismissDialog() {
        // 从 Available / Downloading / Failed 回到 Idle
        _state.value = AboutUiState.Idle
    }

    fun resetToIdle() {
        _state.value = AboutUiState.Idle
    }
}

sealed class AboutUiState {
    data object Idle : AboutUiState()
    data object Checking : AboutUiState()
    data class Available(val manifest: AppUpdateManifest) : AboutUiState()
    data class UpToDate(val remoteVersionName: String) : AboutUiState()
    data class Downloading(val downloadId: Long, val versionName: String) : AboutUiState()
    data class Failed(val error: UpdateError) : AboutUiState()
}
