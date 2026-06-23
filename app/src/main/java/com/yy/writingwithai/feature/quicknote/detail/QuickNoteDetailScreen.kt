package com.yy.writingwithai.feature.quicknote.detail

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.yy.writingwithai.R
import com.yy.writingwithai.app.QuicknoteDetail
import com.yy.writingwithai.app.ui.theme.LocalSpacing
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.core.feishu.sync.FeishuRefStatus
import com.yy.writingwithai.core.note.NoteLinker
import com.yy.writingwithai.core.prefs.ConsentState
import com.yy.writingwithai.core.prefs.ConsentStore
import com.yy.writingwithai.feature.aiwriting.AiActionUiState
import com.yy.writingwithai.feature.aiwriting.AiActionViewModel
import com.yy.writingwithai.feature.aiwriting.AiwritingEntry
import com.yy.writingwithai.feature.aiwriting.streaming.AiActionUiState.Idle
import com.yy.writingwithai.feature.quicknote.model.NoteDetailUiState
import com.yy.writingwithai.feature.quicknote.share.shareNoteMarkdown
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * note-association:详情屏用的 Hilt EntryPoint(ConsentStore + NoteLinker)。
 */
@EntryPoint
@InstallIn(ActivityComponent::class)
internal interface DetailScreenEntryPoint {
    fun consentStore(): ConsentStore
    fun noteLinker(): NoteLinker
    fun noteAssociationSettings(): com.yy.writingwithai.core.prefs.NoteAssociationSettingsStore
    fun llmExtractor(): com.yy.writingwithai.core.note.impl.LlmNoteLinkExtractor?
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickNoteDetailScreen(
    onBack: () -> Unit,
    onEdit: (id: String) -> Unit,
    onDeleted: () -> Unit,
    navController: NavController,
    viewModel: QuickNoteDetailViewModel = hiltViewModel()
) {
    // M4-4:详情屏 consent 闸门(UI 层二次防御,与 ViewModel.start() 一致)
    val context = androidx.compose.ui.platform.LocalContext.current
    val consentStore =
        androidx.compose.runtime.remember(context) {
            EntryPointAccessors.fromActivity(
                context as android.app.Activity,
                DetailScreenEntryPoint::class.java
            ).consentStore()
        }
    val consentFlow by consentStore.consentFlow.collectAsStateWithLifecycle(initialValue = ConsentState.EMPTY)
    val detailEntry = remember(context) {
        EntryPointAccessors.fromActivity(
            context as android.app.Activity,
            DetailScreenEntryPoint::class.java
        )
    }
    val noteLinker = detailEntry.noteLinker()
    val noteAssocSettings = detailEntry.noteAssociationSettings()
    val showAiButton = noteAssocSettings.isEnabled()
    val state by viewModel.uiState.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val fabState by viewModel.fabState.collectAsStateWithLifecycle()
    val aiMeta by viewModel.aiMetaDisplay.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val syncLoading by viewModel.syncLoading.collectAsStateWithLifecycle()
    val feishuRef by viewModel.feishuRef.collectAsStateWithLifecycle()
    val showConflictDialog by viewModel.showConflictDialog.collectAsStateWithLifecycle()

    var showPullDialog by remember { mutableStateOf(false) }
    var pullUrlInput by remember { mutableStateOf("") }
    var showSyncMessageDialog by remember { mutableStateOf(false) }

    val current = state as? NoteDetailUiState.Content
    val noteId = current?.note?.note?.id
    // H2 修:noteId 真存在时才挂 AiActionViewModel,避免 NotFound / saved-state 缺失时残留 FAB/Sheet。
    val aiVm: AiActionViewModel? =
        if (noteId != null) {
            AiwritingEntry.rememberAiActionViewModel(noteId)
        } else {
            null
        }
    // H1 修:`by` 委托订阅 StateFlow,而不是 `.value` 快照读(后者不触发重组,Sheet 永不显示)。
    // aiVm null 时 fallback 到 remember 住的 Idle 流,StreamingPanel 走 return 不渲染。
    val aiStateFlow: StateFlow<AiActionUiState> =
        aiVm?.state ?: remember { MutableStateFlow(Idle) }
    val aiState: AiActionUiState by aiStateFlow.collectAsStateWithLifecycle()

    var actionMenuOpen by remember { mutableStateOf(false) }

    // SAF launcher for exporting single note as Markdown
    val exportMdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val note = (state as? NoteDetailUiState.Content)?.note?.note ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                val md = buildString {
                    if (note.title.isNotBlank()) {
                        append("# ")
                        append(note.title)
                        append("\n\n")
                    }
                    append(note.content)
                }
                os.write(md.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(context, context.getString(R.string.quicknote_export_success), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.quicknote_export_error), Toast.LENGTH_SHORT).show()
        }
    }

    // SAF launcher for exporting single note as plain text
    val exportTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val note = (state as? NoteDetailUiState.Content)?.note?.note ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                val text = buildString {
                    if (note.title.isNotBlank()) {
                        append(note.title)
                        append("\n\n")
                    }
                    append(note.content)
                }
                os.write(text.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(context, context.getString(R.string.quicknote_export_success), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.quicknote_export_error), Toast.LENGTH_SHORT).show()
        }
    }
    // H3 修:TextFieldValue 仅在 noteId 变化时重建,避免 content / wordCount / tags 任一变化重置选区。
    // selection 不从 ViewModel 反向同步回 BasicTextField(单向 BasicTextField → ViewModel)。
    // AI replace 刷新:LaunchedEffect 同步 content 变化到 textFieldValue(keep selection)。
    var textFieldValue by remember(noteId) {
        mutableStateOf(
            TextFieldValue(text = current?.note?.note?.content.orEmpty())
        )
    }
    val syncContent = (state as? NoteDetailUiState.Content)?.note?.note?.content
    LaunchedEffect(syncContent) {
        val newText = syncContent ?: return@LaunchedEffect
        if (textFieldValue.text != newText) {
            textFieldValue = textFieldValue.copy(text = newText)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quicknote_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (current != null) {
                        IconButton(onClick = { onEdit(current.note.note.id) }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.quicknote_detail_edit)
                            )
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (current.note.note.isPinned) {
                                            stringResource(R.string.quicknote_detail_unpin)
                                        } else {
                                            stringResource(R.string.quicknote_detail_pin)
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (current.note.note.isPinned) {
                                            Icons.Filled.PushPin
                                        } else {
                                            Icons.Outlined.PushPin
                                        },
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.togglePinned()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.quicknote_detail_share)) },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    context.shareNoteMarkdown(current.note.note)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.quicknote_detail_export_md)) },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    val note = current.note.note
                                    val name = note.title.ifBlank { note.id } + ".md"
                                    exportMdLauncher.launch(name)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.quicknote_detail_export_txt)) },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    val note = current.note.note
                                    val name = note.title.ifBlank { note.id } + ".txt"
                                    exportTxtLauncher.launch(name)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.quicknote_detail_delete)) },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    confirmDelete = true
                                }
                            )
                            // feishu-bidir-sync:同步菜单
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.quicknote_detail_sync_to_feishu)) },
                                leadingIcon = { Icon(Icons.Filled.Cloud, contentDescription = null) },
                                enabled = !syncLoading,
                                onClick = {
                                    menuExpanded = false
                                    viewModel.pushToFeishu()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.quicknote_detail_pull_from_feishu)) },
                                leadingIcon = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    showPullDialog = true
                                }
                            )
                            if (feishuRef?.docUrl?.isNotEmpty() == true) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.quicknote_detail_open_in_feishu)) },
                                    leadingIcon = { Icon(Icons.Filled.OpenInBrowser, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        feishuRef?.docUrl?.let { url ->
                                            val intent = android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(url)
                                            )
                                            context.startActivity(intent)
                                        }
                                    }
                                )
                            }
                            // feishu-bidir-sync:远程已删恢复入口
                            if (feishuRef?.status == FeishuRefStatus.REMOTE_DELETED) {
                                DropdownMenuItem(
                                    text = { Text("重新同步为新文档") },
                                    leadingIcon = { Icon(Icons.Filled.Cloud, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.recreateFeishuDoc()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // 仅无选区时显示 Share FAB(避免系统 selection toolbar 拦截点击)
            if (current != null && fabState.selectionEmpty) {
                FloatingActionButton(
                    onClick = { context.shareNoteMarkdown(current.note.note) }
                ) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = stringResource(R.string.quicknote_detail_share)
                    )
                }
            }
        },
        bottomBar = {
            // 有选区时用固定底部栏替代 FAB,避开系统 selection toolbar 区域
            if (current != null && !fabState.selectionEmpty && noteId != null && aiVm != null) {
                Surface(shadowElevation = 8.dp) {
                    Row(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(onClick = { context.shareNoteMarkdown(current.note.note) }) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = stringResource(R.string.quicknote_detail_share)
                            )
                        }
                        Box {
                            IconButton(
                                onClick = {
                                    if (consentFlow.accepted) {
                                        actionMenuOpen = true
                                    } else {
                                        AiwritingEntry.requestConsent(navController)
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = stringResource(R.string.aiwriting_action_menu)
                                )
                            }
                            val sourceText =
                                if (selection.collapsed) {
                                    current.note.note.content
                                } else {
                                    current.note.note.content.substring(selection.min, selection.max)
                                }
                            AiwritingEntry.ActionSheetRoute(
                                expanded = actionMenuOpen,
                                onDismiss = { actionMenuOpen = false },
                                onExpand = { aiVm.start(WritingOp.EXPAND, sourceText, noteId) },
                                onPolish = { aiVm.start(WritingOp.POLISH, sourceText, noteId) },
                                onOrganize = { aiVm.start(WritingOp.ORGANIZE, sourceText, noteId) },
                                onSummarize = { aiVm.start(WritingOp.SUMMARIZE, sourceText, noteId) },
                                onTranslate = { aiVm.start(WritingOp.TRANSLATE, sourceText, noteId) },
                                onCopy = { AiwritingEntry.copyToClipboard(context, sourceText) }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        when (val s = state) {
            NoteDetailUiState.Loading -> Unit
            NoteDetailUiState.NotFound ->
                Text(
                    text = stringResource(R.string.quicknote_detail_not_found),
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(LocalSpacing.current.lg)
                )
            is NoteDetailUiState.Content ->
                Column(
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(LocalSpacing.current.md)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = s.note.note.title.ifBlank { s.note.note.content.take(Note.TITLE_FALLBACK_LEN) },
                        style = MaterialTheme.typography.headlineSmall
                    )
                    aiMeta?.let { meta ->
                        Text(
                            text = stringResource(
                                R.string.quicknote_meta_ai_fmt,
                                stringResource(opKeyToRes(meta.opKey)),
                                meta.opAt
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = LocalSpacing.current.sm / 2)
                        )
                    }
                    if (s.note.tags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm / 2),
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = LocalSpacing.current.sm)
                        ) {
                            s.note.tags.forEach { tag ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("#$tag") },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                )
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.quicknote_detail_no_tags),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = LocalSpacing.current.sm)
                        )
                    }
                    // M3:SelectionContainer 改为 BasicTextField(readOnly),selection 推回 ViewModel
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            textFieldValue = newValue
                            viewModel.onSelectionChange(newValue.selection)
                        },
                        readOnly = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = LocalSpacing.current.sm)
                    )
                    // 字数 / 阅读时间 — 放在关联笔记上方,作为正文元数据贴近主体
                    Text(
                        text =
                        "${context.getString(R.string.quicknote_detail_word_count_fmt, s.wordCount)}" +
                            context.getString(R.string.quicknote_detail_word_time_separator) +
                            context.getString(R.string.quicknote_detail_read_time_fmt, s.readMinutes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // note-association: 关联笔记
                    val currentNoteId = s.note.note.id
                    Spacer(Modifier.height(LocalSpacing.current.lg))
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(LocalSpacing.current.md))
                    RelatedNotesSection(
                        noteId = currentNoteId,
                        noteLinker = noteLinker,
                        onNavigate = { targetId ->
                            navController.navigate(QuicknoteDetail(targetId))
                        },
                        onAiTrigger = {
                            detailEntry.llmExtractor()?.extractAndPersist(
                                currentNoteId,
                                bypassRateLimit = true
                            ) ?: 0
                        },
                        showAiButton = showAiButton
                    )
                }
        }
    }

    if (aiVm != null) {
        AiwritingEntry.StreamingPanelRoute(
            state = aiState,
            onAccept = { aiVm.acceptReplace() },
            onReject = { aiVm.reject() },
            onCancel = { aiVm.cancel() },
            onRegenerate = { aiVm.regenerate() },
            onClose = { aiVm.dismiss() },
            onRetry = { aiVm.retry() },
            onNavigateToSettings = { navController.navigate(com.yy.writingwithai.app.Settings) },
            onDismiss = { aiVm.dismiss() },
            onUndo = { aiVm.undo() },
            onDismissReplace = { aiVm.dismiss() }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.quicknote_editor_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onDeleted = onDeleted)
                }) { Text(stringResource(R.string.quicknote_editor_delete_confirm_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.quicknote_editor_delete_confirm_cancel))
                }
            }
        )
    }

    // feishu-bidir-sync:pull URL input dialog
    if (showPullDialog) {
        AlertDialog(
            onDismissRequest = { showPullDialog = false },
            title = { Text(stringResource(R.string.quicknote_feishu_pull_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = pullUrlInput,
                    onValueChange = { pullUrlInput = it },
                    label = { Text("https://bytedance.feishu.cn/docx/...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPullDialog = false
                    if (pullUrlInput.isNotBlank()) {
                        viewModel.pullFromFeishu(pullUrlInput.trim())
                        pullUrlInput = ""
                    }
                }) { Text("拉取") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPullDialog = false
                    pullUrlInput = ""
                }) { Text(stringResource(R.string.quicknote_editor_delete_confirm_cancel)) }
            }
        )
    }

    // feishu-bidir-sync:sync status
    if (syncLoading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    // feishu-bidir-sync:conflict resolution dialog
    if (showConflictDialog && feishuRef?.status == FeishuRefStatus.CONFLICT) {
        val localContent = current?.note?.note?.content.orEmpty()
        ConflictResolutionDialog(
            localPreview = localContent,
            remotePreview = "(飞书端内容,需 pull 拉取)",
            onResolveKeepLocal = viewModel::resolveConflictKeepLocal,
            onResolveKeepRemote = viewModel::resolveConflictKeepRemote,
            onCancel = viewModel::cancelConflictResolution
        )
    }

    // feishu-bidir-sync:sync result message
    if (syncMessage != null && !showSyncMessageDialog) {
        LaunchedEffect(syncMessage) { showSyncMessageDialog = true }
    }
    if (showSyncMessageDialog && syncMessage != null) {
        AlertDialog(
            onDismissRequest = {
                showSyncMessageDialog = false
                viewModel.clearSyncMessage()
            },
            title = { Text("飞书同步") },
            text = { Text(syncMessage ?: "") },
            confirmButton = {
                TextButton(onClick = {
                    showSyncMessageDialog = false
                    viewModel.clearSyncMessage()
                }) { Text("确定") }
            }
        )
    }
}

/** M3:把 lastAiOp("expand"/"polish"/"organize") 映射到 R.string.aiwriting_action_* 中文资源。 */
private fun opKeyToRes(opKey: String): Int = when (opKey) {
    "expand" -> R.string.aiwriting_action_expand
    "polish" -> R.string.aiwriting_action_polish
    "organize" -> R.string.aiwriting_action_organize
    "summarize" -> R.string.aiwriting_action_summarize
    "translate" -> R.string.aiwriting_action_translate
    else -> R.string.aiwriting_action_expand
}
