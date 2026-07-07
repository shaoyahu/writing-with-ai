@file:Suppress("FunctionNaming")

package com.yy.writingwithai.feature.my.entity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityDetailScreen(
    entityKey: String,
    onBack: () -> Unit,
    onNavigateToNote: (noteId: String) -> Unit,
    viewModel: EntityDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(entityKey) { viewModel.load(entityKey) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.surfaceForm.ifBlank { stringResource(R.string.entity_detail_title) }) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.requestDelete()
                    }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.entity_delete_cd)) }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(LocalSpacing.current.md),
                horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
            ) {
                val sourceLabel = if (state.source == "USER_ADDED") {
                    stringResource(
                        R.string.entity_source_user_added
                    )
                } else {
                    stringResource(R.string.entity_source_ai_extracted)
                }
                Text(text = sourceLabel, style = MaterialTheme.typography.labelMedium)
                Text(
                    text = " · ${state.noteCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.notes.isEmpty() && !state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.entity_detail_no_associated),
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
                    items(state.notes, key = { it.noteId }) { note ->
                        Card(
                            onClick = { onNavigateToNote(note.noteId) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(LocalCornerRadius.current.md),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(LocalSpacing.current.md)) {
                                Text(
                                    text = note.title.ifBlank {
                                        note.noteId.take(8)
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (note.preview.isNotBlank()) {
                                    Text(
                                        text = note.preview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.entity_detail_delete_title)) },
            text = { Text(stringResource(R.string.entity_detail_delete_body_fmt, state.surfaceForm)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(stringResource(R.string.entity_delete_confirm_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) {
                    Text(stringResource(R.string.entity_delete_confirm_cancel))
                }
            }
        )
    }
}
