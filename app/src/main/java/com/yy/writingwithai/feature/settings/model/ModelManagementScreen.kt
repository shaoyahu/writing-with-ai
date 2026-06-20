@file:Suppress("FunctionNaming")

package com.yy.writingwithai.feature.settings.model

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalSpacing

/**
 * ui-redesign-m5-glass · 模型管理主屏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagementScreen(
    onProviderClick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModelManagementViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.model_management_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg, vertical = spacing.md)
        ) {
            Text(
                text = stringResource(R.string.model_management_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(spacing.md))

            ProviderInfoCard(
                name = stringResource(R.string.model_provider_deepseek),
                baseUrl = "https://api.deepseek.com/anthropic",
                defaultModel = "deepseek-v4-flash",
                isSelected = state.selectedProviderId == "deepseek",
                hasApiKey = state.selectedProviderId == "deepseek" && state.hasApiKeyForSelected,
                onClick = { onProviderClick("deepseek") }
            )
            Spacer(Modifier.height(spacing.md))
            ProviderInfoCard(
                name = stringResource(R.string.model_provider_minimax),
                baseUrl = "https://api.minimaxi.com",
                defaultModel = "MiniMax-M2.7-highspeed",
                isSelected = state.selectedProviderId == "minimax",
                hasApiKey = state.selectedProviderId == "minimax" && state.hasApiKeyForSelected,
                onClick = { onProviderClick("minimax") }
            )
            Spacer(Modifier.height(spacing.md))
            ProviderInfoCard(
                name = stringResource(R.string.model_provider_mimo),
                baseUrl = "https://api.xiaomimimo.com",
                defaultModel = "mimo-v2.5-flash",
                isSelected = state.selectedProviderId == "mimo",
                hasApiKey = state.selectedProviderId == "mimo" && state.hasApiKeyForSelected,
                onClick = { onProviderClick("mimo") }
            )

            Spacer(Modifier.height(spacing.xl))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(spacing.lg)) {
                    Text(
                        text = stringResource(R.string.model_management_test_ping),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(spacing.sm))
                    when (val r = state.pingResult) {
                        PingResult.Idle -> Text(
                            text = stringResource(R.string.model_management_ping_idle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        PingResult.InProgress -> Text(
                            text = "...",
                            style = MaterialTheme.typography.bodySmall
                        )
                        is PingResult.Success -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(spacing.sm))
                            Text(
                                text = stringResource(R.string.model_management_ping_success, r.latencyMs),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is PingResult.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(spacing.sm))
                            Text(
                                text = stringResource(R.string.model_management_ping_failed, r.reason),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(Modifier.height(spacing.md))
                    FilledTonalButton(
                        onClick = { viewModel.ping(state.selectedProviderId) },
                        enabled = state.hasApiKeyForSelected && state.selectedProviderId != "fake",
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.SmartToy, contentDescription = null)
                        Spacer(Modifier.width(spacing.sm))
                        Text(stringResource(R.string.model_management_test_ping))
                    }
                }
            }
            Spacer(Modifier.height(spacing.xl))
        }
    }
}

@Composable
private fun ProviderInfoCard(
    name: String,
    baseUrl: String,
    defaultModel: String,
    isSelected: Boolean,
    hasApiKey: Boolean,
    onClick: () -> Unit
) {
    val spacing = LocalSpacing.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (hasApiKey) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = baseUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "默认 model: $defaultModel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
