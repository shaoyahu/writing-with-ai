package com.yy.writingwithai.feature.quicknote.list

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalCornerRadius
import com.yy.writingwithai.app.ui.theme.LocalSpacing
import com.yy.writingwithai.core.feishu.sync.FeishuImportService
import com.yy.writingwithai.core.prefs.SearchHistoryStore
import com.yy.writingwithai.core.ui.NoteListSkeleton
import com.yy.writingwithai.core.ui.dropdown.AppActionDropdown
import com.yy.writingwithai.core.ui.dropdown.AppActionItem
import com.yy.writingwithai.feature.quicknote.model.NoteListUiState
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * app-bottom-tab-bar · 笔记 tab 根屏。
 *
 * 瘦身记录(原 M4-3 overflow menu + FAB + exportAllLauncher 全部下移):
 * - 删右上角三点菜单(整段,入口下移"我的" tab)
 * - 删 `Scaffold.floatingActionButton`(职责上移 `AppShell` 中央 FAB)
 * - 删 `exportAllLauncher`(导出迁入 `SettingsDataScreen`)
 * - 删 `onSettingsClick` / `onPromptSettingsClick` 形参
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QuickNoteListScreen(
    onNoteClick: (id: String) -> Unit,
    onCreateClick: () -> Unit,
    // feishu-import-from-folder:从文件夹导入 sub-screen 跳转
    onNavigateToFolderImport: () -> Unit = {},
    // feishu-import-from-folder:未授权时跳"我的"tab
    onNavigateToFeishuAuth: () -> Unit = {},
    viewModel: QuickNoteListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val density = LocalDensity.current
    val allTags: List<String> = (state as? NoteListUiState.Content)?.allTags ?: emptyList()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // fix-2026-06-26-review-r3 M9:搜索历史 UI 旧实现只在 `LaunchedEffect(Unit)` 首次进屏
    // 拉一次,后续 `SearchHistoryStore.add/remove` 写完后 UI 不刷新。新实现:
    // 1) `remember(context)` 加 key 保证 context 切换(罕见但合理)时重新拉;
    // 2) 用 `produceState` 让 store 变化时(本屏内 remove 调用后)立刻 emit 新列表。
    var searchHistory by remember(context) { mutableStateOf<List<String>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(context) {
        searchHistory = SearchHistoryStore.getAll(context)
    }

    // note-list-card-actions · 屏级 3 state:同屏只能显示 1 个菜单 / 1 个 dialog,用 noteId 区分。
    var menuExpandedFor by remember { mutableStateOf<String?>(null) }
    // note-list-card-actions · 长按触摸偏移(px → dp),用于菜单锚定触摸位置
    var menuTouchOffset by remember { mutableStateOf<DpOffset>(DpOffset(0.dp, 0.dp)) }
    var confirmDeleteFor by remember { mutableStateOf<String?>(null) }
    var showAddTagFor by remember { mutableStateOf<String?>(null) }

    // feishu-import-from-folder:导入菜单 / 对话框 state
    var importMenuExpanded by remember { mutableStateOf(false) }
    var showImportDocDialog by remember { mutableStateOf(false) }
    var showUnauthorizedDialog by remember { mutableStateOf(false) }
    var importInput by remember { mutableStateOf("") }
    var importToast by remember { mutableStateOf<String?>(null) }

    // feishu-import-from-folder:监听 VM 的导入结果 / 授权检查事件
    // 用 collectAsStateWithLifecycle 替代 onClick 内 coroutineScope.launch,
    // 避免屏幕旋转 / Composable 重组导致回调丢失。
    val importResultEvent by viewModel.importResultEvents.collectAsStateWithLifecycle(
        initialValue = null as FeishuImportService.ImportResult?
    )
    val authCheckEvent by viewModel.authCheckEvents.collectAsStateWithLifecycle(
        initialValue = null as QuickNoteListViewModel.AuthCheckResult?
    )
    importResultEvent?.let { evt ->
        LaunchedEffect(evt) {
            importToast = when (evt) {
                is FeishuImportService.ImportResult.Success -> "导入成功"
                is FeishuImportService.ImportResult.Failure -> evt.reason
            }
        }
    }
    // 收到授权检查结果时弹相应 dialog / 跳 sub-screen(用 lastHandledAuthCheck 防重复触发)
    var lastHandledAuthCheck by remember { mutableStateOf<QuickNoteListViewModel.AuthCheckResult?>(null) }
    var pendingAuthTarget by remember { mutableStateOf<String?>(null) }
    val currentAuth = authCheckEvent
    if (currentAuth != null && currentAuth != lastHandledAuthCheck) {
        lastHandledAuthCheck = currentAuth
        when (currentAuth) {
            QuickNoteListViewModel.AuthCheckResult.Authorized -> {
                if (pendingAuthTarget == "folder") {
                    pendingAuthTarget = null
                    onNavigateToFolderImport()
                } else {
                    showImportDocDialog = true
                }
            }
            QuickNoteListViewModel.AuthCheckResult.Unauthorized -> showUnauthorizedDialog = true
        }
    }

    Scaffold(
        // app-bottom-tab-bar · 跟【我的】tab 一致:AppShell 外层 Scaffold 已用 innerPadding
        // 扣掉 bottomBar(tab 栏)占位,这里不应再吃 bottom inset(默认 systemBars 含 bottom),
        // 否则 LazyColumn 滚到底会跟 tab 栏之间留一段空隙。改成只吃 statusBars 让 TopAppBar
        // 正常避让状态栏,horizontal + bottom 全交给外层。
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.quicknote_list_title),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                // feishu-import-from-folder:右上角加 dropdown 入口
                actions = {
                    IconButton(onClick = { importMenuExpanded = true }) {
                        Icon(
                            Icons.Filled.CloudDownload,
                            contentDescription = stringResource(R.string.quicknote_list_import_cd)
                        )
                    }
                    AppActionDropdown(
                        expanded = importMenuExpanded,
                        onDismissRequest = { importMenuExpanded = false },
                        items = listOf(
                            AppActionItem(
                                text = stringResource(R.string.quicknote_list_import_from_doc),
                                leadingIcon = Icons.Filled.Description,
                                onClick = {
                                    importMenuExpanded = false
                                    pendingAuthTarget = null
                                    viewModel.requestFeishuAuthCheck()
                                }
                            ),
                            AppActionItem(
                                text = stringResource(R.string.quicknote_list_import_from_folder),
                                leadingIcon = Icons.Filled.Folder,
                                onClick = {
                                    importMenuExpanded = false
                                    pendingAuthTarget = "folder"
                                    viewModel.requestFeishuAuthCheck()
                                }
                            )
                        )
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(top = 0.dp)
        ) {
            // ui-redesign-v2 · 填充背景圆角搜索框(胶囊形)
            val cornerRadius = LocalCornerRadius.current
            CapsuleSearchBar(
                query = state.query,
                onQueryChange = viewModel::setQuery,
                placeholder = stringResource(R.string.quicknote_list_search_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md)
                    .padding(top = spacing.xs, bottom = spacing.sm)
            )
            // fix-quicknote-tags-and-search · 当前筛选 banner(仅 selectedTag 非空)
            if (state.selectedTag != null) {
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.md, vertical = spacing.sm / 2),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { viewModel.selectTag(null) },
                        label = {
                            Text(stringResource(R.string.quicknote_list_filter_banner_fmt, state.selectedTag!!))
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { viewModel.selectTag(null) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.quicknote_list_filter_clear_cd),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    )
                }
            }
            if (state.query.isEmpty() && searchHistory.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md)) {
                    Text(
                        text = stringResource(R.string.quicknote_search_history_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(spacing.xs))
                    for (q in searchHistory.take(5)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setQuery(q) }
                                .padding(vertical = spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(q, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    SearchHistoryStore.remove(context, q)
                                    searchHistory = SearchHistoryStore.getAll(context)
                                }
                            }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.common_remove_cd),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
            TagFilterRow(
                allTags = allTags,
                selectedTag = state.selectedTag,
                onTagSelected = viewModel::selectTag
            )
            when (val s = state) {
                NoteListUiState.Loading -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(5) { NoteListSkeleton() }
                    }
                }
                is NoteListUiState.Empty ->
                    EmptyState(
                        onCreateClick = onCreateClick,
                        modifier = Modifier.fillMaxSize()
                    )
                is NoteListUiState.Content -> {
                    // H1 fix: feishuRefsMap 提升到 LazyColumn 外部,避免每 item 创建独立 Flow 订阅
                    val feishuRefsMap by viewModel.feishuRefs.collectAsStateWithLifecycle()
                    // note-list-card-im-style · IM 风格列表:零外间距,卡片完全相邻,
                    // 只靠 1dp 灰线作分界。horizontal 0(贴视窗边)、vertical 0
                    // (贴搜索框下沿到 tab 栏上沿)、spacedBy 0(卡片间无空隙)。
                    LazyColumn(
                        contentPadding = PaddingValues(0.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items = s.notes, key = { it.note.id }) { item ->
                            // note-list-card-redesign · syncStatus 改传 FeishuRefStatus?(原 String?),
                            // 由 NoteRow 内部 stringResource 解析文案 + 配 chip 颜色
                            val syncStatusEnum = feishuRefsMap[item.note.id]?.status
                            // note-list-card-actions · 自管 AnchoredDraggable 露背景按钮
                            // (2026-06-30 真机反馈:SwipeToDismissBox 的 settle 距离 = 整张卡片宽,
                            // 过远;改 Revealed anchor = -buttonWidthPx,只露按钮区)。
                            // 详见 design.md D1。
                            val buttonWidth = 144.dp
                            val density = LocalDensity.current
                            val buttonWidthPx = with(density) { buttonWidth.toPx() }
                            val scope = rememberCoroutineScope()
                            val revealState = remember(item.note.id) {
                                AnchoredDraggableState(
                                    initialValue = RevealState.Settled,
                                    anchors = DraggableAnchors<RevealState> {
                                        RevealState.Settled at 0f
                                        RevealState.Revealed at -buttonWidthPx
                                    },
                                    positionalThreshold = { totalDistance: Float -> totalDistance * 0.4f },
                                    velocityThreshold = { with(density) { 200.dp.toPx() } },
                                    snapAnimationSpec = spring(),
                                    decayAnimationSpec = exponentialDecay(),
                                    confirmValueChange = { true }
                                )
                            }
                            // stringResource 必须在 @Composable 上下文里求值,所以提前提到外面。
                            val pinCd = stringResource(R.string.quicknote_list_swipe_pin_cd)
                            val deleteCd = stringResource(R.string.quicknote_list_swipe_delete_cd)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // note-list-card-im-style · 固定 88dp(非 min),跟 NoteRow
                                    // 高度硬一致,matchParentSize 才能给 swipe 背景按钮传确定高度,
                                    // 左滑按钮底边跟 Divider 零误差对齐
                                    .height(88.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(start = buttonWidth),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SwipeActionButton(
                                        icon = if (item.note.isPinned) {
                                            Icons.Filled.PushPin
                                        } else {
                                            Icons.Outlined.PushPin
                                        },
                                        contentDescription = pinCd,
                                        tint = MaterialTheme.colorScheme.primary,
                                        onClick = {
                                            scope.launch { revealState.animateTo(RevealState.Settled) }
                                            viewModel.togglePinned(item.note.id, item.note.isPinned)
                                        }
                                    )
                                    SwipeActionButton(
                                        icon = Icons.Filled.Delete,
                                        contentDescription = deleteCd,
                                        tint = MaterialTheme.colorScheme.error,
                                        onClick = {
                                            scope.launch { revealState.animateTo(RevealState.Settled) }
                                            confirmDeleteFor = item.note.id
                                        }
                                    )
                                }
                                // 前景层:NoteRow + DropdownMenu,水平 offset 跟 revealState 走。
                                // 卡片本身不消失,露背景时右上 buttonWidth 区空出 → 背景按钮可见。
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .offset {
                                            IntOffset(
                                                x = (revealState.offset ?: 0f).roundToInt(),
                                                y = 0
                                            )
                                        }
                                        .anchoredDraggable(revealState, Orientation.Horizontal)
                                        .semantics(mergeDescendants = true) {
                                            // 露背景时给 TalkBack 提供两个动作入口
                                            // (替代 SwipeToDismissBox 自带的 swipe-to-dismiss a11y)
                                            customActions = listOf(
                                                CustomAccessibilityAction(
                                                    label = pinCd,
                                                    action = {
                                                        scope.launch {
                                                            revealState.animateTo(RevealState.Settled)
                                                        }
                                                        viewModel.togglePinned(
                                                            item.note.id,
                                                            item.note.isPinned
                                                        )
                                                        true
                                                    }
                                                ),
                                                CustomAccessibilityAction(
                                                    label = deleteCd,
                                                    action = {
                                                        scope.launch {
                                                            revealState.animateTo(RevealState.Settled)
                                                        }
                                                        confirmDeleteFor = item.note.id
                                                        true
                                                    }
                                                )
                                            )
                                        }
                                ) {
                                    NoteRow(
                                        title = item.note.title.ifBlank {
                                            item.note.content.take(
                                                com.yy.writingwithai.core.data.model.Note.TITLE_FALLBACK_LEN
                                            )
                                        },
                                        content = item.note.content,
                                        tags = item.tags,
                                        syncStatus = syncStatusEnum,
                                        onClick = { onNoteClick(item.note.id) },
                                        isPinned = item.note.isPinned,
                                        updatedAt = com.yy.writingwithai.feature.quicknote.list.formatUpdatedAt(
                                            item.note.updatedAt
                                        ),
                                        onLongClick = { touchOffset ->
                                            menuExpandedFor = item.note.id
                                            // px → dp，用于 DropdownMenu offset
                                            menuTouchOffset = DpOffset(
                                                x = with(density) { touchOffset.x.toDp() },
                                                y = with(density) { touchOffset.y.toDp() }
                                            )
                                        },
                                        // note-list-thumbnail · 列表首张图片路径,NoteRow
                                        // 内部按 null 决定是否渲染 72dp 缩略图。
                                        firstImagePath = item.firstImagePath
                                    )
                                    // note-list-card-actions · 长按菜单锚定触摸位置
                                    AppActionDropdown(
                                        expanded = menuExpandedFor == item.note.id,
                                        onDismissRequest = { menuExpandedFor = null },
                                        offset = menuTouchOffset,
                                        items = listOf(
                                            AppActionItem(
                                                text = if (item.note.isPinned) {
                                                    stringResource(R.string.quicknote_list_action_unpin)
                                                } else {
                                                    stringResource(R.string.quicknote_list_action_pin)
                                                },
                                                onClick = {
                                                    menuExpandedFor = null
                                                    viewModel.togglePinned(item.note.id, item.note.isPinned)
                                                },
                                                leadingIcon = Icons.Filled.PushPin
                                            ),
                                            AppActionItem(
                                                text = stringResource(R.string.quicknote_list_action_add_tag),
                                                onClick = {
                                                    menuExpandedFor = null
                                                    showAddTagFor = item.note.id
                                                },
                                                leadingIcon = Icons.Filled.LocalOffer
                                            ),
                                            AppActionItem(
                                                text = stringResource(R.string.quicknote_list_action_delete),
                                                onClick = {
                                                    menuExpandedFor = null
                                                    confirmDeleteFor = item.note.id
                                                },
                                                leadingIcon = Icons.Filled.Delete,
                                                isDestructive = true
                                            )
                                        )
                                    )
                                }
                                // note-list-card-divider-out · 卡片底部分割线从 NoteRow 内部
                                // 移到这里(BoxScope.align BottomCenter)。原因:Card 传 children
                                // 的 maxHeight=Infinity,内部 Box(fillMaxSize).fillMaxHeight 在
                                // Infinity 约束下退化为 wrap content → Divider 永远落在 content
                                // 自然末尾(短卡片 ~80dp),跟 heightIn(min=88dp) 兜底的 Card
                                // 实际底部差 8dp,左滑按钮底边停在 Divider 之上(用户最初反馈)。
                                // 外层 Box 有 heightIn 兜底 + 实际高度确定(等于 Card 实际高度),
                                // BoxScope.align(BottomCenter) 把 Divider 锁在 Box 实际底 1dp,
                                // 跟 SwipeActionButton 底边零误差对齐。
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                )
                            }
                        }
                    }
                }
            }

            // note-list-card-actions · 删除确认 dialog
            confirmDeleteFor?.let { noteId ->
                AlertDialog(
                    onDismissRequest = { confirmDeleteFor = null },
                    title = { Text(stringResource(R.string.quicknote_list_delete_confirm_title)) },
                    text = { Text(stringResource(R.string.quicknote_list_delete_confirm_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmDeleteFor = null
                            viewModel.deleteNote(noteId)
                        }) {
                            Text(stringResource(R.string.quicknote_list_delete_confirm_ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDeleteFor = null }) {
                            Text(stringResource(R.string.quicknote_list_delete_confirm_cancel))
                        }
                    }
                )
            }

            // note-list-card-actions · 添加已有标签 dialog
            showAddTagFor?.let { noteId ->
                val currentTags = (state as? NoteListUiState.Content)
                    ?.notes
                    ?.firstOrNull { it.note.id == noteId }
                    ?.tags
                    .orEmpty()
                AddExistingTagDialog(
                    allTags = allTags,
                    currentTags = currentTags,
                    onTagSelected = { tag -> viewModel.addExistingTag(noteId, tag) },
                    onDismiss = { showAddTagFor = null }
                )
            }

            // feishu-import-from-folder · 未授权 dialog
            if (showUnauthorizedDialog) {
                AlertDialog(
                    onDismissRequest = { showUnauthorizedDialog = false },
                    title = { Text(stringResource(R.string.quicknote_list_import_unauthorized_title)) },
                    text = { Text(stringResource(R.string.quicknote_list_import_unauthorized_msg)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showUnauthorizedDialog = false
                            onNavigateToFeishuAuth()
                        }) {
                            Text(stringResource(R.string.quicknote_list_import_unauthorized_go_auth))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUnauthorizedDialog = false }) {
                            Text(stringResource(R.string.quicknote_list_import_unauthorized_cancel))
                        }
                    }
                )
            }

            // feishu-import-from-folder · 单文档导入 dialog
            if (showImportDocDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showImportDocDialog = false
                        importInput = ""
                    },
                    title = { Text(stringResource(R.string.quicknote_list_import_dialog_title)) },
                    text = {
                        val clipboard = LocalClipboardManager.current
                        OutlinedTextField(
                            value = importInput,
                            onValueChange = { importInput = it },
                            label = { Text(stringResource(R.string.quicknote_list_import_dialog_hint)) },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clip = clipboard.getText()?.text.orEmpty()
                                    if (clip.isNotBlank()) importInput = clip
                                }) {
                                    Icon(
                                        Icons.Filled.ContentPaste,
                                        contentDescription = stringResource(
                                            R.string.quicknote_list_import_dialog_paste
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val input = importInput.trim()
                                showImportDocDialog = false
                                importInput = ""
                                if (input.isNotBlank()) viewModel.importSingleDoc(input)
                            },
                            enabled = importInput.isNotBlank()
                        ) {
                            Text(stringResource(R.string.quicknote_list_import_dialog_ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showImportDocDialog = false
                            importInput = ""
                        }) {
                            Text("取消")
                        }
                    }
                )
            }

            // feishu-import-from-folder · 导入结果轻提示
            importToast?.let { msg ->
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(2500)
                    importToast = null
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.md),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = msg,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

/**
 * fix-m6-list-tag-row-horizontal · 标签筛选横向滚动:
 * - 「全部」 chip 固定在 Row 起点(viewport 最左边),任何滚动位置都可点;
 * - 所有具体标签 chip 放在 weight(1f) 的子 Row 内,可横向 scroll,溢出不再换行。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagFilterRow(allTags: List<String>, selectedTag: String?, onTagSelected: (String?) -> Unit) {
    val spacing = LocalSpacing.current
    val tagScroll = rememberScrollState()
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.sm / 2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 固定 chip,viewport 内永远最左可点(不被滚动带走)
        FilterChip(
            selected = selectedTag == null,
            onClick = { onTagSelected(null) },
            label = { Text(stringResource(R.string.quicknote_list_filter_all)) }
        )
        Spacer(modifier = Modifier.size(spacing.sm))
        // 横向可滚的标签集合
        Row(
            modifier =
            Modifier
                .weight(1f, fill = false)
                .horizontalScroll(tagScroll),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm / 2)
        ) {
            allTags.forEach { tag ->
                FilterChip(
                    selected = selectedTag == tag,
                    onClick = { onTagSelected(if (selectedTag == tag) null else tag) },
                    label = { Text("#$tag") },
                    leadingIcon = if (selectedTag == tag) {
                        {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

/**
 * note-list-card-actions · SwipeToDismissBox 背景按钮(置顶 / 删除)。
 * 72dp 宽 + fillMaxHeight 跟卡片同高(注:NoteRow 现在 heightIn(min=88dp),
 * 按钮高度自然等于卡片高度,不再写死 88dp,避免短卡片时按钮上下空隙)。
 */
@Composable
private fun SwipeActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        color = tint.copy(alpha = 0.15f),
        // note-list-card-redesign · 高度由外层 Box(matchParentSize) 提供,
        // 这里 width(72.dp) + fillMaxHeight 让按钮贴满卡片高度
        modifier = Modifier.width(72.dp).fillMaxHeight()
    ) {
        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * ui-redesign-v2 · 胶囊形填充搜索框:surfaceVariant 背景 + xl(24dp) 圆角 + leadingIcon。
 */
@Composable
private fun CapsuleSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val cornerRadius = LocalCornerRadius.current
    val spacing = LocalSpacing.current
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(cornerRadius.xl)
            )
            .padding(horizontal = spacing.sm2, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.size(spacing.sm))
        // M1 fix: 用 decorationBox 模式正确对齐 placeholder 与输入文本
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            }
        )
        if (query.isNotEmpty()) {
            // M9 fix: 移除 Modifier.size(20.dp),恢复 48dp 触摸目标
            IconButton(onClick = { onQueryChange("") }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.quicknote_search_clear_cd),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * ui-redesign-v2 · 品牌感空状态:64dp 大图标 + 品牌文案 + primary CTA 按钮。
 */
@Composable
private fun EmptyState(onCreateClick: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            modifier = Modifier.padding(spacing.lg)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Text(
                text = stringResource(R.string.quicknote_list_empty),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.quicknote_list_empty_cta),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onCreateClick) {
                Text(stringResource(R.string.quicknote_list_fab_new))
            }
        }
    }
}

/**
 * 将笔记 updatedAt(epoch millis)格式化为简短日期字符串。
 * 格式: 同年内 MM-dd HH:mm,跨年 yyyy-MM-dd
 *
 * fix-2026-06-26-review-r3 M2:`SimpleDateFormat` 提到顶层 val,避免每次重组重建。
 * 原实现每 item 重组都 `new SimpleDateFormat(...)`,SimpleDateFormat 构造非轻量(底层
 * Calendar + DateFormatSymbols + Locale 解析),长列表 + 滚动连续重组时影响 frame budget。
 * fix-2026-06-26-review-r3 LOW:Locale.getDefault() 在某些 locale(阿拉伯/泰语)下产出
 * 非 ASCII 数字,fallback 到 Locale.ROOT 保证可读性。用 lazy 延迟计算,因为顶层 val
 * 初始化时 locale 可能未就绪。
 */
private val SHORT_SAME_YEAR_FORMAT by lazy {
    java.text.SimpleDateFormat("MM-dd HH:mm", safeListLocale())
}
private val SHORT_CROSS_YEAR_FORMAT by lazy {
    java.text.SimpleDateFormat("yyyy-MM-dd", safeListLocale())
}

/**
 * fix-2026-06-26-review-r3 LOW:优先用用户 locale,但排除产出非 ASCII 数字的 locale,
 * fallback 到 Locale.ROOT。
 */
private fun safeListLocale(): java.util.Locale {
    val default = java.util.Locale.getDefault()
    val test = java.text.NumberFormat.getInstance(default).format(0)
    return if (test.all { it.isDigit() || it == '-' || it == ',' || it == '.' }) {
        default
    } else {
        java.util.Locale.ROOT
    }
}

private fun formatUpdatedAt(epochMillis: Long): String {
    val now = java.util.Calendar.getInstance()
    val date = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
    val sameYear = now.get(java.util.Calendar.YEAR) == date.get(java.util.Calendar.YEAR)
    val sdf = if (sameYear) SHORT_SAME_YEAR_FORMAT else SHORT_CROSS_YEAR_FORMAT
    return sdf.format(java.util.Date(epochMillis))
}

/**
 * note-list-card-actions · 列表卡片左滑状态机。
 * Settled=卡片闭合,占满行宽;Revealed=卡片左滑 buttonWidth(2 × 72dp),露背景按钮。
 * 详见 design.md D1(自管 anchoredDraggable,Revealed anchor = -buttonWidthPx)。
 */
private enum class RevealState { Settled, Revealed }
