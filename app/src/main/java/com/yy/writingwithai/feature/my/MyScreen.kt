@file:Suppress("FunctionNaming")

package com.yy.writingwithai.feature.my

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalCornerRadius
import com.yy.writingwithai.app.ui.theme.WritingAppTheme

/**
 * app-bottom-tab-bar · "我的" tab 根屏:
 * - SectionCard 12dp 圆角 + 每项 leading icon
 * - Section 间标题标签(AI 与显示 / 数据管理 / 关于)
 *
 * 入口 6 条(spec 4 Decision 4):
 * - 数据导入/导出 → `SettingsData`
 * - AI 模型管理 → `SettingsModelManagement`
 * - Prompt 模板 → `SettingsPromptTemplate`
 * - 实体别名 → `SettingsAliasManagement`
 * - 飞书同步 → `Settings`(已有 FeishuSyncLogSection，见 app-tab-bar spec)
 * - 关于(版本号)→ 纯展示，不 navigate
 *
 * ux-2026-06-28 #7:关于页新增「检查更新」可点击入口 → `CheckUpdateViewModel` 拉远端 manifest,
 * 已是最新 / 失败 → Snackbar;有新版本 → AlertDialog(下载流程后续 PR 接入 ApkDownloader)。
 *
 * TopAppBar 无 `navigationIcon` ——【我的】是 `AppShell` 内嵌子 NavHost 的顶级 tab 根屏
 * (spec 2.4:所有**非主页** destination TopAppBar 才含 `navigationIcon = ArrowBack`),
 * 顶级 tab 不应有返回箭头，tab 切换由底部 `AppTabBar` 走 navigate 而非 pop。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MyScreen(
    onNavigate: (MeTabTarget) -> Unit,
    modifier: Modifier = Modifier,
    // ux-2026-06-28 #7:关于页「检查更新」VM。Hilt 默认注入，无外部依赖。
    viewModel: CheckUpdateViewModel = hiltViewModel()
) {
    val cornerRadius = LocalCornerRadius.current
    val updateState by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // ux-2026-06-28 #7:resolve 文案在 Composable 作用域，LaunchedEffect 内只消费已就绪字符串。
    val upToDateTemplate = stringResource(R.string.me_check_update_up_to_date_fmt)
    val failedText = stringResource(R.string.me_check_update_failed)
    // ux-2026-06-28 #7:UpToDate / Failed → Snackbar 提示 + consume 回 Idle，避免重组再触发。
    LaunchedEffect(updateState) {
        when (val s = updateState) {
            is CheckUpdateState.UpToDate -> {
                snackbarHostState.showSnackbar(
                    message = upToDateTemplate.format(s.localVersion)
                )
                viewModel.consume()
            }
            CheckUpdateState.Failed -> {
                snackbarHostState.showSnackbar(message = failedText)
                viewModel.consume()
            }
            else -> {}
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // ux-2026-07-01 #tab-gap:AppShell 外层 Scaffold 已用 innerPadding 扣掉 bottomBar(tab 栏)
        // 占位，内层 Scaffold 不应再吃 bottom inset(默认 contentWindowInsets = systemBars 包含
        // bottom)，否则 LazyColumn 滚到底会跟 tab 栏之间留一段空隙。
        // 改成只吃 statusBars 让 TopAppBar 正常避让状态栏(horizontal + bottom 全交给外层)。
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.me_title),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            )
        },
        // ux-2026-06-28 #7:检查更新 Snackbar 通道(UpToDate / Failed 用)
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Section 1 · AI 配置
            item {
                SectionHeader(stringResource(R.string.me_section_ai_config))
            }
            item {
                SectionCard(cornerRadius = cornerRadius.md) {
                    MyListItem(
                        title = stringResource(R.string.me_model_title),
                        icon = Icons.Filled.SmartToy,
                        onClick = { onNavigate(MeTabTarget.SettingsModelManagement) }
                    )
                    HorizontalDivider()
                    MyListItem(
                        title = stringResource(R.string.me_prompt_title),
                        icon = Icons.Filled.SmartToy,
                        onClick = { onNavigate(MeTabTarget.SettingsPromptTemplate) }
                    )
                }
            }
            // Section 2 · 显示(ux-2026-06-28 P5:动画风格移出 AI 配置;language-switcher:加语言)
            item {
                SectionHeader(stringResource(R.string.me_section_display))
            }
            item {
                SectionCard(cornerRadius = cornerRadius.md) {
                    MyListItem(
                        title = stringResource(R.string.anim_style_title),
                        icon = Icons.Filled.Animation,
                        onClick = { onNavigate(MeTabTarget.SettingsAnimationStyle) }
                    )
                    HorizontalDivider()
                    // animation-switch-redesign-followup §7.1:动画详细入口(nav/tab 细分开关)。
                    MyListItem(
                        title = stringResource(R.string.anim_detail_title),
                        icon = Icons.Filled.Tune,
                        onClick = { onNavigate(MeTabTarget.SettingsAnimationDetail) }
                    )
                    HorizontalDivider()
                    MyListItem(
                        title = stringResource(R.string.settings_language_title),
                        icon = Icons.Filled.Translate,
                        onClick = { onNavigate(MeTabTarget.SettingsLanguage) }
                    )
                }
            }
            // Section 3 · 数据管理
            item {
                SectionHeader(stringResource(R.string.me_section_data))
            }
            item {
                SectionCard(cornerRadius = cornerRadius.md) {
                    MyListItem(
                        title = stringResource(R.string.me_data_title),
                        icon = Icons.Filled.Storage,
                        onClick = { onNavigate(MeTabTarget.SettingsData) }
                    )
                    HorizontalDivider()
                    // ux-2026-06-28 P6:飞书走专属路由 SettingsFeishu(不再走 Settings hub)
                    MyListItem(
                        title = stringResource(R.string.me_feishu_title),
                        icon = Icons.Filled.Cloud,
                        onClick = { onNavigate(MeTabTarget.SettingsFeishu) }
                    )
                    HorizontalDivider()
                    // ux-2026-06-28 P6:笔记关联走专属路由 SettingsNoteAssociation
                    MyListItem(
                        title = stringResource(R.string.me_note_association_title),
                        icon = Icons.Filled.LocalOffer,
                        onClick = { onNavigate(MeTabTarget.SettingsNoteAssociation) }
                    )
                }
            }
            // Section 4 · 关于
            item {
                SectionHeader(stringResource(R.string.me_section_about))
            }
            item {
                SectionCard(cornerRadius = cornerRadius.md) {
                    // ux-2026-06-28 #7:检查更新入口。Checking 时显示 progress + 灰显;
                    // 点击直接调 VM.check()，不让 Navigate 接 Nav,Snackbar/Dialog 在本屏处理。
                    MyListItem(
                        title = if (updateState is CheckUpdateState.Checking) {
                            stringResource(R.string.me_check_update_checking)
                        } else {
                            stringResource(R.string.me_check_update_title)
                        },
                        icon = Icons.Filled.Refresh,
                        enabled = updateState !is CheckUpdateState.Checking,
                        onClick = { viewModel.check() }
                    )
                    HorizontalDivider()
                    // 关于(版本号)纯展示，spec 明确不 navigate
                    MyAboutItem(
                        title = stringResource(R.string.me_about_title)
                    )
                }
            }
        }
    }

    // ux-2026-06-28 #7:发现新版本 → AlertDialog(versionName + releaseNotes);
    // 「下载」按钮后续 PR 接入 ApkDownloader，目前仅关闭 dialog 回到 Idle。
    val manifest = (updateState as? CheckUpdateState.UpdateAvailable)?.manifest
    if (manifest != null) {
        val remoteVersionText = stringResource(
            R.string.me_check_update_dialog_remote_version_fmt,
            manifest.versionName
        )
        AlertDialog(
            onDismissRequest = { viewModel.consume() },
            title = { Text(stringResource(R.string.me_check_update_dialog_title)) },
            text = {
                Column {
                    Text(
                        text = remoteVersionText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (manifest.releaseNotes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = manifest.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.consume() }) {
                    Text(stringResource(R.string.me_check_update_btn_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.consume() }) {
                    Text(stringResource(R.string.me_check_update_btn_later))
                }
            }
        )
    }
}

/** Section 标题标签。 */
@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/** 白色圆角 Card 包裹。 */
@Composable
private fun SectionCard(cornerRadius: Dp, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(cornerRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        content()
    }
}

@Composable
private fun MyListItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    // ux-2026-06-28 #7:checking 中灰显入口;默认 true 保持现有调用兼容。
    enabled: Boolean = true
) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                }
            )
        },
        modifier = if (enabled) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        }
    )
}

/** 关于条目:纯展示版本号 + supportingContent，无 clickable，无 chevron。 */
@Composable
private fun MyAboutItem(title: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                text = "v" + com.yy.writingwithai.BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    )
}

@Preview(name = "My", showBackground = true)
@Composable
private fun MyScreenPreview() {
    WritingAppTheme {
        MyScreen(onNavigate = {})
    }
}
