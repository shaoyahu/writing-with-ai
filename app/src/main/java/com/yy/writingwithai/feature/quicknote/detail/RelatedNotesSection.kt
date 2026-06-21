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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.LinkOff
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ExperimentalMaterial3Api")
fun RelatedNotesSection(
    noteId: String,
    noteLinker: NoteLinker,
    onNavigate: (String) -> Unit,
    onAiTrigger: () -> Unit,
    showAiButton: Boolean,
    modifier: Modifier = Modifier
) {
    var related by remember { mutableStateOf<List<RelatedNote>>(emptyList()) }
    var backlinks by remember { mutableStateOf<List<RelatedNote>>(emptyList()) }
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
            EmptyState(onAiTrigger = onAiTrigger, showAiButton = showAiButton)
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
                if (note.signals.contains(LinkType.LLM_EXTRACT)) {
                    SignalChip(text = stringResource(R.string.note_association_signal_ai))
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
private fun EmptyState(onAiTrigger: () -> Unit, showAiButton: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
        if (showAiButton) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onAiTrigger) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.note_association_ai_find))
            }
        }
    }
}
