package com.yy.writingwithai.feature.quicknote.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalSpacing
import com.yy.writingwithai.feature.quicknote.model.NoteListUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickNoteListScreen(
    onNoteClick: (id: String) -> Unit,
    onCreateClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onPromptSettingsClick: () -> Unit = {},
    viewModel: QuickNoteListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val allTags: List<String> = (state as? NoteListUiState.Content)?.allTags ?: emptyList()
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quicknote_list_title)) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_title)) },
                            onClick = {
                                menuOpen = false
                                onPromptSettingsClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_data_title)) },
                            onClick = {
                                menuOpen = false
                                onSettingsClick()
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateClick) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.quicknote_list_fab_new)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text(stringResource(R.string.quicknote_list_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
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
                singleLine = true,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.sm)
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
            TagFilterRow(
                allTags = allTags,
                selectedTag = state.selectedTag,
                onTagSelected = viewModel::selectTag
            )
            when (val s = state) {
                NoteListUiState.Loading -> Unit
                is NoteListUiState.Empty ->
                    EmptyState(
                        onCreateClick = onCreateClick,
                        modifier = Modifier.fillMaxSize()
                    )
                is NoteListUiState.Content ->
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items = s.notes, key = { it.note.id }) { item ->
                            NoteRow(
                                item = item,
                                onClick = onNoteClick,
                                onTagClick = { tag -> viewModel.selectTag(tag) }
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
