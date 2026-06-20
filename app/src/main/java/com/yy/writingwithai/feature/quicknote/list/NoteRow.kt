@file:Suppress("unused", "FunctionNaming")

package com.yy.writingwithai.feature.quicknote.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalSpacing
import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.core.data.model.NoteWithTags
import java.text.DateFormat
import java.util.Date

/**
 * 列表单行(spec §"List ordering"):左侧 pin 图标、标题、内容预览、tag chip 行、时间。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteRow(item: NoteWithTags, onClick: (noteId: String) -> Unit, modifier: Modifier = Modifier) {
    val note = item.note
    val spacing = LocalSpacing.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        onClick = { onClick(note.id) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            if (note.isPinned) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = stringResource(R.string.quicknote_detail_pin),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier =
                    Modifier
                        .size(16.dp)
                        .padding(top = 2.dp)
                )
                Spacer(Modifier.width(spacing.sm))
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = note.title.ifBlank { note.content.take(Note.TITLE_FALLBACK_LEN) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (note.content.isNotBlank()) {
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (item.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm / 2),
                        modifier = Modifier.padding(top = spacing.sm / 2)
                    ) {
                        item.tags.forEach { tagName ->
                            AssistChip(
                                onClick = { onClick(note.id) },
                                label = { Text("#$tagName") },
                                colors = AssistChipDefaults.assistChipColors()
                            )
                        }
                    }
                }
                Text(
                    text =
                    DateFormat
                        .getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(note.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = spacing.sm / 2)
                )
            }
        }
    }
}

/** spec §"Note entity schema":空 title 时由正文前 30 字派生 — 常量见 [Note.TITLE_FALLBACK_LEN]。 */
