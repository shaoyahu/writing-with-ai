package com.yy.writingwithai.feature.quicknote.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalCornerRadius
import com.yy.writingwithai.app.ui.theme.LocalSpacing

/**
 * floating-selection-toolbar · 笔记详情页文本选中时浮现的快捷工具栏。
 *
 * 视觉规则(floating-toolbar-redesign):
 * - 外层 Surface 用 `colorScheme.surfaceContainerHigh` + `tonalElevation = 6.dp` + `shadowElevation = 8.dp`,
 *   不画 BorderStroke(tonalElevation 已表达浮层)。
 * - 形状:`RoundedCornerShape(cornerRadius.xl)` = 24dp(浮层比卡片更圆)。
 * - 操作区:IconButton + 下方 `Text(labelSmall)`,不用 FilledTonalButton(避免视觉过重)。
 * - "AI" 触发:AssistChip(默认色),展开后 DropdownMenu 5 项各带 leading icon。
 * - 已添加态:仅切 icon(Filled.Star vs StarOutline)+ tint(primary),不变底色。
 *
 * @param modifier 父布局传入的 Modifier
 * @param isAiEnabled 是否启用 AI 按钮(已配置 AI provider 时为 true)
 * @param isEntityAdded 当前选区对应的文字是否已经作为实体存在;true 时显示实心星 + primary
 * @param onAddEntity 用户点击"加入实体"回调
 * @param onAiExpand AI 扩写
 * @param onAiPolish AI 润色
 * @param onAiOrganize AI 整理
 * @param onAiSummarize AI 摘要
 * @param onAiTranslate AI 翻译
 */
@Composable
fun SelectionFloatingToolbar(
    modifier: Modifier = Modifier,
    isAiEnabled: Boolean,
    isEntityAdded: Boolean,
    onAddEntity: () -> Unit,
    onAiExpand: () -> Unit,
    onAiPolish: () -> Unit,
    onAiOrganize: () -> Unit,
    onAiSummarize: () -> Unit,
    onAiTranslate: () -> Unit
) {
    val cornerRadius = LocalCornerRadius.current
    val spacing = LocalSpacing.current

    var aiMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius.xl),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ⭐ 加入实体:icon 左 label 右,与 AI 按钮等宽 (Modifier.weight(1f))
            ActionPill(
                icon = if (isEntityAdded) Icons.Filled.Star else Icons.Outlined.StarOutline,
                iconTint = if (isEntityAdded) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                label = stringResource(R.string.selection_toolbar_add_entity),
                onClick = onAddEntity,
                modifier = Modifier.weight(1f)
            )
            // ✨ AI:同样的 ActionPill,点击展开 DropdownMenu
            Box(modifier = Modifier.weight(1f)) {
                ActionPill(
                    icon = Icons.Outlined.AutoAwesome,
                    iconTint = if (isAiEnabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = stringResource(R.string.selection_toolbar_ai),
                    onClick = { if (isAiEnabled) aiMenuExpanded = true },
                    enabled = isAiEnabled,
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = aiMenuExpanded,
                    onDismissRequest = { aiMenuExpanded = false }
                ) {
                    AiMenuItem(
                        text = stringResource(R.string.selection_toolbar_ai_expand),
                        leadingIcon = Icons.AutoMirrored.Outlined.ShortText,
                        onClick = {
                            aiMenuExpanded = false
                            onAiExpand()
                        }
                    )
                    AiMenuItem(
                        text = stringResource(R.string.selection_toolbar_ai_polish),
                        leadingIcon = Icons.Outlined.Brush,
                        onClick = {
                            aiMenuExpanded = false
                            onAiPolish()
                        }
                    )
                    AiMenuItem(
                        text = stringResource(R.string.selection_toolbar_ai_organize),
                        leadingIcon = Icons.Outlined.AccountTree,
                        onClick = {
                            aiMenuExpanded = false
                            onAiOrganize()
                        }
                    )
                    AiMenuItem(
                        text = stringResource(R.string.selection_toolbar_ai_summarize),
                        leadingIcon = Icons.Outlined.Summarize,
                        onClick = {
                            aiMenuExpanded = false
                            onAiSummarize()
                        }
                    )
                    AiMenuItem(
                        text = stringResource(R.string.selection_toolbar_ai_translate),
                        leadingIcon = Icons.Outlined.Translate,
                        onClick = {
                            aiMenuExpanded = false
                            onAiTranslate()
                        }
                    )
                }
            }
        }
    }
}

/**
 * DropdownMenu 项:leading icon + label(text style = bodyMedium,与 MenuItem 默认字号对齐)。
 */
@Composable
private fun AiMenuItem(text: String, leadingIcon: ImageVector? = null, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        onClick = onClick,
        leadingIcon = if (leadingIcon != null) {
            {
                Icon(imageVector = leadingIcon, contentDescription = null)
            }
        } else {
            null
        }
    )
}

/**
 * 工具栏里的"icon 左 / label 右"按钮(Surface 风格 + clickable)。
 * 与同 Row 中其它 ActionPill 等宽(由父 Row 配 Modifier.weight(1f))。
 */
@Composable
private fun ActionPill(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val cornerRadius = LocalCornerRadius.current
    val spacing = LocalSpacing.current
    val textColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.lg))
            .clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) iconTint else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(spacing.xs))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                maxLines = 1
            )
        }
    }
}
