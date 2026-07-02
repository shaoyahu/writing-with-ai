package com.yy.writingwithai.feature.settings.animation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.R

/**
 * animation-switch-redesign-followup §5.2:reduce-motion 系统级提示 Banner,顶层(非 private)共享。
 *
 * 视觉:tertiaryContainer 圆角 Surface + bodySmall 文案。
 *
 * 复用方:
 * - `AnimationStylePreviewScreen`(风格选择页):条件渲染,告诉用户 Theme 仍会强切 NONE。
 * - `AnimationDetailScreen`(细分开关页):条件渲染,告诉用户开关 disabled 但持久化值保留。
 */
@Composable
fun ReduceMotionBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Text(
            text = stringResource(R.string.anim_style_reduce_motion_banner),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp)
        )
    }
}
