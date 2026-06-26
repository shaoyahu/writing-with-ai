@file:Suppress("FunctionNaming")

package com.yy.writingwithai.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.yy.writingwithai.R
import com.yy.writingwithai.core.prefs.AiAbilityCost
import com.yy.writingwithai.core.prefs.AiCostReference

/**
 * onboarding-apikey-prompt · Apikey 教育全屏页。
 *
 * spec: openspec/changes/onboarding-apikey-prompt/specs/onboarding-consent/spec.md
 * "Apikey prompt screen shown after consent" + "Token cost reference displayed"
 *
 * 设计 D1:第二页 Onboarding,跟隐私页串联。同意后 navigate 此页 → ack 后 navigate main。
 * 设计 1.4:`ApikeyPromptDialog` 复用本屏 body(拦截 AI 入口时弹)。
 */
@Composable
fun ApikeyPromptScreen(onAck: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .testTag("apikey_prompt_screen")
    ) {
        Text(
            text = stringResource(R.string.apikey_prompt_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.apikey_prompt_subtitle),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        ApikeyPromptBody(modifier = Modifier.weight(1f))
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onAck,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("apikey_prompt_ack_button")
            ) {
                Text(stringResource(R.string.apikey_prompt_btn_ack))
            }
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("apikey_prompt_skip_button")
            ) {
                Text(stringResource(R.string.apikey_prompt_btn_skip))
            }
        }
    }
}

/**
 * 拦截 dialog 版(已安装老用户从 AI 入口触发,不在 Onboarding 流程)。
 *
 * spec: openspec/changes/onboarding-apikey-prompt/specs/onboarding-consent/spec.md
 * "AI capability guard on first use" — 拦截后弹此 dialog。
 */
@Composable
fun ApikeyPromptDialog(onAck: () -> Unit, onDismiss: () -> Unit, confirmLabel: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = true),
        title = { Text(stringResource(R.string.apikey_prompt_title)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 480.dp)) {
                ApikeyPromptBody(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = onAck) {
                Text(confirmLabel ?: stringResource(R.string.apikey_prompt_btn_ack))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.apikey_prompt_btn_dismiss))
            }
        },
        modifier = Modifier.testTag("apikey_prompt_dialog")
    )
}

/**
 * 共享 body:intro + 能力清单 + 成本表 + 免责声明。
 * Screen / Dialog 共用,UI 渲染口径一致。
 */
@Composable
private fun ApikeyPromptBody(modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
    ) {
        Text(
            text = stringResource(R.string.apikey_prompt_intro),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.apikey_prompt_section_capabilities),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        AbilityCostTable(abilities = AiCostReference.abilities)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.apikey_prompt_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AbilityCostTable(abilities: List<AiAbilityCost>) {
    Surface(
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            abilities.forEachIndexed { index, ability ->
                AbilityCostRow(ability = ability)
                if (index < abilities.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun AbilityCostRow(ability: AiAbilityCost) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = ability.name,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = stringResource(
                R.string.apikey_prompt_token_range_fmt,
                ability.inputTokens,
                ability.outputTokens
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.apikey_prompt_cost_disclaimer_inline) + " · ${ability.rmbRange}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
