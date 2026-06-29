package com.yy.writingwithai.feature.my

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.BuildConfig
import com.yy.writingwithai.core.update.AppUpdateChecker
import com.yy.writingwithai.core.update.AppUpdateManifest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ux-2026-06-28 #7:关于页「检查更新」可点击 — 调用 `AppUpdateChecker.fetch()` 拉远端 manifest,
 * 与 `BuildConfig.VERSION_CODE` 比对。
 *
 * 三种结果:
 * - `UpToDate`:远端 versionCode <= 本地,提示「已是最新 vX」即可
 * - `UpdateAvailable`:弹 dialog,展示新版本 versionName + releaseNotes;下载走现有 ApkDownloader(spec)
 * - `Failed`:网络/解析/HTTP 错误统一提示「检查失败,稍后重试」
 *
 * 消费方:`MyScreen` 收集 state,Snackbar 提示 UpToDate/Failed,AlertDialog 提示 UpdateAvailable。
 */
@HiltViewModel
class CheckUpdateViewModel
@Inject
constructor(
    private val appUpdateChecker: AppUpdateChecker
) : ViewModel() {
    private val _state = MutableStateFlow<CheckUpdateState>(CheckUpdateState.Idle)
    val state: StateFlow<CheckUpdateState> = _state.asStateFlow()

    fun check() {
        viewModelScope.launch {
            _state.value = CheckUpdateState.Checking
            val result = appUpdateChecker.fetch()
            _state.value = result.fold(
                onSuccess = { manifest ->
                    if (manifest.versionCode > BuildConfig.VERSION_CODE) {
                        CheckUpdateState.UpdateAvailable(manifest)
                    } else {
                        CheckUpdateState.UpToDate(
                            // 已是最新时显示本机 versionName(用户更熟悉)
                            BuildConfig.VERSION_NAME
                        )
                    }
                },
                onFailure = { CheckUpdateState.Failed }
            )
        }
    }

    /** dialog 关闭 / snackbar 消费后清回 Idle,避免重组时再触发一次。 */
    fun consume() {
        _state.value = CheckUpdateState.Idle
    }
}

sealed interface CheckUpdateState {
    data object Idle : CheckUpdateState
    data object Checking : CheckUpdateState
    data class UpToDate(val localVersion: String) : CheckUpdateState
    data class UpdateAvailable(val manifest: AppUpdateManifest) : CheckUpdateState
    data object Failed : CheckUpdateState
}
