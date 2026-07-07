package com.yy.writingwithai.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/** fix-2026-06-26-review-r3 LOW:shimmer 动画周期常量，避免 magic number。 */
private const val SHIMMER_DURATION_MS = 1200

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, widthFraction: Float = 1f, height: Int = 16) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHIMMER_DURATION_MS),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    val baseColor = MaterialTheme.colorScheme.outlineVariant
    val shimmerColors = remember(baseColor) {
        listOf(
            baseColor.copy(alpha = 0.3f),
            baseColor.copy(alpha = 0.1f),
            baseColor.copy(alpha = 0.3f)
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = shimmerColors,
                    start = Offset(lerp(-100f, 500f, progress), 0f),
                    end = Offset(lerp(-100f, 500f, progress) + 200f, 0f)
                ),
                shape = RoundedCornerShape(4.dp)
            )
    )
}

@Composable
fun NoteListSkeleton(modifier: Modifier = Modifier) {
    // note-list-card-im-style · 跟新 NoteRow(固定 88dp)节奏完全一致:
    // 无水平 padding(卡片贴视窗),vertical 4dp 留 88dp 内部呼吸,
    // metadata / 标题 / 标签 三段 4dp spacer 分隔,总高 ≈ 88dp
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        ShimmerBox(widthFraction = 0.6f, height = 14) // 顶部 metadata 占位
        Spacer(modifier = Modifier.height(4.dp))
        ShimmerBox(widthFraction = 0.85f, height = 20) // 标题占位
        Spacer(modifier = Modifier.height(4.dp))
        ShimmerBox(widthFraction = 0.5f, height = 12) // 标签占位
    }
}

@Composable
fun NoteDetailSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        ShimmerBox(widthFraction = 0.5f, height = 24)
        Spacer(modifier = Modifier.height(16.dp))
        repeat(5) {
            ShimmerBox(widthFraction = 1f, height = 14)
            Spacer(modifier = Modifier.height(6.dp))
        }
        ShimmerBox(widthFraction = 0.6f, height = 14)
    }
}
