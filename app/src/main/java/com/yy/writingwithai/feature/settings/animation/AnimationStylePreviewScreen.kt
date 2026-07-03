@file:Suppress("FunctionNaming", "LongMethod", "ComplexCondition")

package com.yy.writingwithai.feature.settings.animation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.core.ui.animation.AnimationStyle

/**
 * animation-system-and-consent-redesign §10.2 + animation-switch-redesign-followup §4.1:
 * 动画风格设置页 —— 全 APP 4 选 1 风格库。
 *
 * 布局(自顶向下):
 * - TopAppBar("动画风格") + 返回
 * - reduce-motion Banner(条件渲染)
 * - **风格库段**:4 张单选卡片(MINIMAL / FLUID / IMMERSIVE / NONE),只改 style 枚举。
 *   选中态视觉锚点为 `RadioButton(selected = ...)`,无右侧 Check icon 重叠。
 *
 * 细分开关(nav/tab 动画)已迁至 [AnimationDetailScreen](spec followup §Decisions 1),
 * 入口在 MyScreen「显示」section。
 *
 * Theme 通过 `animationStyleFlow` collect → `CompositionLocalProvider(LocalAnimationTokens = ...)`
 * 让本页和其它页的动画自动跟着变。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimationStylePreviewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnimationStylePreviewViewModel = hiltViewModel()
) {
    val current by viewModel.animationStyle.collectAsStateWithLifecycle()
    val reduceMotion by viewModel.reduceMotionEnabled.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.anim_style_title)) },
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
        // ux-2026-06-28 #5:跟 Me tab 一致。
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
            // animation-switch-redesign-followup §4.1:4 张「风格库」卡片,
            // OPEN Q3 拍板:reduce-motion 不影响 style 卡片的选择(可正常点选,Theme 仍会强切 NONE)。
            items(items = AnimationStyle.values().toList(), key = { "style_${it.name}" }) { style ->
                AnimationStyleCard(
                    style = style,
                    selected = style == current,
                    onClick = { viewModel.onStyleSelected(style) }
                )
            }
        }
    }
}

@Composable
private fun AnimationStyleCard(style: AnimationStyle, selected: Boolean, onClick: () -> Unit) {
    val containerColor: Color = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        // ux-2026-06-28 P5:未选中卡片用 surface 而非 surfaceVariant.copy(alpha=0.4f),视觉一致
        MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(style.displayNameRes()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(style.descriptionRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun AnimationStyle.displayNameRes(): Int = when (this) {
    AnimationStyle.MINIMAL -> R.string.anim_style_minimal
    AnimationStyle.FLUID -> R.string.anim_style_fluid
    AnimationStyle.IMMERSIVE -> R.string.anim_style_immersive
    AnimationStyle.CROSSFADE -> R.string.anim_style_crossfade
    AnimationStyle.SCALE -> R.string.anim_style_scale
    AnimationStyle.SLIDE_UP -> R.string.anim_style_slide_up
    AnimationStyle.NONE -> R.string.anim_style_none
}

private fun AnimationStyle.descriptionRes(): Int = when (this) {
    AnimationStyle.MINIMAL -> R.string.anim_style_minimal_desc
    AnimationStyle.FLUID -> R.string.anim_style_fluid_desc
    AnimationStyle.IMMERSIVE -> R.string.anim_style_immersive_desc
    AnimationStyle.CROSSFADE -> R.string.anim_style_crossfade_desc
    AnimationStyle.SCALE -> R.string.anim_style_scale_desc
    AnimationStyle.SLIDE_UP -> R.string.anim_style_slide_up_desc
    AnimationStyle.NONE -> R.string.anim_style_none_desc
}
