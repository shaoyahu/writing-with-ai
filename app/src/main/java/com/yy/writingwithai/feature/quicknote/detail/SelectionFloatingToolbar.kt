package com.yy.writingwithai.feature.quicknote.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalCornerRadius
import com.yy.writingwithai.app.ui.theme.LocalSpacing

/**
 * floating-selection-toolbar · 笔记详情页文本选中时浮现的快捷工具栏。
 *
 * 视觉风格沿用 [com.yy.writingwithai.core.ui.dropdown.AppActionDropdown]:
 * - containerColor = surfaceContainerHigh
 * - shape = RoundedCornerShape(cornerRadius.md) = 12dp
 * - shadowElevation = 2dp
 *
 * 包含两个按钮:
 * - ⭐ [加入实体] — 本地功能,始终 enabled
 * - ✨ [AI] — 依赖 provider,未配置时淡灰色不可点击;点击展开 dropdown 列出 6 个 AI 操作
 *
 * @param modifier 父布局传入的 Modifier
 * @param isAiEnabled 是否启用 AI 按钮(已配置 AI provider 时为 true)
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
        shape = RoundedCornerShape(cornerRadius.md),
        color = androidx.compose.ui.graphics.Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            color = androidx.compose.ui.graphics.Color(0xFFE65100)
        ),
        shadowElevation = 8.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = androidx.compose.ui.graphics.Color(0xFFFFE0B2)
                )
                .padding(
                    horizontal = spacing.xs,
                    vertical = spacing.xs2
                ),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            // ⭐ 加入实体(本地功能,始终可点击)
            FilledTonalButton(onClick = onAddEntity) {
                Icon(
                    imageVector = Icons.Outlined.StarOutline,
                    contentDescription = null,
                    modifier = Modifier.width(18.dp)
                )
                Spacer(modifier = Modifier.width(spacing.xs))
                Text(
                    text = stringResource(R.string.selection_toolbar_add_entity),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            // ✨ AI 操作(依赖 provider;未配置时禁用)
            // fix-2026-07-05-review-r4 MEDIUM M8:DropdownMenu 应作为 Button 的兄弟节点而非嵌套在 content 内
            Box {
                FilledTonalButton(
                    onClick = { if (isAiEnabled) aiMenuExpanded = true },
                    enabled = isAiEnabled
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.width(18.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.xs))
                    Text(
                        text = stringResource(R.string.selection_toolbar_ai),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                DropdownMenu(
                    expanded = aiMenuExpanded,
                    onDismissRequest = { aiMenuExpanded = false }
                ) {
                    AiMenuItem(
                        text = stringResource(R.string.selection_toolbar_ai_expand),
                        onClick = {
                            aiMenuExpanded = false
                            onAiExpand()
                        }
                    )
                    AiMenuItem(
                        text = stringResource(R.string.selection_toolbar_ai_polish),
                        onClick = {
                            aiMenuExpanded = false
                            onAiPolish()
                        }
                    )
                    AiMenuItem(
                        text = stringResource(R.string.selection_toolbar_ai_organize),
                        onClick = {
                            aiMenuExpanded = false
                            onAiOrganize()
                        }
                    )
                    AiMenuItem(
                        text = stringResource(R.string.selection_toolbar_ai_summarize),
                        onClick = {
                            aiMenuExpanded = false
                            onAiSummarize()
                        }
                    )
                    AiMenuItem(
                        text = stringResource(R.string.selection_toolbar_ai_translate),
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

@Composable
private fun AiMenuItem(text: String, onClick: () -> Unit, leadingIcon: ImageVector? = null) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge
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
