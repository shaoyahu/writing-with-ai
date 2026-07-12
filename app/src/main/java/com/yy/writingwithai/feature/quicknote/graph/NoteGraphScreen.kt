package com.yy.writingwithai.feature.quicknote.graph

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
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
import com.yy.writingwithai.core.note.graph.GraphSnapshot
import com.yy.writingwithai.core.note.graph.NodeCoords

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

    // improve-note-graph-readability D3:副标题需要 snapshot 节点/边计数,在 TopAppBar 内取值。
    // 副标题在 Loaded 才有意义,其它态用 null 走默认"关联图"单行布局。
    val headerSnapshot = (uiState as? GraphUiState.Loaded)?.snapshot

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.note_graph_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (headerSnapshot != null) {
                            val subtitle = if (headerSnapshot.nodes.size == 1) {
                                stringResource(
                                    R.string.note_graph_header_node_count_singular,
                                    headerSnapshot.edges.size
                                )
                            } else {
                                stringResource(
                                    R.string.note_graph_header_node_count_fmt,
                                    headerSnapshot.nodes.size,
                                    headerSnapshot.edges.size
                                )
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    var legendExpanded by remember { mutableStateOf(false) }
                    val legendTitle = stringResource(R.string.note_graph_legend_title)
                    val legendHeader = stringResource(R.string.note_graph_legend_header)
                    IconButton(onClick = { legendExpanded = true }) {
                        Icon(Icons.Filled.Info, contentDescription = legendTitle)
                    }
                    DropdownMenu(
                        expanded = legendExpanded,
                        onDismissRequest = { legendExpanded = false }
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = legendHeader,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
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
                is GraphUiState.Loaded -> NoteGraphLoadedContent(
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
private fun NoteGraphLoadedContent(
    snapshot: GraphSnapshot,
    coords: Map<String, NodeCoords>,
    onNodeTap: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        NoteGraphCanvas(
            snapshot = snapshot,
            coords = coords,
            onNodeTap = onNodeTap
        )
        // improve-note-graph-readability D4:节点数 ≤ 2 时,BottomCenter 引导 banner 取代
        // entity chip 浮层,告诉用户这是关联图 + 可点击 / 缩放。
        if (snapshot.nodes.size <= 2) {
            NoteGraphGuidanceBanner(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

/** improve-note-graph-readability D4:首次引导 banner,Surface + 一行 labelMedium。 */
@Composable
private fun NoteGraphGuidanceBanner(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.note_graph_guidance_banner),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

/**
 * improve-note-graph-readability D5:空态可执行改造。
 * - Icon(Icons.Filled.AccountTree,48dp,onSurfaceVariant 着色)
 * - 标题(text + titleMedium) + 说明(bodyMedium + onSurfaceVariant + top=8dp)
 * - 整体水平居中,padding 24dp
 */
@Composable
private fun EmptyGraphBlock() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.wrapContentSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AccountTree,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = stringResource(R.string.note_graph_empty_actionable_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = stringResource(R.string.note_graph_empty_actionable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
