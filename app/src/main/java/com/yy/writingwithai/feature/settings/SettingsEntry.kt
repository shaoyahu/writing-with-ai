package com.yy.writingwithai.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.yy.writingwithai.feature.settings.prompt.PromptTemplateScreen
import com.yy.writingwithai.feature.settings.prompt.PromptTemplateViewModel

/**
 * `feature/settings` 跨 feature 入口(custom-prompt-template 落地)。
 *
 * spec: openspec/changes/custom-prompt-template/specs/custom-prompt-template/spec.md
 * "feature/settings/ package is self-contained"
 *
 * AppNav 通过此 object 调 Composable,不允许直接 import `SettingsScreen` /
 * `PromptTemplateScreen` 等内部 file(自包含约束)。
 */
object SettingsEntry {
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_PROMPT_TEMPLATE = "settings/prompt-template"

    /** Settings 主屏入口(从 QuickNoteListScreen overflow menu 跳)。 */
    @Composable
    fun SettingsRoute(
        onBack: () -> Unit = {},
        // entity-extraction-polish §5.2:笔记关联设置入口(进 SettingsNoteAssociation route)
        onNavigateToAssociation: () -> Unit = {},
        modifier: Modifier = Modifier
    ) {
        SettingsScreen(
            onBack = onBack,
            onNavigateToAssociation = onNavigateToAssociation,
            modifier = modifier
        )
    }

    /** PromptTemplate 编辑屏入口(从 SettingsScreen 跳)。 */
    @Composable
    fun PromptTemplateRoute(onBack: () -> Unit, viewModel: PromptTemplateViewModel = hiltViewModel()) {
        PromptTemplateScreen(
            viewModel = viewModel,
            onBack = onBack
        )
    }
}
