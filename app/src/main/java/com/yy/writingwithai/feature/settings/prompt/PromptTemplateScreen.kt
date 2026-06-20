package com.yy.writingwithai.feature.settings.prompt

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.R
import com.yy.writingwithai.core.ai.api.WritingOp

/**
 * custom-prompt-template · PromptTemplate 编辑屏(TabRow + OutlinedTextField + 恢复默认)。
 *
 * spec: openspec/changes/custom-prompt-template/specs/custom-prompt-template/spec.md
 * "PromptTemplateScreen provides 3-tab edit UI"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptTemplateScreen(viewModel: PromptTemplateViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
            Text(stringResource(R.string.prompt_hint))
            Spacer(modifier = Modifier.height(8.dp))
            TabRow(selectedTabIndex = tabs.indexOfFirst { it.first == uiState.currentOp }) {
                tabs.forEach { (op, labelRes) ->
                    Tab(
                        selected = uiState.currentOp == op,
                        onClick = { viewModel.onTabSwitch(op) },
                        text = { Text(stringResource(labelRes)) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = uiState.drafts[uiState.currentOp] ?: "",
                onValueChange = { viewModel.onPromptChange(uiState.currentOp, it) },
                label = { Text(stringResource(R.string.prompt_hint_label)) },
                modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                maxLines = 10
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.resetToDefault(uiState.currentOp) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.prompt_reset_default))
            }
        }
    }
}
