package com.yy.writingwithai.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// M5 polish · ui-redesign-m5-glass · 种子色改 #3B82F6 蓝(产品定位"快速记录")。
// 仅在本文件内允许 hex 字面量(Material 3 标准调色板);
// feature / app 包外禁止再出现 Color(0x...)。
@Suppress("MagicNumber")
internal val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF3B82F6),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDBEAFE),
        onPrimaryContainer = Color(0xFF1E3A8A),
        secondary = Color(0xFF60A5FA),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFEFF6FF),
        onSecondaryContainer = Color(0xFF1E40AF),
        tertiary = Color(0xFF06B6D4),
        onTertiary = Color(0xFFFFFFFF),
        background = Color(0xFFFAFAFA),
        onBackground = Color(0xFF1F2937),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1F2937),
        surfaceVariant = Color(0xFFF3F4F6),
        onSurfaceVariant = Color(0xFF6B7280),
        outline = Color(0xFFE5E7EB)
    )

@Suppress("MagicNumber")
internal val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF60A5FA),
        onPrimary = Color(0xFF1E3A8A),
        primaryContainer = Color(0xFF1E40AF),
        onPrimaryContainer = Color(0xFFDBEAFE),
        secondary = Color(0xFF93C5FD),
        onSecondary = Color(0xFF1E3A8A),
        secondaryContainer = Color(0xFF1E40AF),
        onSecondaryContainer = Color(0xFFDBEAFE),
        tertiary = Color(0xFF22D3EE),
        onTertiary = Color(0xFF0E7490),
        background = Color(0xFF111827),
        onBackground = Color(0xFFF9FAFB),
        surface = Color(0xFF1F2937),
        onSurface = Color(0xFFF9FAFB),
        surfaceVariant = Color(0xFF374151),
        onSurfaceVariant = Color(0xFFD1D5DB),
        outline = Color(0xFF4B5563)
    )

// C5 修:成功状态色 token(原 ModelManagementScreen.kt 直接写 Color(0xFF4CAF50) / Color(0xFF2E7D32)
// 违反 CLAUDE.md "颜色一律走 MaterialTheme.colorScheme"。以下 token 走 Material 3 调色板
// 同款命名,业务侧通过 MaterialTheme.customColors.success / successDark 引用。

@Suppress("MagicNumber")
internal val SuccessGreenLight = Color(0xFF4CAF50)

@Suppress("MagicNumber")
internal val SuccessGreenDarkLight = Color(0xFF2E7D32)

@Suppress("MagicNumber")
internal val SuccessGreenDarkTheme = Color(0xFF66BB6A)

@Suppress("MagicNumber")
internal val SuccessGreenDarkDarkTheme = Color(0xFFA5D6A7)
