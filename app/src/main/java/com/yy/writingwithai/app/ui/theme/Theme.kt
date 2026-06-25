@file:Suppress("FunctionNaming")

package com.yy.writingwithai.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * ui-redesign-v2 · 语义色 token 集合,业务侧通过 MaterialTheme.customColors.success / warning 引用。
 */
data class CustomColors(
    val success: Color,
    val successDark: Color,
    val warning: Color,
    val warningDark: Color
)

// 默认 token 实例:顶层 val 保证 App 生命周期内单例,避免 Theme.kt 在重组里反复 new。
private val DefaultSpacing = Spacing()
private val DefaultCornerRadius = CornerRadius()
private val DefaultLightCustomColors = CustomColors(
    success = SuccessGreenLight,
    successDark = SuccessGreenDarkLight,
    warning = WarningAmberLight,
    warningDark = WarningAmberDarkLight
)
private val DefaultDarkCustomColors = CustomColors(
    success = SuccessGreenDarkTheme,
    successDark = SuccessGreenDarkDarkTheme,
    warning = WarningAmberDarkTheme,
    warningDark = WarningAmberDarkDarkTheme
)

// M7 fix: 改为 staticCompositionLocalOf,值仅在主题切换时变化(全量重组),跳过读追踪
private val LocalCustomColors = staticCompositionLocalOf { DefaultLightCustomColors }

/** 业务 Composable 通过 MaterialTheme.customColors 读取语义色 token。 */
val MaterialTheme.customColors: CustomColors
    @Composable
    @ReadOnlyComposable
    get() = LocalCustomColors.current

/**
 * writing-with-ai · Material 3 主题入口。
 *
 * - 默认跟随系统 dark/light(`isSystemInDarkTheme()`)。
 * - 提供 `LocalSpacing` / `LocalCornerRadius` 两个自定义 token,业务 Composable 通过它们读取。
 * - ui-redesign-v2: 种子色从蓝 #3B82F6 改为墨绿 #1B6B4A。
 */
@Composable
fun WritingAppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val customColors = if (darkTheme) DefaultDarkCustomColors else DefaultLightCustomColors

    CompositionLocalProvider(
        LocalSpacing provides DefaultSpacing,
        LocalCornerRadius provides DefaultCornerRadius,
        LocalCustomColors provides customColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
