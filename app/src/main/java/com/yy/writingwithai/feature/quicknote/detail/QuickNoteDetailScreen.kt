package com.yy.writingwithai.feature.quicknote.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalSpacing
import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.feature.quicknote.model.NoteDetailUiState
import com.yy.writingwithai.feature.quicknote.share.shareNoteMarkdown

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickNoteDetailScreen(
    onBack: () -> Unit,
    onEdit: (id: String) -> Unit,
    onDeleted: () -> Unit,
    viewModel: QuickNoteDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

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
                    val current = state as? NoteDetailUiState.Content
                    if (current != null) {
                        IconButton(onClick = { onEdit(current.note.note.id) }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.quicknote_detail_edit),
                            )
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (current.note.note.isPinned) {
                                            stringResource(R.string.quicknote_detail_unpin)
                                        } else {
                                            stringResource(R.string.quicknote_detail_pin)
                                        },
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (current.note.note.isPinned) {
                                            Icons.Filled.PushPin
                                        } else {
                                            Icons.Outlined.PushPin
                                        },
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.togglePinned()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.quicknote_detail_share)) },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    context.shareNoteMarkdown(current.note.note)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.quicknote_detail_delete)) },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                },
            )
        },
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
                            .padding(LocalSpacing.current.lg),
                )
            is NoteDetailUiState.Content ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(LocalSpacing.current.md)
                            .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = s.note.note.title.ifBlank { s.note.note.content.take(Note.TITLE_FALLBACK_LEN) },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    if (s.note.tags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm / 2),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = LocalSpacing.current.sm),
                        ) {
                            s.note.tags.forEach { tag ->
                                AssistChip(onClick = {}, label = { Text("#$tag") })
                            }
                        }
                    }
                    SelectionContainer {
                        Text(
                            text = s.note.note.content,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = LocalSpacing.current.sm),
                        )
                    }
                    Text(
                        text =
                            "${context.getString(R.string.quicknote_detail_word_count_fmt, s.wordCount)}" +
                                context.getString(R.string.quicknote_detail_word_time_separator) +
                                context.getString(R.string.quicknote_detail_read_time_fmt, s.readMinutes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = LocalSpacing.current.md),
                    )
                }
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
            },
        )
    }
}
