@file:Suppress("FunctionNaming")

package com.yy.writingwithai.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** C5:成功状态色 token 集合,业务侧通过 MaterialTheme.customColors.success 引用。 */
data class CustomColors(
    val success: Color,
    val successDark: Color
)

// 默认 token 实例:顶层 val 保证 App 生命周期内单例,避免 Theme.kt 在重组里反复 new。
// 业务需要覆盖可在 WritingAppTheme 外 CompositionLocalProvider 嵌套。
private val DefaultSpacing = Spacing()
private val DefaultCornerRadius = CornerRadius()
private val DefaultLightCustomColors = CustomColors(
    success = SuccessGreenLight,
    successDark = SuccessGreenDarkLight
)
private val DefaultDarkCustomColors = CustomColors(
    success = SuccessGreenDarkTheme,
    successDark = SuccessGreenDarkDarkTheme
)
private val LocalCustomColors = compositionLocalOf { DefaultLightCustomColors }

/** 业务 Composable 通过 MaterialTheme.customColors 读取成功色 token。 */
val MaterialTheme.customColors: CustomColors
    @Composable
    @ReadOnlyComposable
    get() = LocalCustomColors.current

/**
 * writing-with-ai · Material 3 主题入口。
 *
 * - 默认跟随系统 dark/light(`isSystemInDarkTheme()`)。
 * - M0 不暴露"强制明亮 / 强制暗黑 / 跟随系统"切换 UI;M5 打磨阶段补设置项。
 *
 * 提供 `LocalSpacing` / `LocalCornerRadius` 两个自定义 token,业务 Composable 通过它们读取。
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
