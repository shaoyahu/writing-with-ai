@file:Suppress("FunctionNaming")

package com.yy.writingwithai.app

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.yy.writingwithai.feature.settings.SettingsEntry
import com.yy.writingwithai.feature.settings.alias.AliasManagementScreen
import com.yy.writingwithai.feature.settings.animation.AnimationDetailScreen
import com.yy.writingwithai.feature.settings.animation.AnimationStylePreviewScreen
import com.yy.writingwithai.feature.settings.association.NoteAssociationSettingsScreen
import com.yy.writingwithai.feature.settings.data.SettingsDataScreen
import com.yy.writingwithai.feature.settings.feishu.FeishuAuthScreen
import com.yy.writingwithai.feature.settings.i18n.SettingsLanguageScreen
import com.yy.writingwithai.feature.settings.model.ModelManagementEntry

/**
 * fix M62 (full-review):从 AppNav.kt 抽出 Settings 系列 12 个 composable 路由(原 line
 * 263-338)到独立文件 — AppNav.kt 在 M62 review 时是 315 行(项目当前 493 行,包含
 * 这个区块 + 后续其他 routes + 引导启动逻辑)。拆后 AppNav.kt 仅保留 AppShell /
 * Quicknote / Onboarding / Entity / Developer 路由 + 启动逻辑,降到 ~200 行,在
 * 50-行/函数 guideline 范围内(单文件 < 500 行 = 是惯例,不是硬规则)。
 *
 * NavGraphBuilder 注册语义:Navigation Compose 的 `composable<T>` 是 NavGraphBuilder
 * 扩展函数,需要在 NavHost context 内调用。本函数收 `NavGraphBuilder` receiver,在
 * AppNav 主文件 NavHost 内调一次即可 — 等价 inline 但避免主文件膨胀。
 *
 * 影响 API:Nil (AppNav.kt NavHost 调用点本地可见)。
 */
fun NavGraphBuilder.settingsNavRoutes(navController: NavController) {
    composable<SettingsData> {
        SettingsDataScreen(
            onBack = { navController.popBackStack() }
        )
    }
    // language-switcher:「我的 → 设置 → 语言」3 选 1;选完 recreate() 整个 Activity。
    composable<SettingsLanguage> {
        SettingsLanguageScreen(
            onBack = { navController.popBackStack() }
        )
    }
    composable<Settings> {
        SettingsEntry.SettingsRoute(
            onBack = { navController.popBackStack() },
            // entity-extraction-polish §5.2
            onNavigateToAssociation = { navController.navigate(SettingsNoteAssociation) }
        )
    }
    composable<SettingsPromptTemplate> {
        SettingsEntry.PromptTemplateRoute(
            onBack = { navController.popBackStack() }
        )
    }
    composable<SettingsAliasManagement> {
        AliasManagementScreen(onBack = { navController.popBackStack() })
    }
    // entity-extraction-polish §5.1:笔记关联设置 route
    composable<SettingsNoteAssociation> {
        NoteAssociationSettingsScreen(onBack = { navController.popBackStack() })
    }
    composable<SettingsModelManagement> {
        ModelManagementEntry.ModelManagementRoute(
            onProviderClick = { id -> navController.navigate(SettingsModelProviderDetail(id)) },
            onCreateCustomClick = { navController.navigate(SettingsCustomProviderEdit(null)) },
            onEditCustomClick = { id -> navController.navigate(SettingsCustomProviderEdit(id)) },
            onBack = { navController.popBackStack() }
        )
    }
    composable<SettingsModelProviderDetail> { backStackEntry ->
        val args = backStackEntry.toRoute<SettingsModelProviderDetail>()
        ModelManagementEntry.ModelProviderDetailRoute(
            providerId = args.providerId,
            onBack = { navController.popBackStack() }
        )
    }
    composable<SettingsCustomProviderEdit> { backStackEntry ->
        val args = backStackEntry.toRoute<SettingsCustomProviderEdit>()
        ModelManagementEntry.CustomProviderEditRoute(
            providerId = args.providerId,
            onBack = { navController.popBackStack() }
        )
    }
    // animation-system-and-consent-redesign §11.2:动画风格设置 route。
    composable<SettingsAnimationStyle> {
        AnimationStylePreviewScreen(
            onBack = { navController.popBackStack() }
        )
    }
    // animation-switch-redesign-followup §6.1:动画详细设置 route(2 个细分开关入口)。
    composable<SettingsAnimationDetail> {
        AnimationDetailScreen(
            onBack = { navController.popBackStack() }
        )
    }
    // ux-2026-06-28 P6:飞书授权页专属 route(不再走 Settings hub)
    composable<SettingsFeishu> {
        FeishuAuthScreen(
            onBack = { navController.popBackStack() }
        )
    }
    // feishu-import-from-folder:从文件夹导入 sub-screen
    composable<FeishuFolderImport> {
        com.yy.writingwithai.feature.feishuimport.FolderImportScreen(
            onBack = { navController.popBackStack() }
        )
    }
}
