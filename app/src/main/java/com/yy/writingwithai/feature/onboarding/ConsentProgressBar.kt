@file:Suppress("FunctionNaming")

package com.yy.writingwithai.feature.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.yy.writingwithai.core.ui.animation.LocalAnimationTokens

/**
 * animation-system-and-consent-redesign §8.2:滚动进度条。
 *
 * - 显示条款 LazyColumn 的滚动比例 0→1
 * - 用 [animateFloatAsState] 接 [LocalAnimationTokens.current.listItemSpec]，平滑过渡进度
 * - 颜色跟随 MaterialTheme.primary(品牌色)
 */
@Composable
fun ConsentProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val tokens = LocalAnimationTokens.current
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tokens.listItemSpec,
        label = "ConsentProgressBar.progress"
    )
    LinearProgressIndicator(
        progress = { animated },
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
    )
}
