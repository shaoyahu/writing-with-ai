@file:Suppress("FunctionNaming")

package com.yy.writingwithai.feature.settings.model

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalSpacing
import com.yy.writingwithai.app.ui.theme.Spacing
import com.yy.writingwithai.app.ui.theme.customColors
import com.yy.writingwithai.core.ai.api.ProviderDescriptor

/**
 * fix-ai-config-ux · M6 custom-model · 模型管理主屏:
 * - 内置 + 自定义 provider 动态列表(VM.producerDescriptors() suspend via produceState)
 * - FAB "+" → 新建自定义 provider
 * - 自定义 provider 卡片右上角三点菜单(编辑配置 / 删除)
 * - 选中卡片边框高亮
 * - ping InProgress 时显示 CircularProgressIndicator + 文字
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagementScreen(
    onProviderClick: (String) -> Unit,
    onCreateCustomClick: () -> Unit,
    onEditCustomClick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModelManagementViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current

    val descriptors by produceState(
        initialValue = emptyList<ProviderDescriptor>(),
        state.customProviders
    ) {
        value = viewModel.providerDescriptors()
    }

    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateCustomClick) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.custom_provider_new_title)
                )
            }
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

            val builtinIds = remember(descriptors) {
                descriptors.filter { it.id in CustomProviderEditViewModel.BUILTIN_IDS }
                    .map { it.id }
                    .toSet()
            }
            descriptors.forEach { descriptor ->
                val isCustom = descriptor.id !in builtinIds
                ProviderInfoCard(
                    descriptor = descriptor,
                    isSelected = state.selectedProviderId == descriptor.id,
                    hasApiKey = state.configuredProviderIds.contains(descriptor.id),
                    isCustom = isCustom,
                    onSelect = { viewModel.selectProvider(descriptor.id) },
                    onConfigure = { onProviderClick(descriptor.id) },
                    onEditCustom = { onEditCustomClick(descriptor.id) },
                    onDeleteCustom = { pendingDeleteId = descriptor.id }
                )
                Spacer(Modifier.height(spacing.md))
            }

            Spacer(Modifier.height(spacing.xl))

            PingCard(
                pingResult = state.pingResult,
                canPing = state.configuredProviderIds.isNotEmpty() &&
                    state.selectedProviderId != "fake",
                onPing = { viewModel.ping(state.selectedProviderId) },
                spacing = spacing
            )
            Spacer(Modifier.height(spacing.xl))
        }
    }

    pendingDeleteId?.let { idToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.custom_provider_delete_title)) },
            text = { Text(stringResource(R.string.custom_provider_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCustomProvider(idToDelete)
                    pendingDeleteId = null
                }) {
                    Text(stringResource(R.string.custom_provider_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(stringResource(R.string.custom_provider_delete_cancel))
                }
            }
        )
    }
}

@Composable
private fun PingCard(pingResult: PingResult, canPing: Boolean, onPing: () -> Unit, spacing: Spacing) {
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
            when (val r = pingResult) {
                PingResult.Idle -> Text(
                    text = stringResource(R.string.model_management_ping_idle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PingResult.InProgress -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(spacing.sm))
                    Text(
                        text = stringResource(R.string.custom_provider_ping_testing),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
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
                    val detail = r.rawDetail.orEmpty()
                    val text = if (detail.isBlank()) {
                        stringResource(r.messageRes)
                    } else {
                        stringResource(R.string.model_management_ping_failed, detail)
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(spacing.md))
            FilledTonalButton(
                onClick = onPing,
                enabled = canPing && pingResult !is PingResult.InProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (pingResult is PingResult.InProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Filled.SmartToy, contentDescription = null)
                }
                Spacer(Modifier.width(spacing.sm))
                Text(stringResource(R.string.model_management_test_ping))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderInfoCard(
    descriptor: ProviderDescriptor,
    isSelected: Boolean,
    hasApiKey: Boolean,
    isCustom: Boolean,
    onSelect: () -> Unit,
    onConfigure: () -> Unit,
    onEditCustom: () -> Unit,
    onDeleteCustom: () -> Unit
) {
    val spacing = LocalSpacing.current
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isSelected) {
            BorderStroke(3.dp, MaterialTheme.customColors.success)
        } else {
            null
        },
        onClick = onSelect
    ) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = descriptor.displayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (isCustom) {
                        Spacer(Modifier.width(spacing.sm))
                        SuggestionChip(
                            onClick = {},
                            label = { Text(stringResource(R.string.custom_provider_tag_custom)) }
                        )
                    }
                    Spacer(Modifier.width(spacing.sm))
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                if (hasApiKey) {
                                    stringResource(R.string.model_management_status_configured)
                                } else {
                                    stringResource(R.string.model_management_status_not_configured)
                                }
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (hasApiKey) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    )
                }
                if (isSelected && hasApiKey) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                stringResource(R.string.model_management_status_enabled),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.customColors.successDark
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.customColors.success.copy(alpha = 0.15f),
                            labelColor = MaterialTheme.customColors.successDark
                        )
                    )
                }
                if (isCustom) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.custom_provider_menu_edit)) },
                            onClick = {
                                menuExpanded = false
                                onEditCustom()
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.custom_provider_menu_delete)) },
                            onClick = {
                                menuExpanded = false
                                onDeleteCustom()
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Delete, contentDescription = null)
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(spacing.xs))
            val summaryText = descriptor.models.firstOrNull()?.let {
                stringResource(
                    R.string.model_management_models_summary_fmt,
                    descriptor.models.size,
                    it
                )
            } ?: "—"
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(spacing.sm))
            OutlinedButton(
                onClick = onConfigure,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(
                    if (hasApiKey) {
                        stringResource(R.string.model_management_btn_modify)
                    } else {
                        stringResource(R.string.model_management_btn_configure)
                    }
                )
            }
        }
    }
}
