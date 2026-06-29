package com.yy.writingwithai.feature.settings

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.WritingAppTheme
import com.yy.writingwithai.core.prefs.NoteAssociationSettingsStore
import com.yy.writingwithai.core.ui.AnimatedSwitch
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * custom-prompt-template · Settings 主屏(目前 1 个功能项 → AI 关联开关)。
 *
 * 反馈 #4 修(2026-06-23):AI 模型管理 / 提示词模板 / 实体别名 入口已迁到"我的" tab;
 * 飞书同步日志已迁到 FeishuAuth 主屏。当前 SettingsScreen 只保留全局 AI 关联开关。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    // entity-extraction-polish §5.2:跳转「笔记关联」设置 route
    onNavigateToAssociation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        },
        // ux-2026-06-28 #5:跟 Me tab 视觉一致(SectionCard 用 surface 在 surfaceVariant 上浮起)。
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) { padding ->
        val ctx = LocalContext.current
        // R5-4 fix: 仅在 Preview(InspectionMode)打 Log.w,避免 Release 日志被每次冷启污染。
        val isPreview = LocalInspectionMode.current
        // H2 fix: runCatching 防 Preview 崩溃; observeEnabled + collectAsStateWithLifecycle 保持状态同步
        val settings = remember {
            runCatching {
                EntryPointAccessors.fromApplication(
                    ctx.applicationContext,
                    SettingsEntryPoint::class.java
                ).noteAssociationSettings()
            }.onFailure { if (isPreview) Log.w("SettingsScreen", "Hilt EntryPoint unavailable (Preview?)", it) }
                .getOrNull()
        }
        val llmEnabled by (
            settings?.observeEnabled()
                ?: kotlinx.coroutines.flow.flowOf(false)
            )
            .collectAsStateWithLifecycle(initialValue = false)

        LazyColumn(modifier = Modifier.padding(padding)) {
            // 反馈 #4:移除 AI 模型管理 / 提示词模板 / 实体别名 ListItem(已迁到"我的" tab)
            // 保留"保存时使用 AI 找关联" Switch(全局 AI 关联开关,与具体模型/别名解耦)。
            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.note_association_ai_setting_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.note_association_ai_setting_desc))
                    },
                    trailingContent = {
                        AnimatedSwitch(
                            checked = llmEnabled,
                            onCheckedChange = { enabled ->
                                settings?.setEnabled(enabled)
                            }
                        )
                    }
                )
            }
            // entity-extraction-polish §5.2:笔记关联设置入口(阈值 / 暂停 / 立即重跑 / 进度)
            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.note_association_settings_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.note_association_settings_entry_desc))
                    },
                    modifier = Modifier.clickable { onNavigateToAssociation() }
                )
            }
            // 反馈 #4:飞书同步日志 section 已迁到 FeishuAuth 主屏(更合理的位置)
        }
    }
}

// M11 fix: 移除未使用的 feishuSyncEventDao 声明
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsEntryPoint {
    fun noteAssociationSettings(): NoteAssociationSettingsStore
}

@Preview(name = "Settings", showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    WritingAppTheme { SettingsScreen() }
}
