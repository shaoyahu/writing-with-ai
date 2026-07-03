package com.yy.writingwithai.feature.quicknote.detail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
    var aiState by remember { mutableStateOf<AiExtractState>(AiExtractState.Idle) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(noteId) {
        related = noteLinker.getRelated(noteId)
    }

    // ui-redesign-v2 · 外层 Surface 卡片包裹 + 圆角
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
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

            if (related.isEmpty()) {
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
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteLinkCard(note: RelatedNote, onNavigate: (String) -> Unit) {
    Card(
        onClick = { onNavigate(note.noteId) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                // TAG_OVERLAP 不再显示"标签"文字 chip，改为下方直接显示实际标签名
                if (note.signals.contains(LinkType.CONTENT_SIM)) {
                    SignalChip(text = stringResource(R.string.note_association_signal_content))
                }
                if (note.signals.contains(LinkType.ENTITY_HIT)) {
                    SignalChip(text = stringResource(R.string.note_association_signal_entity))
                }
                if (note.signals.contains(LinkType.LLM_EXTRACT)) {
                    SignalChip(text = stringResource(R.string.note_association_signal_ai))
                }
            }
            // 展示共享标签名(TAG_OVERLAP → sharedTags)和共享实体名(ENTITY_HIT → sharedEntities)
            // 标签用 TagChip 样式(圆角背景)，实体用 SignalChip 样式
            val (sharedTags, sharedEntities) = parseSharedItems(note.evidence)
            if (sharedTags.isNotEmpty() || sharedEntities.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 标签用 TagChip 样式
                    sharedTags.take(3).forEach { tag ->
                        TagChip(text = tag)
                    }
                    // 实体用 SignalChip 样式
                    sharedEntities.take(3).forEach { entity ->
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

/**
 * TagChip: 标签专用 chip，带圆角背景色（与列表页 NoteRow 标签样式一致）
 */
@Composable
private fun TagChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
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

/**
 * 从 evidence JSON 中提取共享标签名(sharedTags)和共享实体名(sharedEntities)。
 *
 * TAG_OVERLAP evidence 格式: `{"sharedTags":["科研","AI"]}`
 * ENTITY_HIT evidence 格式: `{"sharedEntities":["M3","Agent"]}`
 * GROUP_CONCAT 拼接后可能同时包含两者，用正则分别提取。
 *
 * 返回 Pair(sharedTags, sharedEntities)，分别用于不同 chip 样式渲染。
 */
private fun parseSharedItems(evidence: String?): Pair<List<String>, List<String>> {
    if (evidence.isNullOrBlank()) return emptyList<String>() to emptyList<String>()

    val tags = mutableListOf<String>()
    val entities = mutableListOf<String>()

    // 匹配 "sharedTags":[...] 和 "sharedEntities":[...]
    val stringPattern = Regex(""""([^"\\]+)""")

    // 提取 sharedTags
    Regex(""""sharedTags"\s*:\s*\[([^]]*)]""").findAll(evidence).forEach { match ->
        stringPattern.findAll(match.groupValues[1]).forEach { s ->
            tags.add(s.groupValues[1])
        }
    }

    // 提取 sharedEntities
    Regex(""""sharedEntities"\s*:\s*\[([^]]*)]""").findAll(evidence).forEach { match ->
        stringPattern.findAll(match.groupValues[1]).forEach { s ->
            entities.add(s.groupValues[1])
        }
    }

    return tags.toList() to entities.toList()
}
