@file:Suppress("unused", "FunctionNaming")

package com.yy.writingwithai.feature.quicknote.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalSpacing
import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.core.data.model.NoteWithTags
import com.yy.writingwithai.core.feishu.sync.FeishuRefStatus
import java.text.DateFormat
import java.util.Date

/**
 * 列表单行(spec §"List ordering"):左侧 pin 图标、标题、标题右侧最多 2 个 tag chip、正文预览、时间。
 *
 * fix-m6-note-row-tag-inline · tag 从第二行 FlowRow 改为标题右侧内联小 chip,
 * 高度与 titleMedium 接近;超过 [MAX_INLINE_TAGS] 个的 tag 不再显示,只追加「+N」。
 */
private const val MAX_INLINE_TAGS = 2

@Composable
fun NoteRow(
    item: NoteWithTags,
    onClick: (noteId: String) -> Unit,
    onTagClick: (String) -> Unit = {},
    feishuStatus: FeishuRefStatus? = null,
    modifier: Modifier = Modifier
) {
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
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm)
        ) {
            // 标题行:pin + 标题(weight=1f,塞不下 ellipsis) + 最多 2 个 tag + 溢出 +N
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.isPinned) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = stringResource(R.string.quicknote_detail_pin),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(spacing.sm))
                }
                Text(
                    text = note.title.ifBlank { note.content.take(Note.TITLE_FALLBACK_LEN) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                // feishu-bidir-sync:飞书同步状态 chip
                if (feishuStatus != null) {
                    Spacer(Modifier.width(spacing.xs))
                    val (labelRes, chipColor) = when (feishuStatus) {
                        FeishuRefStatus.SYNCED ->
                            R.string.quicknote_feishu_status_synced to MaterialTheme.colorScheme.outlineVariant
                        FeishuRefStatus.DIRTY ->
                            R.string.quicknote_feishu_status_dirty to MaterialTheme.colorScheme.tertiary
                        FeishuRefStatus.CONFLICT ->
                            R.string.quicknote_feishu_status_conflict to MaterialTheme.colorScheme.error
                        FeishuRefStatus.REMOTE_DELETED ->
                            R.string.quicknote_feishu_status_remote_deleted to MaterialTheme.colorScheme.outlineVariant
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(chipColor)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                if (item.tags.isNotEmpty()) {
                    Spacer(Modifier.width(spacing.sm))
                    item.tags.take(MAX_INLINE_TAGS).forEach { tagName ->
                        InlineTagChip(tagName = tagName, onClick = { onTagClick(tagName) })
                        Spacer(Modifier.width(spacing.xs))
                    }
                    val overflow = item.tags.size - MAX_INLINE_TAGS
                    if (overflow > 0) {
                        Text(
                            text = "+$overflow",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (note.content.isNotBlank()) {
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
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

/**
 * 标题右侧内联 tag chip:小高度,labelSmall 字号,与 titleMedium 同字级基线对齐,
 * 走 secondaryContainer 背景;点击触发 [onClick]。
 */
@Composable
private fun InlineTagChip(tagName: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 8.dp, vertical = 1.dp)
    ) {
        Text(
            text = "#$tagName",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1
        )
    }
}

/** spec §"Note entity schema":空 title 时由正文前 30 字派生 — 常量见 [Note.TITLE_FALLBACK_LEN]。 */
