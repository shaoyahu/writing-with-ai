@file:Suppress("FunctionNaming")

package com.yy.writingwithai.feature.settings.model

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalSpacing
import com.yy.writingwithai.app.ui.theme.Spacing
import com.yy.writingwithai.core.ai.api.ApiFormat
import com.yy.writingwithai.core.ai.provider.AuthStyle
import com.yy.writingwithai.core.ui.dropdown.AppSelectionDropdown

/**
 * M6 custom-model · 自定义 Provider 编辑表单。
 *
 * 三区布局:
 * 1. 连接信息(始终展开):显示名称 / Base URL / API Key + 表单内 ping
 * ux-2026-06-28 #3 简化:扁平单段布局，无折叠。
 * 1. 连接信息:显示名称 / 完整 URL / API Key + 表单内 ping
 * 2. 模型 & 认证(直接展示，不再折叠):认证方式 / 自定义 Header / 默认模型 / 支持模型列表
 * 协议只走 anthropic 兼容，Endpoint Path / API 格式从表单移除，完整 URL 由用户填入。
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

    val isEdit = providerId != null
    // ux-2026-06-28 P1:apikey 已存在时隐藏输入框，显示"修改 apikey"按钮;点按钮才露出。
    var editingKey by remember { mutableStateOf(false) }
    val keyExists = isEdit && !editingKey && state.apiKey.isNotBlank()

    LaunchedEffect(providerId) {
        if (providerId != null) {
            viewModel.loadExisting(providerId)
        }
    }

    // 离开屏幕时清 isSaving，避免下次进入残留 disabled
    DisposableEffect(Unit) {
        onDispose { viewModel.clearSaving() }
    }

    // R6-6 fix: 原 LifecycleResumeEffect(Unit) + onPauseOrDispose { job.cancel() } 在 onPause 期间
    // (用户按 Home / 切后台) VM 发出的 Saved / SaveFailed 事件会被丢掉，Toast 弹不出来。
    // 改 LaunchedEffect(viewModel) 绑定 Composable 生命周期，离开 Composition 才取消，
    // 不依赖 RESUMED — onPause 期间 events 仍可正常 collect，用户回到前台可看到 Toast。
    LaunchedEffect(viewModel) {
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
            // custom-provider-api-format:协议下拉(OpenAI 兼容 / Anthropic 兼容)。
            val apiFormatLabels = mapOf(
                ApiFormat.ANTHROPIC to stringResource(R.string.custom_provider_api_format_anthropic),
                ApiFormat.OPENAI to stringResource(R.string.custom_provider_api_format_openai)
            )
            AppSelectionDropdown(
                options = ApiFormat.entries,
                selected = state.apiFormat,
                onSelected = viewModel::onApiFormatChanged,
                label = { Text(stringResource(R.string.custom_provider_api_format_label)) },
                optionLabel = { apiFormatLabels[it] ?: it.name },
                modifier = Modifier.fillMaxWidth()
            )

            // custom-provider-api-format:helper 文案按 state.apiFormat 动态切换，只描述
            // body / SSE 协议，不给具体 path 字面提示(各家 URL 形态不一)。
            Text(
                text = stringResource(
                    when (state.apiFormat) {
                        ApiFormat.OPENAI -> R.string.custom_provider_helper_openai
                        ApiFormat.ANTHROPIC -> R.string.custom_provider_helper_anthropic
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ux-2026-06-28 P1:apikey 已存在 → 显示"修改 apikey"按钮;否则显示输入框。
            if (keyExists) {
                OutlinedButton(
                    onClick = { editingKey = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.model_provider_detail_btn_update_key))
                }
            } else {
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
            }

            FormPingSection(
                pingResult = state.pingResult,
                // ux-2026-06-28 P1:编辑模式 keyExists 时也允许 ping(密钥已存)
                canPing = state.displayName.isNotBlank() && state.baseUrl.isNotBlank() &&
                    state.defaultModel.isNotBlank() && (state.apiKey.isNotBlank() || keyExists),
                onPing = viewModel::pingFromForm,
                spacing = spacing
            )

            // ===== 2. 模型 & 认证(扁平，不再折叠;ux-2026-06-28 #3 不藏高级设置) =====
            SectionHeader(stringResource(R.string.custom_provider_section_models_auth))

            // ux-2026-06-28 P3:UI 只展示 AUTHORIZATION + X_API_KEY;CUSTOM_HEADER 保留 enum 兼容旧数据。
            val authStyleLabels = mapOf(
                AuthStyle.AUTHORIZATION to stringResource(R.string.custom_provider_auth_style_authorization),
                AuthStyle.X_API_KEY to stringResource(R.string.custom_provider_auth_style_x_api_key),
                AuthStyle.CUSTOM_HEADER to stringResource(R.string.custom_provider_auth_style_custom_header)
            )
            AppSelectionDropdown(
                options = AuthStyle.entries.filter { it != AuthStyle.CUSTOM_HEADER },
                selected = state.authStyle,
                onSelected = viewModel::onAuthStyleChanged,
                label = { Text(stringResource(R.string.custom_provider_auth_style_label)) },
                optionLabel = { authStyleLabels[it] ?: it.name },
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

            Spacer(Modifier.height(spacing.md))

            Button(
                onClick = viewModel::save,
                // ux-2026-06-28 P1:编辑模式已有 apikey 时允许保存(apiKey 为空但 keyExists)
                enabled = state.displayName.isNotBlank() && state.baseUrl.isNotBlank() &&
                    state.defaultModel.isNotBlank() && (state.apiKey.isNotBlank() || keyExists) && !state.isSaving,
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
