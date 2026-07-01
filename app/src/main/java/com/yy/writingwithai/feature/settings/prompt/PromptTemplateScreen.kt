package com.yy.writingwithai.feature.settings.prompt

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.core.ui.animation.LocalAnimationTokens

/**
 * fix-ai-config-ux · PromptTemplate 编辑屏:
 * - Tab 标题红点 indicator(有未保存改动时)
 * - 底部 Row{ Button("保存") + OutlinedButton("恢复默认") }
 * - 顶部提示改为 prompt_hint_v2
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptTemplateScreen(viewModel: PromptTemplateViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tabs =
        listOf(
            WritingOp.EXPAND to R.string.prompt_op_expand,
            WritingOp.POLISH to R.string.prompt_op_polish,
            WritingOp.ORGANIZE to R.string.prompt_op_organize
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_prompt_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.prompt_hint_v2))
            Spacer(modifier = Modifier.height(8.dp))
            TabRow(
                selectedTabIndex = tabs.indexOfFirst { it.first == uiState.currentOp }.coerceAtLeast(0)
            ) {
                tabs.forEach { (op, labelRes) ->
                    Tab(
                        selected = uiState.currentOp == op,
                        onClick = { viewModel.onTabSwitch(op) },
                        text = {
                            Row {
                                Text(stringResource(labelRes))
                                if (uiState.pendingSave.contains(op)) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier =
                                        Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.error)
                                    )
                                }
                            }
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // animation-system · tab 切换接 token(spec §REQ 6):currentOp 变化时
            // AnimatedContent 用 tabContentSpec 平滑过渡新旧内容(spec MINIMAL=tween 200ms,
            // IMMERSIVE=tween 350ms, NONE=snap 即时切)。
            // NOTE:transitionSpec lambda 不是 @Composable，需提前读 token 再引用。
            val tabSpec = LocalAnimationTokens.current.tabContentSpec
            AnimatedContent(
                targetState = uiState.currentOp,
                transitionSpec = {
                    androidx.compose.animation.ContentTransform(
                        targetContentEnter = androidx.compose.animation.fadeIn(animationSpec = tabSpec),
                        initialContentExit = androidx.compose.animation.fadeOut(animationSpec = tabSpec)
                    )
                },
                label = "PromptTemplateScreen.content"
            ) { currentOp ->
                OutlinedTextField(
                    value = uiState.drafts[currentOp] ?: "",
                    onValueChange = { viewModel.onPromptChange(currentOp, it) },
                    label = { Text(stringResource(R.string.prompt_hint_label)) },
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    maxLines = 10
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { viewModel.save(uiState.currentOp) },
                    enabled = uiState.pendingSave.contains(uiState.currentOp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.prompt_save))
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { viewModel.resetToDefault(uiState.currentOp) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.prompt_reset_default))
                }
            }
        }
    }
}
