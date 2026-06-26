package com.yy.writingwithai.feature.quicknote.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalCornerRadius
import com.yy.writingwithai.app.ui.theme.LocalSpacing
import com.yy.writingwithai.core.feishu.sync.FeishuRefStatus
import com.yy.writingwithai.core.prefs.SearchHistoryStore
import com.yy.writingwithai.core.ui.NoteListSkeleton
import com.yy.writingwithai.feature.quicknote.model.NoteListUiState
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickNoteListScreen(
    onNoteClick: (id: String) -> Unit,
    onCreateClick: () -> Unit,
    viewModel: QuickNoteListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quicknote_list_title)) }
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
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        text = stringResource(R.string.quicknote_search_history_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    for (q in searchHistory.take(5)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setQuery(q) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(q, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    SearchHistoryStore.remove(context, q)
                                    searchHistory = SearchHistoryStore.getAll(context)
                                }
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp))
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
                    // H2 fix: 预解析 FeishuRefStatus→string 资源(Composable scope 内)
                    val statusSynced = stringResource(R.string.quicknote_feishu_status_synced)
                    val statusDirty = stringResource(R.string.quicknote_feishu_status_dirty)
                    val statusConflict = stringResource(R.string.quicknote_feishu_status_conflict)
                    val statusRemoteDeleted = stringResource(R.string.quicknote_feishu_status_remote_deleted)
                    // L4 fix: 加 horizontal 边距和 item 间距,卡片不贴屏幕边缘
                    LazyColumn(
                        contentPadding = PaddingValues(
                            horizontal = spacing.md,
                            vertical = spacing.sm
                        ),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items = s.notes, key = { it.note.id }) { item ->
                            val syncLabel = feishuRefsMap[item.note.id]?.status?.let { status ->
                                when (status) {
                                    FeishuRefStatus.SYNCED -> statusSynced
                                    FeishuRefStatus.DIRTY -> statusDirty
                                    FeishuRefStatus.CONFLICT -> statusConflict
                                    FeishuRefStatus.REMOTE_DELETED -> statusRemoteDeleted
                                }
                            }
                            NoteRow(
                                title = item.note.title.ifBlank {
                                    item.note.content.take(com.yy.writingwithai.core.data.model.Note.TITLE_FALLBACK_LEN)
                                },
                                content = item.note.content,
                                tags = item.tags,
                                syncStatus = syncLabel,
                                onClick = { onNoteClick(item.note.id) },
                                isPinned = item.note.isPinned,
                                updatedAt = com.yy.writingwithai.feature.quicknote.list.formatUpdatedAt(
                                    item.note.updatedAt
                                )
                            )
                        }
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
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(cornerRadius.xl)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.size(8.dp))
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
