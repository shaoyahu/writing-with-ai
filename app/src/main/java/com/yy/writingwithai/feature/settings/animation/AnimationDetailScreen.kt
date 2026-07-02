@file:Suppress("FunctionNaming")

package com.yy.writingwithai.feature.settings.animation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R

/**
 * animation-switch-redesign-followup §5.1:动画详细页(二级入口)。
 *
 * 承载 2 个 nav/tab 细分开关,从 `AnimationStylePreviewScreen` 迁出(spec followup §Decisions 1)。
 * 入口:MyScreen → Display section → "动画详细"。
 *
 * 布局(自顶向下):
 * - TopAppBar("动画详细") + 返回
 * - reduce-motion Banner(条件渲染,跟主风格页共用 [ReduceMotionBanner])
 * - **导航动画** 开关(走 [AnimationToggleRow] 顶层版)
 * - **标签动画** 开关
 *
 * reduce-motion 时 2 个开关 `enabled = false`(只读,spec OPEN Q2 拍板:disabled 显示而非隐藏),
 * 持久化值保留,关掉 reduce-motion 后立即恢复。
 *
 * spec 关联:openspec/changes/animation-switch-redesign-followup/specs/animation-system/spec.md
 * REQ ADDED AnimationDetailScreen exposes nav/tab animation toggles。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimationDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnimationDetailViewModel = hiltViewModel()
) {
    val reduceMotion by viewModel.reduceMotionEnabled.collectAsStateWithLifecycle()
    val navEnabled by viewModel.navAnimationsEnabled.collectAsStateWithLifecycle()
    val tabEnabled by viewModel.tabAnimationsEnabled.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.anim_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        // 跟 AnimationStylePreviewScreen 一致:Me tab 风格。
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (reduceMotion) {
                item(key = "reduce_motion_banner") {
                    ReduceMotionBanner(modifier = Modifier.fillMaxWidth())
                }
            }
            item(key = "toggle_nav") {
                AnimationToggleRow(
                    title = stringResource(R.string.anim_toggle_nav_title),
                    description = stringResource(R.string.anim_toggle_nav_description),
                    checked = navEnabled,
                    // OPEN Q2 拍板:reduce-motion 时 disabled 显示(只读 OFF),不隐藏。
                    enabled = !reduceMotion,
                    onCheckedChange = viewModel::onNavAnimationsToggled
                )
            }
            item(key = "toggle_tab") {
                AnimationToggleRow(
                    title = stringResource(R.string.anim_toggle_tab_title),
                    description = stringResource(R.string.anim_toggle_tab_description),
                    checked = tabEnabled,
                    enabled = !reduceMotion,
                    onCheckedChange = viewModel::onTabAnimationsToggled
                )
            }
        }
    }
}
