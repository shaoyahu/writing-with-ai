package com.yy.writingwithai.feature.quicknote.list

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
import androidx.compose.material3.OutlinedTextField
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
import com.yy.writingwithai.app.ui.theme.LocalSpacing
import com.yy.writingwithai.core.prefs.SearchHistoryStore
import com.yy.writingwithai.core.ui.NoteListSkeleton
import com.yy.writingwithai.feature.quicknote.model.NoteListUiState
import kotlinx.coroutines.launch

/**
 * app-bottom-tab-bar · 笔记 tab 根屏。
 *
 * 瘦身记录:
 * - 删 `MoreVert` + `DropdownMenu`(整段)
 * - 删 `Scaffold.floatingActionButton`(职责上移 `AppShell` 中央 FAB)
 * - 删 `exportAllLauncher`(导出迁入 `SettingsDataScreen`)
 * - 删 `onSettingsClick` / `onPromptSettingsClick` 形参(入口下移"我的" tab)
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
    var searchHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
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
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text(stringResource(R.string.quicknote_list_search_hint)) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = stringResource(R.string.quicknote_list_search_hint)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md)
                    .padding(top = 4.dp, bottom = spacing.sm),
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.quicknote_search_clear_cd)
                            )
                        }
                    }
                },
                singleLine = true
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
                is NoteListUiState.Content ->
                    LazyColumn(
                        // review r1 L1 修:删底部 80dp padding(FAB 已移走,不再需要让位)。
                        contentPadding = PaddingValues(bottom = 0.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items = s.notes, key = { it.note.id }) { item ->
                            val feishuRefsMap = viewModel.feishuRefs.collectAsStateWithLifecycle().value
                            NoteRow(
                                item = item,
                                onClick = onNoteClick,
                                onTagClick = { tag -> viewModel.selectTag(tag) },
                                feishuStatus = feishuRefsMap[item.note.id]?.status
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

@Composable
private fun EmptyState(onCreateClick: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            modifier = Modifier.padding(spacing.lg)
        ) {
            Text(
                text = stringResource(R.string.quicknote_list_empty),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.quicknote_list_empty_cta),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Button(onClick = onCreateClick) {
                Text(stringResource(R.string.quicknote_list_fab_new))
            }
        }
    }
}
