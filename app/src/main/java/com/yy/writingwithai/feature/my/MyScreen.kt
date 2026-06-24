@file:Suppress("FunctionNaming")

package com.yy.writingwithai.feature.my

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.BuildConfig
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.WritingAppTheme

/**
 * app-bottom-tab-bar · "我的" tab 根屏。
 *
 * 反馈(2026-06-23):按功能类型 4 分组:
 * - 卡 1:「AI 配置」— AI 模型管理 + Prompt 模板,类内横线分隔,无边距
 * - 卡 2:「实体别名」— 独立 Card
 * - 卡 3:「同步 / 数据」— 数据导入导出 + 飞书同步 + 设置,类内横线
 * - 卡 4:「关于」— 版本号展示
 *
 * 页面背景浅灰(surfaceVariant),Card 白色(surface),类间浅灰间距自然形成分隔。
 * Card 无左右边距,只有上下间距。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MyScreen(onNavigate: (MeTabTarget) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.me_title)) }) },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // 卡 1 · AI 配置:AI 模型管理 + Prompt 模板,同一类,横线分隔
            item {
                SectionCard {
                    MyListItem(stringResource(R.string.me_model_title)) {
                        onNavigate(MeTabTarget.SettingsModelManagement)
                    }
                    HorizontalDivider()
                    MyListItem(stringResource(R.string.me_prompt_title)) {
                        onNavigate(MeTabTarget.SettingsPromptTemplate)
                    }
                }
            }
            // 卡 2 · 实体别名(独立)
            item {
                SectionCard {
                    MyListItem(stringResource(R.string.me_alias_title)) {
                        onNavigate(MeTabTarget.SettingsAliasManagement)
                    }
                }
            }
            // 卡 3 · 数据 + 飞书同步 + 设置
            item {
                SectionCard {
                    MyListItem(stringResource(R.string.me_data_title)) {
                        onNavigate(MeTabTarget.SettingsData)
                    }
                    HorizontalDivider()
                    MyListItem(stringResource(R.string.me_feishu_title)) {
                        onNavigate(MeTabTarget.FeishuAuth)
                    }
                    HorizontalDivider()
                    MyListItem(stringResource(R.string.me_settings_title)) {
                        onNavigate(MeTabTarget.Settings)
                    }
                }
            }
            // 卡 4 · 关于(展示,不 navigate)
            item {
                SectionCard {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.me_about_title)) },
                        supportingContent = {
                            Text(
                                text = "v" + BuildConfig.VERSION_NAME,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }
    }
}

/** 白色 Card 包裹:填满宽度,无左右边距,垂直 12dp 间距。 */
@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    ) {
        content()
    }
}

@Composable
private fun MyListItem(title: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Preview(name = "My", showBackground = true)
@Composable
private fun MyScreenPreview() {
    WritingAppTheme {
        MyScreen(onNavigate = {})
    }
}
