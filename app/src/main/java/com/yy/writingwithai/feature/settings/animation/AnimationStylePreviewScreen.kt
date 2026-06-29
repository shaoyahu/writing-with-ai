@file:Suppress("FunctionNaming", "LongMethod", "ComplexCondition")

package com.yy.writingwithai.feature.settings.animation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.core.ui.AnimatedSwitch
import com.yy.writingwithai.core.ui.animation.AnimationStyle

/**
 * animation-system-and-consent-redesign §10.2:动画风格设置页。
 *
 * 布局(自顶向下):
 * - TopAppBar("动画风格") + 返回
 * - reduce-motion Banner(条件渲染)
 * - LazyColumn 4 张单选卡片,每张含:
 *   - RadioButton + 风格名 + 描述
 *   - 迷你实时预览:nav dot + AnimatedSwitch + Tab dot
 *
 * 选中的卡片实时驱动 [com.yy.writingwithai.core.prefs.UserPrefsStore.setAnimationStyle],
 * Theme 通过 `animationStyleFlow` collect → `CompositionLocalProvider(LocalAnimationTokens = ...)`
 * 让本页以及其它页的动画自动跟着变(本页内的预览立即重绘,其它页要重启或重 compose)。
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (reduceMotion) {
                ReduceMotionBanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = AnimationStyle.values().toList(), key = { it.name }) { style ->
                    AnimationStyleCard(
                        style = style,
                        selected = style == current,
                        enabled = !reduceMotion || style == AnimationStyle.NONE,
                        onClick = { viewModel.onStyleSelected(style) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReduceMotionBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Text(
            text = stringResource(R.string.anim_style_reduce_motion_banner),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun AnimationStyleCard(style: AnimationStyle, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val containerColor: Color = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        // ux-2026-06-28 P5:未选中卡片用 surface 而非 surfaceVariant.copy(alpha=0.4f),视觉一致
        MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioButton(selected = selected, onClick = if (enabled) onClick else null)
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
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            // 迷你预览条(spec §10.2):nav dot + AnimatedSwitch + Tab dot
            PreviewRow(style = style)
        }
    }
}

@Composable
private fun PreviewRow(style: AnimationStyle) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // nav 提示(用 dot + label 代表过渡风格)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.anim_style_preview_nav),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Switch 预览(ux-2026-06-28 P5):可交互切换,让用户感知不同风格的动画差异
        var previewChecked by remember(style) { mutableStateOf(style == AnimationStyle.FLUID) }
        AnimatedSwitch(
            checked = previewChecked,
            onCheckedChange = { previewChecked = it }
        )
        // Tab 点(IMMERSIVE 风格用更大点示意"大幅变化")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(if (style == AnimationStyle.IMMERSIVE) 10.dp else 6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
            )
            Text(
                text = stringResource(R.string.anim_style_preview_tab),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun AnimationStyle.displayNameRes(): Int = when (this) {
    AnimationStyle.MINIMAL -> R.string.anim_style_minimal
    AnimationStyle.FLUID -> R.string.anim_style_fluid
    AnimationStyle.IMMERSIVE -> R.string.anim_style_immersive
    AnimationStyle.NONE -> R.string.anim_style_none
}

private fun AnimationStyle.descriptionRes(): Int = when (this) {
    AnimationStyle.MINIMAL -> R.string.anim_style_minimal_desc
    AnimationStyle.FLUID -> R.string.anim_style_fluid_desc
    AnimationStyle.IMMERSIVE -> R.string.anim_style_immersive_desc
    AnimationStyle.NONE -> R.string.anim_style_none_desc
}
