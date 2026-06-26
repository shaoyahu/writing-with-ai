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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/** fix-2026-06-26-review-r3 LOW:shimmer 动画周期常量,避免 magic number。 */
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

    val shimmerColors = listOf(
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f),
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )

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
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        ShimmerBox(widthFraction = 0.6f, height = 20)
        Spacer(modifier = Modifier.height(8.dp))
        ShimmerBox(widthFraction = 1f, height = 14)
        Spacer(modifier = Modifier.height(4.dp))
        ShimmerBox(widthFraction = 0.8f, height = 14)
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
