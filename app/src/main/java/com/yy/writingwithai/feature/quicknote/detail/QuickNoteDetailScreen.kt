package com.yy.writingwithai.feature.quicknote.detail

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalCornerRadius
import com.yy.writingwithai.app.ui.theme.LocalSpacing
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.core.feishu.sync.FeishuRefStatus
import com.yy.writingwithai.core.note.NoteLinker
import com.yy.writingwithai.core.prefs.ConsentState
import com.yy.writingwithai.core.prefs.ConsentStore
import com.yy.writingwithai.core.prefs.SecureApiKeyStore
import com.yy.writingwithai.core.ui.dropdown.AppActionDropdown
import com.yy.writingwithai.core.ui.dropdown.AppActionItem
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * note-association:详情屏用的 Hilt EntryPoint(ConsentStore + NoteLinker)。
 */
@EntryPoint
@InstallIn(ActivityComponent::class)
internal interface DetailScreenEntryPoint {
    fun consentStore(): ConsentStore
    fun noteLinker(): NoteLinker
    fun noteAssociationSettings(): com.yy.writingwithai.core.prefs.NoteAssociationSettingsStore
    fun llmExtractor(): com.yy.writingwithai.core.note.impl.SemanticNoteLinker?

    // real-provider-integration §4:UI 早返回 apikey-missing Snackbar，需实时监听已配 provider
    fun secureApiKeyStore(): SecureApiKeyStore
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickNoteDetailScreen(
    onBack: () -> Unit,
    onEdit: (id: String) -> Unit,
    onDeleted: () -> Unit,
    onNavigateToNote: (id: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    // real-provider-integration §4:apikey 缺失时 Snackbar action "去设置" 跳模型管理页
    onNavigateToModelManagement: () -> Unit,
    onRequestConsent: () -> Unit,
    viewModel: QuickNoteDetailViewModel = hiltViewModel()
) {
    // feishu clip labels 提升到 Composable 顶部 scope，供 onClick lambda 使用
    val feishuClipUrlLabel = stringResource(R.string.feishu_clip_label_url)
    val feishuClipErrorLabel = stringResource(R.string.feishu_clip_label_error)
    // M4-4:详情屏 consent 闸门(UI 层二次防御，与 ViewModel.start() 一致)
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
    val showAiButton by noteAssocSettings.observeEnabled()
        .collectAsStateWithLifecycle(initialValue = noteAssocSettings.isEnabled())
    // real-provider-integration §4:UI 早返回 apikey-missing Snackbar
    val secureApiKeyStore = detailEntry.secureApiKeyStore()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val apikeyMissingMessage = stringResource(R.string.ai_error_provider_not_configured)
    val apikeyMissingAction = stringResource(R.string.ai_error_action_go_settings)
    // M2 fix:uiState 是驱动整个详情屏的主 Flow，必须 lifecycle-aware。
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
    // H2 修:noteId 真存在时才挂 AiActionViewModel，避免 NotFound / saved-state 缺失时残留 FAB/Sheet。
    val aiVm: AiActionViewModel? =
        if (noteId != null) {
            AiwritingEntry.rememberAiActionViewModel(noteId)
        } else {
            null
        }
    // H1 修:`by` 委托订阅 StateFlow，而不是 `.value` 快照读(后者不触发重组，Sheet 永不显示)。
    // aiVm null 时 fallback 到 remember 住的 Idle 流，StreamingPanel 走 return 不渲染。
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
    // H3 修:TextFieldValue 仅在 noteId 变化时重建，避免 content / wordCount / tags 任一变化重置选区。
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
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
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
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.common_more_cd)
                            )
                        }
                        AppActionDropdown(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            items = buildList {
                                add(
                                    AppActionItem(
                                        text = if (current.note.note.isPinned) {
                                            stringResource(R.string.quicknote_detail_unpin)
                                        } else {
                                            stringResource(R.string.quicknote_detail_pin)
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.togglePinned()
                                        },
                                        leadingIcon = if (current.note.note.isPinned) {
                                            Icons.Filled.PushPin
                                        } else {
                                            Icons.Outlined.PushPin
                                        }
                                    )
                                )
                                add(
                                    AppActionItem(
                                        text = stringResource(R.string.quicknote_detail_share),
                                        onClick = {
                                            menuExpanded = false
                                            context.shareNoteMarkdown(current.note.note)
                                        },
                                        leadingIcon = Icons.Filled.Share
                                    )
                                )
                                add(
                                    AppActionItem(
                                        text = stringResource(R.string.quicknote_detail_export_md),
                                        onClick = {
                                            menuExpanded = false
                                            val note = current.note.note
                                            val name = note.title.ifBlank { note.id } + ".md"
                                            exportMdLauncher.launch(name)
                                        },
                                        leadingIcon = Icons.Filled.Share
                                    )
                                )
                                add(
                                    AppActionItem(
                                        text = stringResource(R.string.quicknote_detail_export_txt),
                                        onClick = {
                                            menuExpanded = false
                                            val note = current.note.note
                                            val name = note.title.ifBlank { note.id } + ".txt"
                                            exportTxtLauncher.launch(name)
                                        },
                                        leadingIcon = Icons.Filled.Share
                                    )
                                )
                                add(
                                    AppActionItem(
                                        text = stringResource(R.string.quicknote_detail_delete),
                                        onClick = {
                                            menuExpanded = false
                                            confirmDelete = true
                                        },
                                        leadingIcon = Icons.Filled.Delete,
                                        isDestructive = true
                                    )
                                )
                                // feishu-bidir-sync:同步菜单
                                add(
                                    AppActionItem(
                                        text = stringResource(R.string.quicknote_detail_sync_to_feishu),
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.pushToFeishu()
                                        },
                                        leadingIcon = Icons.Filled.Cloud,
                                        enabled = !syncLoading
                                    )
                                )
                                add(
                                    AppActionItem(
                                        text = stringResource(R.string.quicknote_detail_pull_from_feishu),
                                        onClick = {
                                            menuExpanded = false
                                            pullUrlInput = feishuRef?.docUrl.orEmpty()
                                            showPullDialog = true
                                        },
                                        leadingIcon = Icons.Filled.CloudDownload
                                    )
                                )
                                if (feishuRef?.docUrl?.isNotEmpty() == true) {
                                    add(
                                        AppActionItem(
                                            text = stringResource(R.string.quicknote_detail_open_in_feishu),
                                            onClick = {
                                                menuExpanded = false
                                                feishuRef?.docUrl?.let { url ->
                                                    val intent = android.content.Intent(
                                                        android.content.Intent.ACTION_VIEW,
                                                        android.net.Uri.parse(url)
                                                    )
                                                    context.startActivity(intent)
                                                }
                                            },
                                            leadingIcon = Icons.Filled.OpenInBrowser
                                        )
                                    )
                                }
                                // feishu-bidir-sync:远程已删恢复入口
                                if (feishuRef?.status == FeishuRefStatus.REMOTE_DELETED) {
                                    add(
                                        AppActionItem(
                                            text = stringResource(R.string.quicknote_detail_resync_as_new),
                                            onClick = {
                                                menuExpanded = false
                                                viewModel.recreateFeishuDoc()
                                            },
                                            leadingIcon = Icons.Filled.Cloud
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            // ui-redesign-v2: FAB 已移除，操作走底部栏
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            // ui-redesign-v2 · 固定底部操作栏:Share + AI 两个常驻图标
            if (current != null && noteId != null) {
                Surface(shadowElevation = 2.dp, tonalElevation = 0.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = LocalSpacing.current.lg, vertical = LocalSpacing.current.sm),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(onClick = { context.shareNoteMarkdown(current.note.note) }) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = stringResource(R.string.quicknote_detail_share),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (aiVm != null) {
                            Box {
                                IconButton(
                                    onClick = {
                                        if (consentFlow.accepted) {
                                            // real-provider-integration §4:apikey 未配置 → Snackbar 早返回
                                            // 不弹 sheet / 不发请求 / 不让用户走到 AiActionViewModel.start()
                                            snackbarScope.launch {
                                                val configured =
                                                    secureApiKeyStore.observeConfiguredProviders().first()
                                                if (configured.isEmpty()) {
                                                    val result =
                                                        snackbarHostState.showSnackbar(
                                                            message = apikeyMissingMessage,
                                                            actionLabel = apikeyMissingAction
                                                        )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        onNavigateToModelManagement()
                                                    }
                                                    return@launch
                                                }
                                                actionMenuOpen = true
                                            }
                                        } else {
                                            onRequestConsent()
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.AutoAwesome,
                                        contentDescription = stringResource(R.string.aiwriting_action_menu),
                                        tint = MaterialTheme.colorScheme.secondary
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
        }
    ) { innerPadding ->
        val attachmentPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            uri?.let { viewModel.addAttachment(it) }
        }
        // H8 fix:用 collectAsStateWithLifecycle 替代 LaunchedEffect + collect,
        // 让 attachment Flow 感知 lifecycle(onStop 暂停 collect,onStart 恢复),
        // 避免后台持续 collect 浪费资源。
        val attachments by viewModel.observeAttachments()
            .collectAsStateWithLifecycle(initialValue = emptyList())

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
                        style = MaterialTheme.typography.headlineLarge
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
                    // 字数 / 阅读时间 — 放在关联笔记上方，作为正文元数据贴近主体
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
                    // media-attachment-infrastructure · 附件图片 LazyRow
                    // ux-2026-06-28 #8:之前用 ColorPainter 当 placeholder 显示空白方块;
                    // 改为 produceState 在 IO 线程 decodeFile → ImageBitmap，失败回退占位色。
                    if (attachments.isNotEmpty()) {
                        Spacer(Modifier.height(LocalSpacing.current.md))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)) {
                            items(attachments, key = { it.id }) { att ->
                                val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                                    initialValue = null,
                                    key1 = att.localPath
                                ) {
                                    value = withContext(Dispatchers.IO) {
                                        runCatching {
                                            // 80dp 缩略:目标 160px(inSampleSize 取 2^n)，省内存
                                            BitmapFactory.Options().apply {
                                                inJustDecodeBounds = true
                                            }.let { bounds ->
                                                BitmapFactory.decodeFile(att.localPath, bounds)
                                                BitmapFactory.Options().apply {
                                                    inSampleSize = calculateThumbSample(
                                                        bounds.outWidth,
                                                        bounds.outHeight,
                                                        160
                                                    )
                                                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                                                }
                                            }.let { opts ->
                                                BitmapFactory.decodeFile(att.localPath, opts)
                                            }
                                        }.getOrNull()?.asImageBitmap()
                                    }
                                }
                                val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap!!,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(LocalCornerRadius.current.sm))
                                            .background(placeholderColor)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(LocalCornerRadius.current.sm))
                                            .background(placeholderColor)
                                    )
                                }
                            }
                        }
                    }
                    androidx.compose.material3.TextButton(
                        onClick = {
                            attachmentPicker.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(LocalSpacing.current.xs))
                        Text(stringResource(R.string.quicknote_detail_add_image))
                    }
                    Spacer(Modifier.height(LocalSpacing.current.md))
                    RelatedNotesSection(
                        noteId = currentNoteId,
                        noteLinker = noteLinker,
                        onNavigate = onNavigateToNote,
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
            onNavigateToSettings = onNavigateToSettings,
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
                    label = { Text(stringResource(R.string.quicknote_feishu_pull_url_label)) },
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
                }) { Text(stringResource(R.string.quicknote_feishu_pull_confirm)) }
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
            remotePreview = stringResource(R.string.quicknote_feishu_conflict_remote_placeholder),
            onResolveKeepLocal = viewModel::resolveConflictKeepLocal,
            onResolveKeepRemote = viewModel::resolveConflictKeepRemote,
            onCancel = viewModel::cancelConflictResolution
        )
    }

    // feishu-bidir-sync:sync result message
    // CR-FIX-M6:消费 SyncMessage sealed 事件，不再 startsWith("同步完成:") 解析。
    if (syncMessage != null && !showSyncMessageDialog) {
        LaunchedEffect(syncMessage) { showSyncMessageDialog = true }
    }
    if (showSyncMessageDialog && syncMessage != null) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val msg = syncMessage
        val isSuccess = msg is SyncMessage.Success
        val displayText: String = when (msg) {
            is SyncMessage.Success -> msg.docUrl
            is SyncMessage.Failure -> msg.reason
            null -> ""
        }
        AlertDialog(
            onDismissRequest = {
                showSyncMessageDialog = false
                viewModel.clearSyncMessage()
            },
            title = {
                Text(
                    if (isSuccess) {
                        stringResource(
                            R.string.quicknote_feishu_sync_success
                        )
                    } else {
                        stringResource(R.string.quicknote_feishu_sync_fail)
                    }
                )
            },
            text = { Text(displayText) },
            confirmButton = {
                TextButton(onClick = {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    // 成功时复制 URL(打开看文档);失败时复制错误消息
                    val label = if (isSuccess) feishuClipUrlLabel else feishuClipErrorLabel
                    cm.setPrimaryClip(android.content.ClipData.newPlainText(label, displayText))
                    showSyncMessageDialog = false
                    viewModel.clearSyncMessage()
                }) {
                    Text(
                        if (isSuccess) {
                            stringResource(
                                R.string.quicknote_feishu_copy_link
                            )
                        } else {
                            stringResource(R.string.quicknote_feishu_copy_error)
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSyncMessageDialog = false
                    viewModel.clearSyncMessage()
                }) { Text(stringResource(R.string.quicknote_feishu_close)) }
            }
        )
    }
}

/**
 * ux-2026-06-28 #8:缩略图 inSampleSize 计算。80dp ≈ 160px(2x density),
 * `reqPx` 上下浮动，长边 ≤ reqPx 时 sampleSize=1。
 */
private fun calculateThumbSample(srcWidth: Int, srcHeight: Int, reqPx: Int): Int {
    if (srcWidth <= 0 || srcHeight <= 0) return 1
    var sample = 1
    val halfW = srcWidth / 2
    val halfH = srcHeight / 2
    while (halfW / sample >= reqPx && halfH / sample >= reqPx) {
        sample *= 2
    }
    return sample
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
