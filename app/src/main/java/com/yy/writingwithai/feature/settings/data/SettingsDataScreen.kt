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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDataScreen(onBack: () -> Unit, viewModel: SettingsDataViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
        }
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
                            val ts =
                                SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
                            exportLauncher.launch("writing-with-ai-export-$ts.zip")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.settings_data_export)) }
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
