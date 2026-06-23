package com.yy.writingwithai.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yy.writingwithai.R
import com.yy.writingwithai.feature.settings.feishu.FeishuSyncLogSection
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * custom-prompt-template · Settings 主屏(目前 1 个功能项 → PromptTemplateScreen)。
 *
 * spec: openspec/changes/custom-prompt-template/specs/custom-prompt-template/spec.md
 * "Settings screen entry in QuickNoteListScreen overflow menu"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onPromptTemplateClick: () -> Unit,
    onModelManagementClick: () -> Unit = {},
    onAliasManagementClick: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        val ctx = LocalContext.current
        val settings = remember {
            EntryPoints.get(
                ctx.applicationContext,
                SettingsEntryPoint::class.java
            ).noteAssociationSettings()
        }
        var llmEnabled by remember { mutableStateOf(settings.isEnabled()) }

        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.model_management_title)) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable(onClick = onModelManagementClick)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_prompt_title)) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable(onClick = onPromptTemplateClick)
                )
            }
            // note-association P4:LLM 抽取开关
            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.note_association_ai_setting_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.note_association_ai_setting_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = llmEnabled,
                            onCheckedChange = { enabled ->
                                llmEnabled = enabled
                                settings.setEnabled(enabled)
                            }
                        )
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.entity_alias_management_title)) },
                    supportingContent = { Text(stringResource(R.string.entity_alias_management_desc)) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onAliasManagementClick)
                )
            }
            // feishu-bidir-sync:同步日志 section
            item {
                val ctx = LocalContext.current
                val entry = remember(ctx) {
                    EntryPoints.get(ctx.applicationContext, SettingsEntryPoint::class.java)
                }
                FeishuSyncLogSection(eventDao = entry.feishuSyncEventDao())
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsEntryPoint {
    fun noteAssociationSettings(): com.yy.writingwithai.core.prefs.NoteAssociationSettingsStore
    fun feishuSyncEventDao(): com.yy.writingwithai.core.feishu.sync.FeishuSyncEventDao
}
