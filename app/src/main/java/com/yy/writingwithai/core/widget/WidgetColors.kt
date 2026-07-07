package com.yy.writingwithai.core.widget

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.glance.unit.ColorProvider

/**
 * widget-rome-compat · widget 颜色 token(从 Material 3 ColorScheme 派生)。
 *
 * 替换原 6 个硬编码 hex 颜色常量(`cBlue` / `cWhite` / `cBg` / `cTitle` / `cBody` / `cMeta`),
 * 跟随系统暗色 / 亮色 / Material You 取色三档自适应。
 *
 * 注意:此函数在 GlanceTheme { provideContent { } } 内调用，MaterialTheme.colorScheme
 * 由 GlanceTheme 提供，非普通 Compose 主题。GlanceTheme 内部桥接了 Material 3 色彩系统。
 */
@Immutable
data class WidgetColors(
    val widgetPrimary: ColorProvider,
    val widgetBackground: ColorProvider,
    val widgetOnBackground: ColorProvider,
    val widgetOnSurfaceVariant: ColorProvider,
    val widgetPrimaryContainer: ColorProvider,
    val widgetOutline: ColorProvider
)

@Composable
@ReadOnlyComposable
fun widgetColors(): WidgetColors = with(MaterialTheme.colorScheme) {
    WidgetColors(
        widgetPrimary = colorProvider(primary),
        widgetBackground = colorProvider(surface),
        widgetOnBackground = colorProvider(onSurface),
        widgetOnSurfaceVariant = colorProvider(onSurfaceVariant),
        widgetPrimaryContainer = colorProvider(primaryContainer),
        widgetOutline = colorProvider(outline)
    )
}

/** 桥接 Material 3 [androidx.compose.ui.graphics.Color] 到 Glance [ColorProvider]。 */
private fun colorProvider(color: androidx.compose.ui.graphics.Color): ColorProvider = ColorProvider(color = color)
