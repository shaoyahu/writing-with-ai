package com.yy.writingwithai.feature.settings.animation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.core.ui.AnimatedSwitch

/**
 * animation-switch-redesign-followup §1:独立开关行,顶层(非 private)共享。
 *
 * 左侧标题 + 描述,右侧 [AnimatedSwitch]。
 *
 * - `enabled = false` 时(如 reduce-motion 场景):开关保持 OFF 视觉
 *   (`AnimatedSwitch` 内部 `colors.disabled*`),`onCheckedChange` 不触发
 *   (`Modifier.clickable(enabled = false)`)。
 * - 调用方自行决定 `checked` 初值来源 —— 默认走 `UserPrefsStore.flow` 持久化值。
 *
 * 复用方:
 * - `AnimationDetailScreen`(主消费者):渲染 nav/tab 2 toggle。
 * - 其它未来动画页面可按需复用。
 */
@Composable
fun AnimationToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}
