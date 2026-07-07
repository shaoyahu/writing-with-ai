package com.yy.writingwithai.feature.feishuimport

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalSpacing
import com.yy.writingwithai.app.ui.theme.Spacing
import com.yy.writingwithai.core.feishu.sync.FeishuImportService
import com.yy.writingwithai.core.ui.NoteListSkeleton
import kotlinx.coroutines.launch

/**
 * feishu-import-from-folder · 从文件夹导入 sub-screen。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderImportScreen(onBack: () -> Unit, viewModel: FolderImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var selectedTokens by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()
    val spacing = LocalSpacing.current

    // feishu-import-from-folder:批量导入进度 + 结果 dialog
    var batchProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var batchSummary by remember { mutableStateOf<FeishuImportService.ImportSummary?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.folder_import_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val s = state) {
                is FolderImportViewModel.State.Input -> {
                    InputSection(
                        input = input,
                        onInputChange = { input = it },
                        error = null,
                        onParse = { viewModel.onParse(input) },
                        spacing = spacing
                    )
                }
                is FolderImportViewModel.State.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Column(
                            modifier = Modifier.padding(top = spacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(spacing.md))
                            Text(
                                stringResource(R.string.folder_import_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    LazyColumn(contentPadding = PaddingValues(spacing.md)) {
                        items(5) { NoteListSkeleton() }
                    }
                }
                is FolderImportViewModel.State.Loaded -> {
                    LoadedSection(
                        docs = s.docs,
                        selectedTokens = selectedTokens,
                        onToggle = { token ->
                            selectedTokens = if (token in selectedTokens) {
                                selectedTokens - token
                            } else {
                                selectedTokens + token
                            }
                        },
                        onSelectAll = {
                            // docs 为空时 selectedTokens.size == docs.size 恒为 true(都=0),
                            // 会误判为"全部已选"。加 docs.isNotEmpty() 保护。
                            selectedTokens =
                                if (s.docs.isNotEmpty() && selectedTokens.size == s.docs.size) {
                                    emptySet()
                                } else {
                                    s.docs.map { it.token }.toSet()
                                }
                        },
                        onImport = {
                            scope.launch {
                                val tokens = selectedTokens.toList()
                                batchProgress = 0 to tokens.size
                                val summary = viewModel.importSelected(
                                    tokens = tokens,
                                    folderToken = input,
                                    onProgress = { done, total -> batchProgress = done to total }
                                )
                                batchProgress = null
                                batchSummary = summary
                            }
                        },
                        onRetry = {
                            viewModel.resetToInput()
                            selectedTokens = emptySet()
                        },
                        spacing = spacing
                    )
                }
                is FolderImportViewModel.State.Error -> {
                    InputSection(
                        input = input,
                        onInputChange = { input = it },
                        error = s.message,
                        onParse = { viewModel.onParse(input) },
                        spacing = spacing
                    )
                }
            }

            // feishu-import-from-folder:批量导入进度 dialog
            batchProgress?.let { (done, total) ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {},
                    title = { Text(stringResource(R.string.quicknote_list_import_batch_loading_fmt, done, total)) },
                    text = {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    },
                    confirmButton = {}
                )
            }

            // feishu-import-from-folder:结果 dialog(仅显示失败数量)
            // review 2026-07-07 Finding #11:原硬编码中文 → 走 strings.xml
            batchSummary?.let { summary ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { batchSummary = null },
                    title = { Text(stringResource(R.string.quicknote_list_import_batch_result_title)) },
                    text = {
                        Text(
                            // 三段格式(成功 X / 部分失败 Y / 失败 Z)直接复用已有 batch_result_fmt
                            stringResource(
                                R.string.quicknote_list_import_batch_result_fmt,
                                summary.successCount,
                                summary.partialCount,
                                summary.failureCount
                            )
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            batchSummary = null
                            onBack()
                        }) {
                            Text(stringResource(R.string.quicknote_list_import_batch_result_ok))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun InputSection(
    input: String,
    onInputChange: (String) -> Unit,
    error: String?,
    onParse: () -> Unit,
    spacing: Spacing
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.md),
        verticalArrangement = Arrangement.Top
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            label = { Text(stringResource(R.string.folder_import_input_hint)) },
            singleLine = true,
            isError = error != null,
            trailingIcon = {
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                IconButton(onClick = {
                    val clip = clipboard.getText()?.text.orEmpty()
                    if (clip.isNotBlank()) onInputChange(clip)
                }) {
                    Icon(
                        Icons.Filled.ContentPaste,
                        contentDescription = stringResource(R.string.folder_import_paste)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Spacer(Modifier.height(spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.size(spacing.xs))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.height(spacing.md))
        Button(
            onClick = onParse,
            enabled = input.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.folder_import_parse_button))
        }
    }
}

@Composable
private fun LoadedSection(
    docs: List<FeishuImportService.DocSummary>,
    selectedTokens: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onImport: () -> Unit,
    onRetry: () -> Unit,
    spacing: Spacing
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.folder_import_chip_docx_only, docs.size),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = onSelectAll) {
                    Text(
                        if (docs.isNotEmpty() && selectedTokens.size == docs.size) {
                            stringResource(R.string.folder_import_deselect_all)
                        } else {
                            stringResource(R.string.folder_import_select_all)
                        }
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = spacing.sm)
        ) {
            items(items = docs, key = { it.token }) { doc ->
                DocRow(
                    title = doc.title,
                    subtitle = doc.url ?: doc.token,
                    checked = doc.token in selectedTokens,
                    onToggle = { onToggle(doc.token) }
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(R.string.folder_import_retry_folder))
                }
                Button(
                    onClick = onImport,
                    enabled = selectedTokens.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.folder_import_import_button_fmt, selectedTokens.size))
                }
            }
        }
    }
}

@Composable
private fun DocRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .background(
                if (checked) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Icon(
            Icons.Filled.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.ifBlank { stringResource(R.string.folder_import_no_title) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
