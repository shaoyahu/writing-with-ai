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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
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
import com.yy.writingwithai.core.note.RelatedNote
import com.yy.writingwithai.core.prefs.ConsentState
import com.yy.writingwithai.core.prefs.ConsentStore
import com.yy.writingwithai.core.prefs.SecureApiKeyStore
import com.yy.writingwithai.core.ui.dropdown.AppActionDropdown
import com.yy.writingwithai.core.ui.dropdown.AppActionItem
import com.yy.writingwithai.feature.aiwriting.AiActionUiState
import com.yy.writingwithai.feature.aiwriting.AiActionViewModel
import com.yy.writingwithai.feature.aiwriting.AiwritingEntry
import com.yy.writingwithai.feature.aiwriting.streaming.AiActionUiState.Idle
import com.yy.writingwithai.feature.quicknote.model.EntityHighlight
import com.yy.writingwithai.feature.quicknote.model.NoteDetailUiState
import com.yy.writingwithai.feature.quicknote.model.toHighlight
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
    android.util.Log.d("DetailToolbar", "QuickNoteDetailScreen compose enter")
    val keyboardCtrl = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
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
    val showFolderMigrationDialog by viewModel.showFolderMigrationDialog.collectAsStateWithLifecycle()
    val folderMigrationInfo by viewModel.folderMigrationInfo.collectAsStateWithLifecycle()
    val decomposeState by viewModel.decomposeState.collectAsStateWithLifecycle()
    val entityRows by viewModel.entityRows.collectAsStateWithLifecycle()

    // note-decompose-highlight T3:是否已配置 AI 模型（驱动菜单项可见性）
    val hasAiProvider by produceState(initialValue = false, key1 = secureApiKeyStore) {
        secureApiKeyStore.observeConfiguredProviders().collect { value = it.isNotEmpty() }
    }

    var showPullDialog by remember { mutableStateOf(false) }
    var pullUrlInput by remember { mutableStateOf("") }
    // entity-management-and-ai-decompose §2.5:重新拆解确认对话框
    var showReDecomposeConfirm by rememberSaveable { mutableStateOf(false) }

    val current = state as? NoteDetailUiState.Content
    val noteId = current?.note?.note?.id

    // note-decompose-highlight T6:缓存加载 — 进入详情页时查询已有实体
    LaunchedEffect(noteId) {
        noteId?.let { viewModel.loadCachedEntities() }
    }

    // note-decompose-highlight M1 fix:用 Job 取消前一个 Snackbar 防排队；M2 fix:用 context.getString 替代 String.format
    var decomposeSnackbarJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val decomposeFoundFmtResId = R.string.note_decompose_found_fmt
    val decomposeNoEntities = stringResource(R.string.note_decompose_no_entities)
    LaunchedEffect(decomposeState) {
        if (decomposeState is DecomposeState.Idle || decomposeState is DecomposeState.Loading) return@LaunchedEffect
        decomposeSnackbarJob?.cancel()
        decomposeSnackbarJob = snackbarScope.launch {
            when (val ds = decomposeState) {
                is DecomposeState.Decomposed ->
                    snackbarHostState.showSnackbar(
                        if (ds.entityCount > 0) {
                            context.getString(decomposeFoundFmtResId, ds.entityCount)
                        } else {
                            decomposeNoEntities
                        }
                    )
                is DecomposeState.Error ->
                    snackbarHostState.showSnackbar(ds.message)
                else -> { /* Idle/Loading skipped */ }
            }
        }
    }

    // note-decompose-highlight T5:BottomSheet 状态 — M4 fix:用 EntityHighlight 替代 NoteEntityRow
    var selectedEntity by remember { mutableStateOf<EntityHighlight?>(null) }
    var relatedForEntity by remember { mutableStateOf<List<RelatedNote>>(emptyList()) }
    val entitySheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
    // note-detail-polish: 用旧 API BasicTextField(TextFieldValue, readOnly=false) +
    // onValueChange 立即 hide IME —— 长按选中正常工作 + IME 不弹。
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
    // note-detail-polish: 直接用 uiSelection 驱动 UI;SelectionContainer.onSelectionChange 更新它
    var uiSelection by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(androidx.compose.ui.text.TextRange.Zero)
    }
    android.util.Log.d("DetailToolbar", "render check: uiSelection=$uiSelection current!=null=${current != null}")
    LaunchedEffect(Unit) {
        viewModel.selection.collect { sel ->
            android.util.Log.d("DetailToolbar", "viewModel.selection collect: $sel")
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
                                // note-decompose-highlight T3:拆解菜单项
                                // note-detail-polish: Loading 时不隐藏菜单项,改为禁用+显示进度文案
                                val isDecomposing = decomposeState is DecomposeState.Loading
                                add(
                                    AppActionItem(
                                        text = when {
                                            isDecomposing -> stringResource(R.string.note_decomposing)
                                            entityRows.isNotEmpty() -> stringResource(R.string.note_redecompose)
                                            else -> stringResource(R.string.note_decompose)
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.decompose()
                                        },
                                        leadingIcon = Icons.Outlined.Hub,
                                        enabled = hasAiProvider && !isDecomposing
                                    )
                                )
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            // ui-redesign-v2: FAB 已移除，操作走底部栏
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
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

        // note-detail-polish: 拆解 Loading 时在 TopAppBar 下方显示进度条
        if (decomposeState is DecomposeState.Loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

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
                // note-detail-polish: Box 包裹内容 + 浮动选中工具栏
                Box(modifier = Modifier.fillMaxSize()) {
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
                        // entity-management-and-ai-decompose §3:蓝色字体 + 右上角 ✦ 十字星星
                        // 用 SpanStyle(color = primaryColor) 标记实体文本,不再用 Underline。
                        // 实体文本末尾直接追加 ✦ 字符(作为 AnnotatedString 的一部分)。
                        val currentNoteId = s.note.note.id
                        val contentText = s.note.note.content
                        val titleLen = s.note.note.title.length + 1 // +1 for "\n"
                        val contentLen = contentText.length
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val entityHighlights = remember(entityRows, titleLen, contentLen) {
                            entityRows.mapNotNull { it.toHighlight(titleLen, contentLen) }
                        }
                        val annotatedContent = remember(contentText, entityHighlights, primaryColor) {
                            buildEntityAnnotatedString(contentText, entityHighlights, primaryColor)
                        }
                        // note-detail-polish:统一用 BasicTextField 渲染正文，无论有无实体。
                        // 有实体时 annotatedContent 带 SpanStyle(下划线/颜色)+StringAnnotation(entity)，
                        // BasicTextField 正确渲染样式;同时支持文本选择(唤出浮选工具栏)。
                        // 点击实体高亮时通过 onValueChange 检测 collapsed selection 位置，
                        // 弹出 entity sheet —— 替代原来的 ClickableText。
                        BasicTextField(
                            value = TextFieldValue(
                                annotatedString = annotatedContent,
                                selection = uiSelection
                            ),
                            onValueChange = { newValue ->
                                val selection = newValue.selection
                                if (!selection.collapsed) {
                                    // 用户选择了文本 → 更新 uiSelection 驱动浮选工具栏
                                    uiSelection = selection
                                    viewModel.onSelectionChange(selection)
                                } else {
                                    // 用户点击了某个位置(collapsed) → 检测是否命中实体
                                    val offset = selection.start
                                    val annotations = annotatedContent.getStringAnnotations(
                                        tag = "entity",
                                        start = offset,
                                        end = offset
                                    )
                                    val entityKey = annotations.firstOrNull()?.item
                                    if (entityKey != null) {
                                        val highlight = entityHighlights.find {
                                            it.entityKey == entityKey
                                        }
                                        if (highlight != null) {
                                            snackbarScope.launch {
                                                relatedForEntity = viewModel.getRelatedByEntity(entityKey)
                                                selectedEntity = highlight
                                                entitySheetState.show()
                                            }
                                        }
                                    }
                                    // 点击后重置 selection，隐藏浮选工具栏
                                    uiSelection = androidx.compose.ui.text.TextRange.Zero
                                    viewModel.onSelectionChange(
                                        androidx.compose.ui.text.TextRange.Zero
                                    )
                                }
                                keyboardCtrl?.hide()
                            },
                            readOnly = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
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
                            related = viewModel.related.collectAsStateWithLifecycle(initialValue = emptyList()).value,
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

        // note-decompose-highlight T5:实体详情 BottomSheet
        val entity = selectedEntity
        val entityTagText = if (entity != null) {
            if (entity.source == "USER_ADDED") {
                stringResource(R.string.entity_source_user_added)
            } else {
                stringResource(
                    when (entity.entityType) {
                        "person" -> R.string.entity_type_person
                        "work" -> R.string.entity_type_work
                        "event" -> R.string.entity_type_event
                        "location" -> R.string.entity_type_location
                        "org" -> R.string.entity_type_org
                        "concept" -> R.string.entity_type_concept
                        "date" -> R.string.entity_type_date
                        "url" -> R.string.entity_type_url
                        "quote" -> R.string.entity_type_quote
                        "product" -> R.string.entity_type_product
                        "task" -> R.string.entity_type_task
                        "number" -> R.string.entity_type_number
                        else -> R.string.entity_type_concept
                    }
                )
            }
        } else {
            ""
        }
        if (entity != null && entitySheetState.isVisible) {
            ModalBottomSheet(
                onDismissRequest = { selectedEntity = null },
                sheetState = entitySheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LocalSpacing.current.lg, vertical = LocalSpacing.current.md)
                ) {
                    // 标题行：实体名 + tag 标签
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
                    ) {
                        Text(
                            text = entity.surfaceForm,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = entityTagText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(LocalSpacing.current.md))
                    if (relatedForEntity.isEmpty()) {
                        Text(
                            text = stringResource(R.string.note_decompose_no_related),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        relatedForEntity.forEach { related ->
                            androidx.compose.material3.Card(
                                onClick = {
                                    // note-decompose-highlight H2 fix:不同步清 selectedEntity，让 sheet 走完关闭动画
                                    snackbarScope.launch { entitySheetState.hide() }
                                    onNavigateToNote(related.noteId)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                elevation = androidx.compose.material3.CardDefaults.cardElevation(
                                    defaultElevation = 0.dp
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = related.title.ifBlank {
                                            stringResource(R.string.quicknote_untitled)
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    if (related.preview.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = related.preview,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(LocalSpacing.current.lg))
                }
            }
        } // Box
    }
    // note-detail-polish: 选中文字时浮出快捷工具栏 — 放在 Scaffold 顶层(避免被 when/Column 嵌套吞掉)
    if (!uiSelection.collapsed && current != null) {
        android.util.Log.d("DetailToolbar", "TOOLBAR rendering now (top-level)")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars)
                .padding(bottom = LocalSpacing.current.md),
            contentAlignment = Alignment.BottomCenter
        ) {
            SelectionFloatingToolbar(
                isAiEnabled = hasAiProvider,
                onAddEntity = {
                    val selectedText = current.note.note.content.substring(
                        uiSelection.min,
                        uiSelection.max
                    )
                    viewModel.addEntityFromSelection(
                        surface = selectedText,
                        spanStart = uiSelection.min,
                        spanEnd = uiSelection.max
                    )
                },
                onAiExpand = {
                    aiVm?.start(
                        WritingOp.EXPAND,
                        current.note.note.content.substring(uiSelection.min, uiSelection.max),
                        noteId!!
                    )
                },
                onAiPolish = {
                    aiVm?.start(
                        WritingOp.POLISH,
                        current.note.note.content.substring(uiSelection.min, uiSelection.max),
                        noteId!!
                    )
                },
                onAiOrganize = {
                    aiVm?.start(
                        WritingOp.ORGANIZE,
                        current.note.note.content.substring(uiSelection.min, uiSelection.max),
                        noteId!!
                    )
                },
                onAiSummarize = {
                    aiVm?.start(
                        WritingOp.SUMMARIZE,
                        current.note.note.content.substring(uiSelection.min, uiSelection.max),
                        noteId!!
                    )
                },
                onAiTranslate = {
                    aiVm?.start(
                        WritingOp.TRANSLATE,
                        current.note.note.content.substring(uiSelection.min, uiSelection.max),
                        noteId!!
                    )
                }
            )
        }
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

    // feishu-folder-migration:folder token 变更迁移对话框
    val migrationInfo = folderMigrationInfo
    if (showFolderMigrationDialog && migrationInfo != null) {
        FolderMigrationDialog(
            oldLocation = describeFolderLocation(migrationInfo.refFolderToken),
            newLocation = describeFolderLocation(migrationInfo.currentFolderToken),
            onDeleteAndRecreate = viewModel::resolveFolderMigrationDeleteAndRecreate,
            onUpdateInPlace = viewModel::resolveFolderMigrationUpdateInPlace,
            onCancel = viewModel::cancelFolderMigration
        )
    }

    // entity-management-and-ai-decompose §2.5:重新拆解确认对话框
    if (showReDecomposeConfirm) {
        AlertDialog(
            onDismissRequest = { showReDecomposeConfirm = false },
            title = { Text(stringResource(R.string.entity_redecompose_confirm_title)) },
            text = { Text(stringResource(R.string.entity_redecompose_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showReDecomposeConfirm = false
                    viewModel.decompose(forceReExtract = true)
                }) { Text(stringResource(R.string.entity_redecompose_confirm_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showReDecomposeConfirm = false }) {
                    Text(stringResource(R.string.entity_redecompose_confirm_cancel))
                }
            }
        )
    }

    // entity-management-and-ai-decompose §2.7:API key 未配置时弹错误对话框
    if (decomposeState is DecomposeState.ApiKeyMissing) {
        AlertDialog(
            onDismissRequest = { viewModel.resetDecomposeState() },
            title = { Text(stringResource(R.string.entity_apikey_missing_title)) },
            text = { Text(stringResource(R.string.entity_apikey_missing_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetDecomposeState()
                    onNavigateToModelManagement()
                }) { Text(stringResource(R.string.entity_apikey_missing_go_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resetDecomposeState() }) {
                    Text(stringResource(R.string.entity_apikey_missing_cancel))
                }
            }
        )
    }

    // entity-management-and-ai-decompose §2.6:全屏 loading 覆盖层
    if (decomposeState is DecomposeState.Loading) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { /* 不允许点击外部关闭 */ },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            androidx.compose.material3.Surface(
                modifier = Modifier.size(160.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.entity_decomposing),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    // feishu-bidir-sync:sync result message
    // CR-FIX-M6:消费 SyncMessage sealed 事件，不再 startsWith("同步完成:") 解析。
    // feishu-sync-feedback · 成功 → Snackbar;失败 → 按 Failure 子类型渲染不同 Dialog。
    val syncSuccessFmt = stringResource(R.string.feishu_sync_success_fmt)
    val syncActionCopy = stringResource(R.string.feishu_sync_action_copy)
    val syncCopied = stringResource(R.string.feishu_sync_copied)
    var syncSnackbarJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    LaunchedEffect(syncMessage) {
        val msg = syncMessage ?: return@LaunchedEffect
        if (msg is SyncMessage.Success) {
            // D3 · cancel 前一个 Snackbar 防排队
            syncSnackbarJob?.cancel()
            syncSnackbarJob = snackbarScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = syncSuccessFmt.format(msg.noteTitle),
                    actionLabel = syncActionCopy,
                    duration = androidx.compose.material3.SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    cm.setPrimaryClip(
                        android.content.ClipData.newPlainText("Feishu doc URL", msg.docUrl)
                    )
                    snackbarHostState.showSnackbar(
                        message = syncCopied,
                        duration = androidx.compose.material3.SnackbarDuration.Short
                    )
                }
                viewModel.clearSyncMessage()
            }
        }
        // Failure cases 由下方 when 直接渲染 AlertDialog,不需要 Snackbar 通道
    }
    syncMessage?.let { msg ->
        if (msg is SyncMessage.Failure) {
            when (msg) {
                is SyncMessage.Failure.Conflict -> ConflictResolutionDialog(
                    localPreview = (current?.note?.note?.content ?: "").take(120),
                    remotePreview = (current?.note?.note?.content ?: "").take(120),
                    onResolveKeepLocal = {
                        viewModel.resolveConflictKeepLocal()
                        viewModel.clearSyncMessage()
                    },
                    onResolveKeepRemote = {
                        viewModel.resolveConflictKeepRemote()
                        viewModel.clearSyncMessage()
                    },
                    onCancel = {
                        viewModel.cancelConflictResolution()
                        viewModel.clearSyncMessage()
                    }
                )
                is SyncMessage.Failure.FolderMigration -> FolderMigrationDialog(
                    oldLocation = describeFolderLocation(msg.refFolderToken),
                    newLocation = describeFolderLocation(msg.currentFolderToken),
                    onDeleteAndRecreate = {
                        viewModel.resolveFolderMigrationDeleteAndRecreate()
                        viewModel.clearSyncMessage()
                    },
                    onUpdateInPlace = {
                        viewModel.resolveFolderMigrationUpdateInPlace()
                        viewModel.clearSyncMessage()
                    },
                    onCancel = {
                        viewModel.cancelFolderMigration()
                        viewModel.clearSyncMessage()
                    }
                )
                is SyncMessage.Failure.RemoteDeleted -> SyncFailureDialogs.RemoteDeleted(
                    onRecreate = {
                        viewModel.recreateFeishuDoc()
                        viewModel.clearSyncMessage()
                    },
                    onDismiss = { viewModel.clearSyncMessage() }
                )
                is SyncMessage.Failure.Empty -> SyncFailureDialogs.Empty(
                    onDismiss = { viewModel.clearSyncMessage() }
                )
                is SyncMessage.Failure.Network -> SyncFailureDialogs.Network(
                    onRetry = {
                        viewModel.retryLastSync()
                        viewModel.clearSyncMessage()
                    },
                    onDismiss = { viewModel.clearSyncMessage() }
                )
                is SyncMessage.Failure.Server -> SyncFailureDialogs.Server(
                    onRetry = {
                        viewModel.retryLastSync()
                        viewModel.clearSyncMessage()
                    },
                    onDismiss = { viewModel.clearSyncMessage() }
                )
                is SyncMessage.Failure.RateLimited -> SyncFailureDialogs.RateLimited(
                    retryAfterSeconds = msg.retryAfterSeconds,
                    onAck = { viewModel.clearSyncMessage() }
                )
                is SyncMessage.Failure.Unknown -> SyncFailureDialogs.Unknown(
                    cause = msg.cause,
                    onCopyError = {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cm.setPrimaryClip(
                            android.content.ClipData.newPlainText("同步错误", msg.cause)
                        )
                        viewModel.clearSyncMessage()
                    },
                    onDismiss = { viewModel.clearSyncMessage() }
                )
            }
        }
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

/**
 * entity-management-and-ai-decompose §3:构建带实体高亮的 AnnotatedString。
 *
 * - 蓝色字体:`SpanStyle(color = primaryColor)`,不再用 Underline。
 * - 实体文本末尾追加 ✦ 字符(作为普通文本的一部分)。
 * - 重叠实体按 span 长度降序处理(最长优先),短 span 的 annotation 会被长 span 覆盖。
 */
private fun buildEntityAnnotatedString(
    content: String,
    highlights: List<EntityHighlight>,
    primaryColor: androidx.compose.ui.graphics.Color
): AnnotatedString = buildAnnotatedString {
    append(content)
    val sorted = highlights.sortedByDescending { it.contentEnd - it.contentStart }
    val claimed = mutableListOf<IntRange>()
    for (h in sorted) {
        val start = h.contentStart.coerceIn(0, content.length)
        val end = h.contentEnd.coerceIn(start, content.length)
        if (start >= end) continue
        addStyle(
            style = SpanStyle(color = primaryColor),
            start = start,
            end = end
        )
        // 完全未被更长 span 占据的区间才追加 StringAnnotation + ✦
        if (claimed.none { it.first <= start && it.last >= end }) {
            addStringAnnotation(
                tag = "entity",
                annotation = h.entityKey,
                start = start,
                end = end
            )
            // 在 entity 文本末尾追加 ✦(作为普通文本,用 SpanStyle 着色)
            val starIndex = content.length
            append(EntityCrossStarChar)
            addStyle(
                style = SpanStyle(color = primaryColor),
                start = starIndex,
                end = starIndex + EntityCrossStarChar.length
            )
            claimed.add(start until end)
        }
    }
}

private const val EntityCrossStarChar: String = "✦"
