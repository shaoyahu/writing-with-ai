@file:Suppress("FunctionNaming")

package com.yy.writingwithai.feature.my

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalCornerRadius
import com.yy.writingwithai.app.ui.theme.WritingAppTheme

/**
 * ui-redesign-v2 · "我的" tab 根屏:
 * - SectionCard 12dp 圆角 + 每项 leading icon
 * - Section 间标题标签(AI 配置 / 数据管理 / 关于)
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MyScreen(onNavigate: (MeTabTarget) -> Unit, modifier: Modifier = Modifier) {
    val cornerRadius = LocalCornerRadius.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.me_title)) }) },
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
            // Section 2 · 数据管理
            item {
                SectionHeader(stringResource(R.string.me_section_data))
            }
            item {
                SectionCard(cornerRadius = cornerRadius.md) {
                    MyListItem(
                        title = stringResource(R.string.me_alias_title),
                        icon = Icons.Filled.LocalOffer,
                        onClick = { onNavigate(MeTabTarget.SettingsAliasManagement) }
                    )
                    HorizontalDivider()
                    MyListItem(
                        title = stringResource(R.string.me_data_title),
                        icon = Icons.Filled.Storage,
                        onClick = { onNavigate(MeTabTarget.SettingsData) }
                    )
                    HorizontalDivider()
                    MyListItem(
                        title = stringResource(R.string.me_feishu_title),
                        icon = Icons.Filled.Cloud,
                        onClick = { onNavigate(MeTabTarget.FeishuAuth) }
                    )
                    HorizontalDivider()
                    MyListItem(
                        title = stringResource(R.string.me_settings_title),
                        icon = Icons.Filled.Settings,
                        onClick = { onNavigate(MeTabTarget.Settings) }
                    )
                }
            }
            // Section 3 · 关于
            item {
                SectionHeader(stringResource(R.string.me_section_about))
            }
            item {
                SectionCard(cornerRadius = cornerRadius.md) {
                    MyListItem(
                        title = stringResource(R.string.me_about_title),
                        icon = Icons.Filled.Info,
                        onClick = { onNavigate(MeTabTarget.About) }
                    )
                }
            }
        }
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
private fun MyListItem(title: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
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
