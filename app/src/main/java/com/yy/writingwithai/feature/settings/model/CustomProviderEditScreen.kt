@file:Suppress("FunctionNaming")

package com.yy.writingwithai.feature.settings.model

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalSpacing
import com.yy.writingwithai.app.ui.theme.Spacing
import com.yy.writingwithai.core.ai.api.ApiFormat
import com.yy.writingwithai.core.ai.provider.AuthStyle

/**
 * M6 custom-model · 自定义 Provider 编辑表单。
 *
 * 三区布局:
 * 1. 连接信息(始终展开):显示名称 / Base URL / API Key + 表单内 ping
 * 2. 模型 & 认证(可折叠):认证方式 / 自定义 Header / 默认模型 / 支持模型列表
 * 3. 高级设置(可折叠):Endpoint Path / API 格式 / 自定义 Headers
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomProviderEditScreen(
    providerId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CustomProviderEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isEdit = providerId != null

    LaunchedEffect(providerId) {
        if (providerId != null) {
            viewModel.loadExisting(providerId)
        }
    }

    // 离开屏幕时清 isSaving,避免下次进入残留 disabled
    DisposableEffect(Unit) {
        onDispose { viewModel.clearSaving() }
    }

    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is CustomProviderEditEvent.Saved -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.model_provider_detail_saved_toast),
                            Toast.LENGTH_SHORT
                        ).show()
                        onBack()
                    }
                    is CustomProviderEditEvent.SaveFailed -> {
                        val detail = event.reason.rawDetail.orEmpty()
                        val text = if (detail.isBlank()) {
                            context.getString(event.reason.messageRes)
                        } else {
                            context.getString(
                                R.string.custom_provider_error_fmt,
                                context.getString(event.reason.messageRes),
                                detail
                            )
                        }
                        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (isEdit) {
                            stringResource(R.string.custom_provider_edit_title)
                        } else {
                            stringResource(R.string.custom_provider_new_title)
                        }
                    )
                },
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            // ===== 1. 连接信息 =====
            SectionHeader(stringResource(R.string.custom_provider_section_connection))

            OutlinedTextField(
                value = state.displayName,
                onValueChange = viewModel::onDisplayNameChanged,
                label = { Text(stringResource(R.string.custom_provider_display_name_label)) },
                placeholder = { Text(stringResource(R.string.custom_provider_display_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::onBaseUrlChanged,
                label = { Text(stringResource(R.string.custom_provider_base_url_label)) },
                placeholder = { Text(stringResource(R.string.custom_provider_base_url_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::onApiKeyChanged,
                label = { Text(stringResource(R.string.model_provider_detail_api_key_label)) },
                singleLine = true,
                visualTransformation =
                if (state.revealApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                trailingIcon = {
                    IconButton(onClick = viewModel::toggleRevealApiKey) {
                        Icon(
                            imageVector =
                            if (state.revealApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(R.string.model_provider_detail_show_key)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            FormPingSection(
                pingResult = state.pingResult,
                canPing = state.displayName.isNotBlank() && state.baseUrl.isNotBlank() &&
                    state.defaultModel.isNotBlank() && state.apiKey.isNotBlank(),
                onPing = viewModel::pingFromForm,
                spacing = spacing
            )

            // ===== 2. 模型 & 认证 =====
            CollapsibleSectionHeader(
                title = stringResource(R.string.custom_provider_section_models_auth),
                expanded = state.isModelsAuthExpanded,
                onToggle = viewModel::toggleModelsAuthExpanded
            )
            AnimatedVisibility(
                visible = state.isModelsAuthExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    AuthStyleDropdown(
                        selected = state.authStyle,
                        onSelected = viewModel::onAuthStyleChanged,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (state.authStyle == AuthStyle.CUSTOM_HEADER) {
                        OutlinedTextField(
                            value = state.customAuthHeaderName,
                            onValueChange = viewModel::onCustomAuthHeaderNameChanged,
                            label = { Text(stringResource(R.string.custom_provider_custom_header_label)) },
                            placeholder = { Text(stringResource(R.string.custom_provider_custom_header_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = state.defaultModel,
                        onValueChange = viewModel::onDefaultModelChanged,
                        label = { Text(stringResource(R.string.custom_provider_default_model_label)) },
                        placeholder = { Text(stringResource(R.string.custom_provider_default_model_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = stringResource(R.string.custom_provider_supported_models_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ModelChipInput(
                        models = state.supportedModels,
                        newInput = state.newModelInput,
                        onInputChange = viewModel::onNewModelInputChanged,
                        onAdd = viewModel::addModel,
                        onRemove = viewModel::removeModel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ===== 3. 高级设置 =====
            CollapsibleSectionHeader(
                title = stringResource(R.string.custom_provider_section_advanced),
                expanded = state.isAdvancedExpanded,
                onToggle = viewModel::toggleAdvancedExpanded
            )
            AnimatedVisibility(
                visible = state.isAdvancedExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    OutlinedTextField(
                        value = state.endpointPath,
                        onValueChange = viewModel::onEndpointPathChanged,
                        label = { Text(stringResource(R.string.custom_provider_endpoint_path_label)) },
                        placeholder = { Text(stringResource(R.string.custom_provider_endpoint_path_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    ApiFormatDropdown(
                        selected = state.apiFormat,
                        onSelected = viewModel::onApiFormatChanged,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(spacing.md))

            Button(
                onClick = viewModel::save,
                enabled = state.displayName.isNotBlank() && state.baseUrl.isNotBlank() &&
                    state.defaultModel.isNotBlank() && state.apiKey.isNotBlank() && !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                }
                Text(stringResource(R.string.custom_provider_save))
            }

            Spacer(Modifier.height(spacing.xl))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun CollapsibleSectionHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    OutlinedButton(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null
        )
    }
}

@Composable
private fun FormPingSection(pingResult: PingResult, canPing: Boolean, onPing: () -> Unit, spacing: Spacing) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        FilledTonalButton(
            onClick = onPing,
            enabled = canPing && pingResult !is PingResult.InProgress,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.SmartToy, contentDescription = null)
            Spacer(Modifier.width(spacing.sm))
            Text(stringResource(R.string.custom_provider_test_connection))
        }
        Spacer(Modifier.width(spacing.md))
        when (val r = pingResult) {
            PingResult.Idle -> {}
            PingResult.InProgress -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
            is PingResult.Success -> Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            is PingResult.Failed -> Icon(
                Icons.Filled.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
    when (val r = pingResult) {
        PingResult.Idle -> {}
        PingResult.InProgress -> Text(
            text = stringResource(R.string.custom_provider_ping_testing),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        is PingResult.Success -> Text(
            text = stringResource(R.string.model_management_ping_success, r.latencyMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        is PingResult.Failed -> Text(
            text = (r.rawDetail ?: stringResource(r.messageRes)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthStyleDropdown(selected: AuthStyle, onSelected: (AuthStyle) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val labels = mapOf(
        AuthStyle.AUTHORIZATION to R.string.custom_provider_auth_style_authorization,
        AuthStyle.X_API_KEY to R.string.custom_provider_auth_style_x_api_key,
        AuthStyle.CUSTOM_HEADER to R.string.custom_provider_auth_style_custom_header
    )
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = stringResource(labels.getValue(selected)),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.custom_provider_auth_style_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AuthStyle.entries.forEach { style ->
                DropdownMenuItem(
                    text = { Text(stringResource(labels.getValue(style))) },
                    onClick = {
                        onSelected(style)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiFormatDropdown(selected: ApiFormat, onSelected: (ApiFormat) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val labels = mapOf(
        ApiFormat.ANTHROPIC to R.string.custom_provider_api_format_anthropic,
        ApiFormat.OPENAI to R.string.custom_provider_api_format_openai
    )
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = stringResource(labels.getValue(selected)),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.custom_provider_api_format_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ApiFormat.entries.forEach { format ->
                DropdownMenuItem(
                    text = { Text(stringResource(labels.getValue(format))) },
                    onClick = {
                        onSelected(format)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelChipInput(
    models: List<String>,
    newInput: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    Column(modifier = modifier) {
        if (models.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm / 2),
                modifier = Modifier.fillMaxWidth()
            ) {
                models.forEach { model ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(model) },
                        label = { Text(model) },
                        trailingIcon = {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    )
                }
            }
        }
        OutlinedTextField(
            value = newInput,
            onValueChange = { value ->
                if (value.contains(',')) {
                    value.split(',').forEach { segment ->
                        val trimmed = segment.trim()
                        if (trimmed.isNotBlank()) {
                            onInputChange(trimmed)
                            onAdd()
                        }
                    }
                    onInputChange("")
                } else {
                    onInputChange(value)
                }
            },
            placeholder = { Text(stringResource(R.string.custom_provider_add_model_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (newInput.isNotBlank()) {
                    onAdd()
                }
            }),
            trailingIcon = {
                IconButton(
                    onClick = onAdd,
                    enabled = newInput.isNotBlank()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.common_add_cd))
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
