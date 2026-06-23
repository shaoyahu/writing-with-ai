package com.yy.writingwithai.feature.quicknote.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.R
import com.yy.writingwithai.core.data.db.entity.LinkType
import com.yy.writingwithai.core.note.NoteLinker
import com.yy.writingwithai.core.note.RelatedNote
import kotlinx.coroutines.launch

/** UI state for the AI association extraction triggered by the user. */
sealed interface AiExtractState {
    /** Not running. */
    data object Idle : AiExtractState

    /** AI call is in flight. */
    data object Loading : AiExtractState

    /** AI returned results successfully. [linkCount] is the number of links persisted. */
    data class Success(val linkCount: Int) : AiExtractState

    /** AI call failed. [message] is a short user-facing error. */
    data class Error(val message: String) : AiExtractState
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ExperimentalMaterial3Api")
fun RelatedNotesSection(
    noteId: String,
    noteLinker: NoteLinker,
    onNavigate: (String) -> Unit,
    onAiTrigger: suspend () -> Int,
    showAiButton: Boolean,
    modifier: Modifier = Modifier
) {
    var related by remember { mutableStateOf<List<RelatedNote>>(emptyList()) }
    var backlinks by remember { mutableStateOf<List<RelatedNote>>(emptyList()) }
    var aiState by remember { mutableStateOf<AiExtractState>(AiExtractState.Idle) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(noteId) {
        related = noteLinker.getRelated(noteId)
        backlinks = noteLinker.getBacklinks(noteId)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Section header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Hub,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.note_association_section_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(12.dp))

        if (related.isEmpty() && backlinks.isEmpty()) {
            // Capture Composable-dependent values outside coroutine scope
            val errorUnknown = stringResource(R.string.note_association_ai_error_unknown)
            EmptyState(
                aiState = aiState,
                showAiButton = showAiButton,
                onAiTrigger = {
                    aiState = AiExtractState.Loading
                    scope.launch {
                        try {
                            val count = onAiTrigger()
                            aiState = AiExtractState.Success(count)
                            // Refresh the list after AI extraction
                            related = noteLinker.getRelated(noteId)
                            backlinks = noteLinker.getBacklinks(noteId)
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            aiState = AiExtractState.Error(
                                e.message ?: errorUnknown
                            )
                        }
                    }
                }
            )
        } else {
            if (related.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.note_association_related_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                related.forEach { r -> NoteLinkCard(r, onNavigate) }
                if (backlinks.isNotEmpty()) Spacer(Modifier.height(8.dp))
            }
            if (backlinks.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.note_association_backlinks_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
                backlinks.forEach { r -> NoteLinkCard(r, onNavigate, isBacklink = true) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteLinkCard(note: RelatedNote, onNavigate: (String) -> Unit, isBacklink: Boolean = false) {
    ElevatedCard(
        onClick = { onNavigate(note.noteId) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        note.signals.contains(LinkType.WIKILINK) -> Icons.Filled.Link
                        note.signals.contains(LinkType.TAG_OVERLAP) -> Icons.Filled.LocalOffer
                        note.signals.contains(LinkType.CONTENT_SIM) -> Icons.Filled.Search
                        note.signals.contains(LinkType.ENTITY_HIT) -> Icons.Filled.Tag
                        note.signals.contains(LinkType.LLM_EXTRACT) -> Icons.Filled.AutoAwesome
                        else -> Icons.Filled.Link
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = note.title.ifBlank { stringResource(R.string.quicknote_untitled) },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isBacklink) {
                    Spacer(Modifier.width(8.dp))
                    SignalChip(text = stringResource(R.string.note_association_signal_backlink))
                }
            }
            if (note.preview.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = note.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (note.signals.contains(LinkType.WIKILINK)) {
                    SignalChip(text = stringResource(R.string.note_association_signal_wikilink))
                }
                if (note.signals.contains(LinkType.TAG_OVERLAP)) {
                    SignalChip(text = stringResource(R.string.note_association_signal_tag))
                }
                if (note.signals.contains(LinkType.CONTENT_SIM)) {
                    SignalChip(text = stringResource(R.string.note_association_signal_content))
                }
                if (note.signals.contains(LinkType.ENTITY_HIT)) {
                    SignalChip(text = "Entity")
                }
                if (note.signals.contains(LinkType.LLM_EXTRACT)) {
                    SignalChip(text = stringResource(R.string.note_association_signal_ai))
                }
            }
            val sharedEntities = parseSharedEntities(note.evidence)
            if (sharedEntities.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sharedEntities.take(6).forEach { entity ->
                        SignalChip(text = entity)
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun EmptyState(aiState: AiExtractState, showAiButton: Boolean, onAiTrigger: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon adapts to state
        when (aiState) {
            is AiExtractState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.note_association_ai_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is AiExtractState.Success -> {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (aiState.linkCount > 0) {
                        stringResource(R.string.note_association_ai_success_found, aiState.linkCount)
                    } else {
                        stringResource(R.string.note_association_ai_success_none)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is AiExtractState.Error -> {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = aiState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            is AiExtractState.Idle -> {
                Icon(
                    Icons.Outlined.LinkOff,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.note_association_related_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Show button in Idle or Error state (allow retry)
        if (showAiButton && (aiState is AiExtractState.Idle || aiState is AiExtractState.Error)) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onAiTrigger) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (aiState is AiExtractState.Error) {
                        stringResource(R.string.note_association_ai_retry)
                    } else {
                        stringResource(R.string.note_association_ai_find)
                    }
                )
            }
        }
    }
}

private fun parseSharedEntities(evidence: String?): List<String> {
    if (evidence.isNullOrBlank()) return emptyList()
    val match = Regex(""""sharedEntities"\s*:\s*\[([^]]*)]""").find(evidence) ?: return emptyList()
    return Regex(""""([^"\\]+)""")
        .findAll(match.groupValues[1])
        .map { it.groupValues[1] }
        .toList()
}
