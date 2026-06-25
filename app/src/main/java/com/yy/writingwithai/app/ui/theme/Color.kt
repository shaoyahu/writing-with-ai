package com.yy.writingwithai.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ui-redesign-v2 · 种子色改为墨绿 #1B6B4A + 琥珀 #D4940A + 薄荷 #2BAD8E。
// 与 app 名称「小札」、launcher icon 深墨绿背景呼应。
// 仅在本文件内允许 hex 字面量(Material 3 标准调色板);
// feature / app 包外禁止再出现 Color(0x...)。
@Suppress("MagicNumber")
internal val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF1B6B4A),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFA8F4C6),
        onPrimaryContainer = Color(0xFF002112),
        secondary = Color(0xFFD4940A),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFDFA2),
        onSecondaryContainer = Color(0xFF2A1E00),
        tertiary = Color(0xFF2BAD8E),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFA4F4D4),
        onTertiaryContainer = Color(0xFF002119),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFBFDF8),
        onBackground = Color(0xFF191C19),
        surface = Color(0xFFFBFDF8),
        onSurface = Color(0xFF191C19),
        surfaceVariant = Color(0xFFDDE5DB),
        onSurfaceVariant = Color(0xFF414942),
        outline = Color(0xFF717971),
        outlineVariant = Color(0xFFC1C9BF)
    )

@Suppress("MagicNumber")
internal val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF8CD8AB),
        onPrimary = Color(0xFF003822),
        primaryContainer = Color(0xFF005234),
        onPrimaryContainer = Color(0xFFA8F4C6),
        secondary = Color(0xFFF5BE48),
        onSecondary = Color(0xFF443100),
        secondaryContainer = Color(0xFF614700),
        onSecondaryContainer = Color(0xFFFFDFA2),
        tertiary = Color(0xFF89D8B8),
        onTertiary = Color(0xFF00382A),
        tertiaryContainer = Color(0xFF00513D),
        onTertiaryContainer = Color(0xFFA4F4D4),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF111411),
        onBackground = Color(0xFFE1E3DD),
        surface = Color(0xFF111411),
        onSurface = Color(0xFFE1E3DD),
        surfaceVariant = Color(0xFF414942),
        onSurfaceVariant = Color(0xFFC1C9BF),
        outline = Color(0xFF8B938A),
        outlineVariant = Color(0xFF717971)
    )

// ui-redesign-v2 · 成功状态色 token
@Suppress("MagicNumber")
internal val SuccessGreenLight = Color(0xFF2E7D32)

@Suppress("MagicNumber")
internal val SuccessGreenDarkLight = Color(0xFF1B5E20)

@Suppress("MagicNumber")
internal val SuccessGreenDarkTheme = Color(0xFF66BB6A)

@Suppress("MagicNumber")
internal val SuccessGreenDarkDarkTheme = Color(0xFFA5D6A7)

// ui-redesign-v2 · 警告状态色 token(琥珀色系)
@Suppress("MagicNumber")
internal val WarningAmberLight = Color(0xFFF57C00)

@Suppress("MagicNumber")
internal val WarningAmberDarkLight = Color(0xFFE65100)

@Suppress("MagicNumber")
internal val WarningAmberDarkTheme = Color(0xFFFFB74D)

@Suppress("MagicNumber")
internal val WarningAmberDarkDarkTheme = Color(0xFFFFCC80)
