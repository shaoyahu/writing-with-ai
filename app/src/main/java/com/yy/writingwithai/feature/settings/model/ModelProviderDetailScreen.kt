@file:Suppress("FunctionNaming")

package com.yy.writingwithai.feature.settings.model

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalSpacing
import com.yy.writingwithai.core.ai.provider.ProviderConfig
import com.yy.writingwithai.core.ui.dropdown.AppSelectionDropdown
import kotlinx.coroutines.launch

/**
 * model-management-detail-dropdown · Provider 详情屏(三级页 — 点进具体服务商):
 *
 * ux-2026-06-29:重写 — 协议类型只支持 Anthropic 兼容，不再让用户在 OpenAI / Anthropic 间切换
 * (用户指示 v1 收敛到 Anthropic 兼容)。原 [ApiFormatDropdown] 删，改成静态「协议」行
 * 展示 "Anthropic 兼容"。endpoint / 鉴权方式 / 路径都按 Anthropic 兼容协议走，
 * baseURL 等 provider 内部细节对用户透明。
 *
 * 保留:
 * - 选择模型下拉(默认项带「(默认)」后缀;切换 → VM.onModelSelected 写 prefs)
 * - apikey 输入(已配默认隐藏 + 「修改 apikey」按钮显式触发，见 ux-2026-06-28 #1)
 * - 区分新配置 vs 覆盖(banner + 按钮文案 + placeholder)
 * - SharedFlow 接收 save 事件 → Toast + 自动 onBack
 *
 * 删:
 * - ApiFormatDropdown composable
 * - currentApiFormat state + VM.loadApiFormat / VM.onApiFormatSelected 调用
 * - ApiFormat import
 *
 * 未改(基础设施，后续单独清理任务):
 * - CoreAiGateway / AnthropicCompatibleAdapter 仍支持 ApiFormat.OPENAI 分支
 * - ModelManagementViewModel.onApiFormatSelected / loadApiFormat 仍存在
 * - ProviderPrefsStore.setApiFormat / getApiFormat 仍存在
 * - DeepseekConfig.apiFormat = ApiFormat.OPENAI(OPENAI 仍走 chat/completions 端点，
 *   但 UI 不再让用户选，默认透传)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelProviderDetailScreen(
    providerId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModelManagementViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val isExisting = state.configuredProviderIds.contains(providerId)

    var config by remember { mutableStateOf<ProviderConfig?>(null) }
    LaunchedEffect(providerId, state.customProviders) {
        config = viewModel.getProviderConfig(providerId)
    }

    var apiKey by remember { mutableStateOf("") }
    var revealKey by remember { mutableStateOf(false) }
    // ux-2026-06-28 #1:已配 apikey 默认隐藏输入框，显式「修改 apikey」按钮触发。
    var editingKey by remember { mutableStateOf(false) }

    // 进屏回填当前 model
    var currentModel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(providerId, config) {
        val cfg = config ?: return@LaunchedEffect
        currentModel = viewModel.loadSelectedModel(providerId) ?: cfg.defaultModel
    }

    LaunchedEffect(Unit) {
        viewModel.saveEvents.collect { result ->
            when (result) {
                is SaveResult.Success -> {
                    Toast.makeText(
                        context,
                        R.string.model_provider_detail_saved_toast,
                        Toast.LENGTH_SHORT
                    ).show()
                    onBack()
                }
                is SaveResult.Failed -> {
                    // fix-2026-06-28-ai-model-selection-actually-used:按 operationKind
                    // 选文案。MODEL_SELECT 文案由 VM 直接传 messageRes(无 rawDetail
                    // 拼接，纯本地切换，无 save 上下文)，不混入 save_failed_fmt。
                    val text = when (result.operationKind) {
                        SaveResult.OperationKind.MODEL_SELECT ->
                            context.getString(result.messageRes)
                        SaveResult.OperationKind.SAVE -> {
                            val detail = result.rawDetail.orEmpty()
                            if (detail.isBlank()) {
                                context.getString(result.messageRes)
                            } else {
                                context.getString(
                                    R.string.model_provider_detail_save_failed_fmt,
                                    detail
                                )
                            }
                        }
                    }
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    val displayName = config?.displayName ?: providerId
    // fix-2026-06-28-ai-model-selection-actually-used:onModelSelected 改为 suspend 后，
    // Composable 调它需要 coroutine scope。
    val modelSelectScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(displayName) },
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
            SuggestionChip(
                onClick = {},
                label = {
                    Text(
                        if (isExisting) {
                            stringResource(R.string.model_provider_detail_banner_existing)
                        } else {
                            stringResource(R.string.model_provider_detail_banner_new)
                        }
                    )
                },
                icon = if (isExisting) {
                    { Icon(Icons.Filled.CheckCircle, contentDescription = null) }
                } else {
                    null
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = if (isExisting) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            )

            // 协议类型 — ux-2026-06-29 重写:只支持 Anthropic 兼容，静态展示
            // (原 ApiFormatDropdown 让用户在 OpenAI / Anthropic 间切，已删除)。
            // 走 read-only OutlinedTextField 保持表单风格一致。
            OutlinedTextField(
                value = stringResource(R.string.model_provider_detail_api_format_anthropic),
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(stringResource(R.string.model_provider_detail_api_format_label)) },
                modifier = Modifier.fillMaxWidth()
            )

            // 选择模型下拉
            config?.let { cfg ->
                val defaultSuffix = stringResource(R.string.model_provider_detail_model_default_suffix)
                val selectedModel = currentModel ?: cfg.defaultModel
                AppSelectionDropdown(
                    options = cfg.supportedModels,
                    selected = selectedModel,
                    onSelected = { picked ->
                        currentModel = picked
                        // fix-2026-06-28-ai-model-selection-actually-used:VM.onModelSelected
                        // 现在是 suspend，失败会 emit SaveResult.Failed(MODEL_SELECT) 走
                        // saveEvents SharedFlow,UI 在 LaunchedEffect 收事件弹 Toast。
                        modelSelectScope.launch { viewModel.onModelSelected(providerId, picked) }
                    },
                    label = { Text(stringResource(R.string.model_provider_detail_model_label)) },
                    optionLabel = { model ->
                        if (model == cfg.defaultModel) "$model $defaultSuffix" else model
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(spacing.md))
            // ux-2026-06-28 #1:已配 apikey 默认隐藏输入框，显示「修改 apikey」按钮;点击展开输入框。
            if (isExisting && !editingKey) {
                OutlinedButton(
                    onClick = { editingKey = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.model_provider_detail_btn_update_key))
                }
            } else {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.model_provider_detail_api_key_label)) },
                    placeholder = {
                        Text(
                            if (isExisting) {
                                stringResource(R.string.model_provider_detail_placeholder_override)
                            } else {
                                stringResource(R.string.model_provider_detail_placeholder_new)
                            }
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { revealKey = !revealKey }) {
                            Icon(
                                imageVector = if (revealKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(R.string.model_provider_detail_show_key)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(spacing.md))
            Button(
                onClick = {
                    viewModel.saveProvider(providerId, apiKey, currentModel)
                },
                // ux-2026-06-28 #1:已配 apikey 时允许空 key 保存(VM 跳过 key 写盘)，仅切模型 / provider。
                enabled = apiKey.isNotBlank() || isExisting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(Modifier.width(spacing.xs))
                Text(
                    if (isExisting) {
                        stringResource(R.string.model_provider_detail_save_override)
                    } else {
                        stringResource(R.string.model_provider_detail_save)
                    }
                )
            }
        }
    }
}
