@file:Suppress("FunctionNaming")

package com.yy.writingwithai.feature.my.entity

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalCornerRadius
import com.yy.writingwithai.app.ui.theme.LocalSpacing
import com.yy.writingwithai.core.data.db.dao.entity.EntityListRow
import com.yy.writingwithai.core.note.entity.EntityType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EntityManagementScreen(
    onBack: () -> Unit,
    onEntityClick: (entityKey: String) -> Unit,
    viewModel: EntityManagementViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.entity_management_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (state.selectionMode) {
                        Text(
                            text = "${state.selectedKeys.size}",
                            modifier = Modifier.padding(end = LocalSpacing.current.sm)
                        )
                        IconButton(
                            onClick = { viewModel.requestBatchDelete() },
                            enabled = state.selectedKeys.isNotEmpty()
                        ) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.entity_delete_cd)) }
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = stringResource(R.string.entity_exit_selection_cd)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(LocalSpacing.current.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.search,
                    onValueChange = viewModel::setSearch,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.entity_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true
                )
                Spacer(Modifier.width(LocalSpacing.current.sm))
                var sortExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { sortExpanded = true }) {
                        Icon(Icons.Filled.Sort, contentDescription = stringResource(R.string.entity_sort_cd))
                    }
                    DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                        EntityManagementViewModel.SortMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(sortLabel(mode)) },
                                onClick = {
                                    viewModel.setSort(mode)
                                    sortExpanded = false
                                },
                                leadingIcon = {
                                    if (mode == state.sort) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null
                                        )
                                    } else {
                                        Spacer(Modifier.width(24.dp))
                                    }
                                }
                            )
                        }
                    }
                }
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = LocalSpacing.current.md),
                horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm / 2)
            ) {
                item {
                    FilterChip(selected = state.typeFilter == null, onClick = {
                        viewModel.setTypeFilter(null)
                    }, label = { Text(stringResource(R.string.entity_filter_all)) })
                }
                items(EntityType.values()) { type ->
                    FilterChip(
                        selected = state.typeFilter == type,
                        onClick = { viewModel.setTypeFilter(type) },
                        label = { Text(typeDisplayName(type)) }
                    )
                }
            }
            if (state.entities.isEmpty() && !state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.entity_empty_state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        horizontal = LocalSpacing.current.md,
                        vertical = LocalSpacing.current.sm
                    ),
                    verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm / 2)
                ) {
                    items(state.entities, key = { it.entityKey }) { row ->
                        EntityListItem(
                            row = row,
                            selectionMode = state.selectionMode,
                            selected = row.entityKey in state.selectedKeys,
                            onClick = {
                                if (state.selectionMode) {
                                    viewModel.toggleSelection(
                                        row.entityKey
                                    )
                                } else {
                                    onEntityClick(row.entityKey)
                                }
                            },
                            onLongClick = { viewModel.toggleSelection(row.entityKey) }
                        )
                    }
                }
            }
        }
    }

    if (state.showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::cancelBatchDelete,
            title = { Text(stringResource(R.string.entity_batch_delete_title)) },
            text = { Text(stringResource(R.string.entity_batch_delete_body_fmt, state.selectedKeys.size)) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmBatchDelete
                ) { Text(stringResource(R.string.entity_delete_confirm_ok)) }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::cancelBatchDelete
                ) { Text(stringResource(R.string.entity_delete_confirm_cancel)) }
            }
        )
    }
}

@Composable
private fun sortLabel(mode: EntityManagementViewModel.SortMode): String = when (mode) {
    EntityManagementViewModel.SortMode.NAME -> stringResource(R.string.entity_sort_name)
    EntityManagementViewModel.SortMode.NOTE_COUNT -> stringResource(R.string.entity_sort_note_count)
    EntityManagementViewModel.SortMode.LAST_EXTRACTED -> stringResource(R.string.entity_sort_last_extracted)
}

@Composable
private fun typeDisplayName(type: EntityType): String = stringResource(
    when (type) {
        EntityType.PERSON -> R.string.entity_type_person
        EntityType.WORK -> R.string.entity_type_work
        EntityType.EVENT -> R.string.entity_type_event
        EntityType.LOCATION -> R.string.entity_type_location
        EntityType.ORG -> R.string.entity_type_org
        EntityType.CONCEPT -> R.string.entity_type_concept
        EntityType.DATE -> R.string.entity_type_date
        EntityType.URL -> R.string.entity_type_url
        EntityType.QUOTE -> R.string.entity_type_quote
        EntityType.PRODUCT -> R.string.entity_type_product
        EntityType.TASK -> R.string.entity_type_task
        EntityType.NUMBER -> R.string.entity_type_number
    }
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntityListItem(
    row: EntityListRow,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(LocalCornerRadius.current.md),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ).padding(LocalSpacing.current.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
        ) {
            if (selectionMode) Checkbox(checked = selected, onCheckedChange = { onClick() })
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.surfaceForm,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${typeDisplayName(row.entityType)} · ${row.noteCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
