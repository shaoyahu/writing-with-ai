package com.yy.writingwithai.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 统一圆角 token。Modifier.clip / cornerSize 走这里,业务代码不写裸 `.dp` 圆角。
 */
data class CornerRadius(
    val sm: Dp = 4.dp,
    val md: Dp = 8.dp,
    val lg: Dp = 16.dp,
)

/**
 * token 在 App 根提供一次,生命周期内不变 → 用 `staticCompositionLocalOf` 跳过读取追踪。
 */
val LocalCornerRadius = staticCompositionLocalOf { CornerRadius() }
