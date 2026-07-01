package com.yy.writingwithai.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ui-redesign-v2 · 统一间距 token(9 档)。所有页面间距走这里，业务代码不写裸 `.dp`。
 */
data class Spacing(
    val xs2: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val sm2: Dp = 12.dp,
    val md: Dp = 16.dp,
    val md2: Dp = 20.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xl2: Dp = 40.dp
)

/**
 * token 在 App 根提供一次，生命周期内不变 → 用 `staticCompositionLocalOf` 跳过读取追踪。
 */
val LocalSpacing = staticCompositionLocalOf { Spacing() }
