package com.yy.writingwithai.feature.quicknote.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.app.ui.theme.LocalCornerRadius
import com.yy.writingwithai.app.ui.theme.LocalSpacing

/**
 * ui-redesign-v2 · 笔记行组件:Card 改为 border-card(elevation=0 + 1dp outlineVariant border + md 12dp 圆角),
 * 左侧 3dp 彩色竖条(tag 色或 primary)。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun NoteRow(
    title: String,
    content: String,
    tags: List<String>,
    syncStatus: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPinned: Boolean = false,
    updatedAt: String? = null,
    // note-list-card-actions · 长按回调,默认空,保持向后兼容
    onLongClick: () -> Unit = {}
) {
    val spacing = LocalSpacing.current
    val cornerRadius = LocalCornerRadius.current
    // M3 fix: remember(tags) 缓存颜色,避免每次重组重新计算 Color.hsl()
    val isDark = isSystemInDarkTheme()
    // fix-review-r4:tagAccentColor 在 remember{} 内调用,不是 @Composable 上下文,
    // 不能在里面读 MaterialTheme。把 primary 颜色从 Composable 上下文传入。
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = remember(tags, isDark) { tagAccentColor(tags, isDark, primaryColor) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            // note-list-card-actions · combinedClickable 同时支持单击进详情 + 长按弹菜单
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(cornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        // M8 fix: 用 IntrinsicSize.Min 让竖条高度跟随内容
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
        ) {
            // 左侧彩色竖条
            Spacer(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = cornerRadius.md, bottomStart = cornerRadius.md))
                    .background(accentColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = spacing.md, vertical = spacing.sm)
            ) {
                // M4 fix: 恢复 isPinned 指示器 + updatedAt 时间戳
                if (isPinned || updatedAt != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        if (isPinned) {
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (updatedAt != null) {
                            Text(
                                text = updatedAt,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(spacing.xs))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (content.isNotBlank()) {
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(spacing.xs))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        tags.take(3).forEach { tag ->
                            // M5 fix: SuggestionChip 改为非交互式 Text+背景,避免误导可点击
                            Text(
                                text = tag,
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
                        if (tags.size > 3) {
                            Text(
                                text = "+${tags.size - 3}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(start = spacing.xs)
                            )
                        }
                    }
                }
                if (syncStatus != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = syncStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 从第一个 tag 名推导竖条颜色:tag 为空则用 primary。
 * 暗色模式用更高 lightness 保证对比度。
 *
 * fix-2026-06-26-review-r3 M10:用 `kotlin.math.abs` 替代 `mod(360f)` 把 hash 映射到
 * `[0, 360)` 区间。原 `Int.mod(Float)` 实现对负数先做 `%`,再用 `let { if (it < 0) ... }`
 * 二次校正,语义上等价但读起来绕;改写为单次无分支映射更直观。同时把 `hashCode` 先
 * 转 `UInt` 再取模,避免 `Int.MIN_VALUE` 在 `% 360` 时被解释成负数后再校正。
 *
 * fix-2026-06-27-review-r4 M11:空 tag 不再用 hardcoded hex,统一走
 * `MaterialTheme.colorScheme.primary`,跟暗色/亮色主题自动适配。
 */
private fun tagAccentColor(tags: List<String>, isDark: Boolean, primaryColor: Color): Color {
    if (tags.isEmpty()) {
        return primaryColor
    }
    val rawHash = tags.first().hashCode()
    // UInt 转换 → 无符号 32-bit → % 360 → [0, 360)
    val hue = (rawHash.toUInt() % 360u).toFloat()
    val lightness = if (isDark) 0.6f else 0.45f
    return Color.hsl(hue, 0.6f, lightness)
}
