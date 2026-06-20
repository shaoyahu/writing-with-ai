package com.yy.writingwithai.feature.settings.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * `feature/settings/model` 跨 feature 入口(provider-real-integration + ui-redesign 落地)。
 *
 * AppNav 通过此 object 调 Composable,不直接 import `ModelManagementScreen` /
 * `ModelProviderDetailScreen` 等内部 file(自包含约束)。
 */
object ModelManagementEntry {
    /** 模型管理主屏入口(从 SettingsScreen 跳)。 */
    @Composable
    fun ModelManagementRoute(
        onProviderClick: (providerId: String) -> Unit,
        onBack: () -> Unit,
        modifier: Modifier = Modifier,
        viewModel: ModelManagementViewModel = hiltViewModel()
    ) {
        ModelManagementScreen(
            viewModel = viewModel,
            onProviderClick = onProviderClick,
            onBack = onBack,
            modifier = modifier
        )
    }

    /** Provider 详情屏(填 apikey)入口(从主屏点 provider Card 跳)。 */
    @Composable
    fun ModelProviderDetailRoute(
        providerId: String,
        onBack: () -> Unit,
        modifier: Modifier = Modifier,
        viewModel: ModelManagementViewModel = hiltViewModel()
    ) {
        ModelProviderDetailScreen(
            providerId = providerId,
            viewModel = viewModel,
            onBack = onBack,
            modifier = modifier
        )
    }
}
