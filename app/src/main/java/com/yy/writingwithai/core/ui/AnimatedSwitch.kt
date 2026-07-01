@file:Suppress("FunctionNaming")

package com.yy.writingwithai.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.core.ui.animation.LocalAnimationTokens
import kotlin.math.roundToInt

/**
 * animation-system · token-aware `Switch` 封装(spec §REQ 5)。
 *
 * 与 Material3 `Switch` 的差异:
 * - thumbX 位置动画 spec 走 [LocalAnimationTokens.current.switchSpec]，而不是 Material3 内部
 *   默认 spring(stiffness=1600)。NONE 风格时 spec = `snap()`,thumb 即时跳到目标位置(满足
 *   spec §REQ 5 Scenario "AnimatedSwitch under NONE style 时 thumb SHALL snap")。
 * - 视觉跟 Material3 Switch 一致:52×32dp 轨道 + 16dp 圆形 thumb，沿 x 轴 ±10dp 滑动;
 *   颜色通过传入 [colors](默认 [SwitchDefaults.colors])拿 checked / unchecked 主题色。
 *
 * a11y:继承 `Modifier.toggleable(role = Switch)`,TalkBack 念"Switch, on/off"。
 */
@Composable
fun AnimatedSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // ux-2026-06-28 #5:默认值针对 surfaceVariant 背景加强对比 — M3 SwitchDefaults 的
    // uncheckedTrackColor 在浅色 surfaceVariant 上几乎不可见，这里用 outline 颜色兜底。
    colors: SwitchColors = SwitchDefaults.colors(
        uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        uncheckedThumbColor = MaterialTheme.colorScheme.surface
    )
) {
    val tokens = LocalAnimationTokens.current
    val trackColor: Color = if (checked) {
        if (enabled) colors.checkedTrackColor else colors.disabledCheckedTrackColor
    } else {
        if (enabled) colors.uncheckedTrackColor else colors.disabledUncheckedTrackColor
    }
    val thumbColor: Color = if (checked) {
        if (enabled) colors.checkedThumbColor else colors.disabledCheckedThumbColor
    } else {
        if (enabled) colors.uncheckedThumbColor else colors.disabledUncheckedThumbColor
    }

    // thumbX:0 (unchecked) ↔ 1 (checked)，动画 spec 来自 token。
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tokens.switchSpec,
        label = "AnimatedSwitch.thumbProgress"
    )

    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(trackColor)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { onCheckedChange?.invoke(it) }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // thumb 沿 x 轴 ±10dp 滑动;需 dp→px 转换以适配不同 density 设备。
        val travelPx = with(LocalDensity.current) { 20.dp.roundToPx() }
        val offsetPx = (progress * travelPx).roundToInt()
        Box(
            modifier = Modifier
                .size(24.dp)
                .offset { IntOffset(x = offsetPx, y = 0) }
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}
