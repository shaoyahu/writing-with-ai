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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.yy.writingwithai.core.ai.provider.ProviderConfig

/**
 * fix-ai-config-ux · M6 custom-model · Provider 详情屏:
 * - 通过 VM.getProviderConfig(providerId) suspend 拿配置(避免 runBlocking ANR)
 * - 区分新配置 vs 覆盖(banner + 按钮文案 + placeholder)
 * - SharedFlow 接收 save 事件 → Toast + 自动 onBack
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val isExisting = state.configuredProviderIds.contains(providerId)

    var config by remember { mutableStateOf<ProviderConfig?>(null) }
    LaunchedEffect(providerId, state.customProviders) {
        config = viewModel.getProviderConfig(providerId)
    }

    var apiKey by remember { mutableStateOf("") }
    var revealKey by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
                        val detail = result.rawDetail.orEmpty()
                        val text = if (detail.isBlank()) {
                            context.getString(result.messageRes)
                        } else {
                            context.getString(
                                R.string.model_provider_detail_save_failed_fmt,
                                detail
                            )
                        }
                        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }
    }

    val displayName = config?.displayName ?: providerId
    val baseUrl = config?.baseUrl ?: "—"
    val defaultModel = config?.defaultModel ?: "—"

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(displayName) },
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
            Text(
                text = stringResource(R.string.model_provider_detail_base_url),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = baseUrl, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(
                    R.string.model_provider_detail_default_model_fmt,
                    defaultModel
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(spacing.md))
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
            Spacer(Modifier.height(spacing.md))
            Button(
                onClick = {
                    if (apiKey.isNotBlank()) {
                        viewModel.saveProvider(providerId, apiKey)
                    }
                },
                enabled = apiKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(Modifier.width(4.dp))
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
