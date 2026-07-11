package com.yy.writingwithai.feature.quicknote.graph

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

/**
 * note-graph-view · 图屏入口(tasks §3.1)。
 *
 * 状态机:
 * - `Empty` → 居中 `Surface(surfaceVariant)` 文案
 * - `Loading` → `CircularProgressIndicator`
 * - `Loaded` → `NoteGraphCanvas(快照 + 布局)`
 * - `Error` → 居中 `Text`(错误信息)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteGraphScreen(
    noteId: String,
    onBack: () -> Unit,
    onNodeTap: (String) -> Unit,
    viewModel: NoteGraphViewModel = hiltViewModel<NoteGraphViewModel>()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // load once per noteId
    remember(noteId) { viewModel.loadSnapshot(noteId) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.note_graph_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    var legendExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { legendExpanded = true }) {
                        Icon(Icons.Filled.Info, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = legendExpanded,
                        onDismissRequest = { legendExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.note_graph_legend_related)) },
                            onClick = { legendExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.note_graph_legend_entity)) },
                            onClick = { legendExpanded = false }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            when (val s = uiState) {
                is GraphUiState.Empty -> EmptyGraphBlock()
                is GraphUiState.Loading -> CircularProgressIndicator()
                is GraphUiState.Loaded -> NoteGraphCanvas(
                    snapshot = s.snapshot,
                    coords = s.coords,
                    onNodeTap = onNodeTap
                )
                is GraphUiState.Error -> Text(
                    text = s.message,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyGraphBlock() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.wrapContentSize()
    ) {
        Text(
            text = stringResource(R.string.note_graph_empty),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
