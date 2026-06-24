@file:Suppress("FunctionNaming")

package com.yy.writingwithai.feature.my

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.BuildConfig
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.WritingAppTheme
import com.yy.writingwithai.core.update.UpdateError

/**
 * app-self-hosted-update · 「我的」→「关于」屏。
 *
 * 顶部「检查更新」按钮触发 [AboutViewModel.checkForUpdate];有新版时弹 [UpdateDialog]。
 * 也显示当前 app 版本号(从 BuildConfig)。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, viewModel: AboutViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.about_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 版本信息卡
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.about_current_version), style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            // 检查更新按钮 + 状态文案
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.about_check_update_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.about_check_update_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { viewModel.checkForUpdate(BuildConfig.VERSION_CODE) },
                        enabled = state !is AboutUiState.Checking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            when (state) {
                                is AboutUiState.Checking -> stringResource(R.string.about_checking)
                                else -> stringResource(R.string.about_check_update)
                            }
                        )
                    }
                    // 状态文案(非 Available 时,UI 反馈入口)
                    StateFeedback(state = state)
                }
            }

            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.common_back))
            }
        }

        // 有新版时弹 dialog
        if (state is AboutUiState.Available) {
            val available = state as AboutUiState.Available
            UpdateDialog(
                manifest = available.manifest,
                onDownload = { viewModel.startDownload() },
                onLater = { viewModel.dismissDialog() }
            )
        }
    }

    // 一次性副作用:UpToDate / Failed → 弹 Snackbar,然后回到 Idle
    LaunchedEffect(state) {
        when (val s = state) {
            is AboutUiState.UpToDate -> {
                snackbarHostState.showSnackbar("已是最新 v${s.remoteVersionName}")
                viewModel.resetToIdle()
            }
            is AboutUiState.Failed -> {
                snackbarHostState.showSnackbar(failedMessage(s.error))
                viewModel.resetToIdle()
            }
            else -> Unit
        }
    }
}

@Composable
private fun StateFeedback(state: AboutUiState) {
    when (state) {
        is AboutUiState.Downloading -> Text(
            "下载中... v${state.versionName},系统通知栏查看进度",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        is AboutUiState.Available -> Text(
            "发现新版本 v${state.manifest.versionName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        else -> Unit
    }
}

private fun failedMessage(err: UpdateError): String = when (err) {
    is UpdateError.Network -> "检查失败:网络异常"
    is UpdateError.Http -> "检查失败:服务端错误(${err.code})"
    is UpdateError.Parse -> "检查失败:响应格式错误"
    is UpdateError.ChecksumMismatch -> "下载文件损坏,请重试"
}

@androidx.compose.ui.tooling.preview.Preview(name = "About", showBackground = true)
@Composable
private fun AboutScreenPreview() {
    WritingAppTheme {
        AboutScreen(onBack = {})
    }
}
