package com.yy.writingwithai.feature.settings.data

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * fix-2026-06-26-review-r3 M2:SimpleDateFormat 提到顶层共享单例,避免每次重组 / 按钮 click
 * 重新构造。原实现内嵌在 `onClick` lambda 内,虽然 lambda 只在 click 触发时执行,但两个按钮
 * 各自 new 一份独立对象,格式化样式共享本应一份。
 */
private val FILE_TS_FORMAT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDataScreen(onBack: () -> Unit, viewModel: SettingsDataViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ctx = LocalContext.current // review r1 H3 修:snackbar 文案走 strings.xml 需 Context
    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri: Uri? ->
            uri?.let { viewModel.exportToJsonZip(it) }
        }
    val importLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            uri?.let { viewModel.importFromZip(it) }
        }
    val saveReportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri: Uri? ->
            uri?.let { viewModel.saveImportReport(it) }
        }
    val saveResult by viewModel.lastSaveReportResult.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(saveResult) {
        when (val r = saveResult) {
            SaveReportResult.Idle -> Unit
            SaveReportResult.Success -> {
                // review r1 H3 修:snackbar 文案改走 strings.xml(已存在的 key),不再硬编码中文。
                snackbarHostState.showSnackbar(message = ctx.getString(R.string.settings_data_report_saved))
                viewModel.resetSaveReportResult()
            }
            is SaveReportResult.Failed -> {
                snackbarHostState.showSnackbar(message = ctx.getString(R.string.settings_data_save_failed, r.reason))
                viewModel.resetSaveReportResult()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_data_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            when (val s = state) {
                DataUiState.Idle -> {
                    // H2 修:r1 review。导出按钮在 notesCount == 0 时置灰 + 显示 no_data 文案。
                    val notesCount by viewModel.notesCount.collectAsState()
                    val canExport = notesCount > 0
                    Button(
                        enabled = canExport,
                        onClick = {
                            val ts = FILE_TS_FORMAT.format(Date())
                            exportLauncher.launch("writing-with-ai-export-$ts.zip")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.me_data_export_all)) }
                    if (!canExport) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.settings_data_no_data),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // M4 修:r1 review。Idle 之外状态按钮置灰,避免用户重复触发。
                    Button(
                        enabled = true,
                        onClick = { importLauncher.launch(arrayOf("application/zip")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.settings_data_import)) }
                }
                DataUiState.Exporting -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.settings_data_exporting))
                }
                DataUiState.Importing -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.settings_data_importing))
                }
                is DataUiState.Done -> {
                    // L4 修:r1 review。Done 分导出 / 导入:
                    // - 导出(导出按钮触发的 Done):仅显示成功条数
                    // - 导入:显示三项计数(success / skipped / failed)+ 可选失败详情
                    val summaryText =
                        if (s.isImport) {
                            stringResource(
                                R.string.import_report_summary,
                                s.report.successCount,
                                s.report.skippedCount,
                                s.report.failedCount
                            )
                        } else {
                            stringResource(R.string.settings_data_done, s.report.successCount)
                        }
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (s.report.failedCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text =
                            stringResource(
                                R.string.settings_data_failed,
                                s.report.failedNotes
                                    .joinToString("; ") { it.title.ifBlank { it.noteId } }
                            ),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    // last-import-report-save · 导入完成后暴露报告 zip 保存入口。
                    // 仅在 isImport == true 显示(导出 Done 无报告);缓存 null(VM cleared)置灰。
                    if (s.isImport) {
                        val canSave = viewModel.lastImportReportZipBytes != null
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            enabled = canSave,
                            onClick = {
                                val ts = FILE_TS_FORMAT.format(Date())
                                saveReportLauncher.launch("import-report-$ts.zip")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.settings_data_save_report)) }
                        if (!canSave) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.settings_data_no_report),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
                is DataUiState.Failed -> {
                    Text(
                        text = stringResource(R.string.settings_data_failed, s.error),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
