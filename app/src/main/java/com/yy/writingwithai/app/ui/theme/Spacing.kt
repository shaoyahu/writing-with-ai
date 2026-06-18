package com.yy.writingwithai.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 统一间距 token。所有页面间距走这里,业务代码不写裸 `.dp`。
 */
data class Spacing(
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
)

/**
 * token 在 App 根提供一次,生命周期内不变 → 用 `staticCompositionLocalOf` 跳过读取追踪。
 */
val LocalSpacing = staticCompositionLocalOf { Spacing() }
