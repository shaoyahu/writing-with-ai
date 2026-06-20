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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalSpacing

/**
 * ui-redesign-m5-glass · Provider 详情屏:填 apikey + 保存。
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
    val context = LocalContext.current
    val spacing = LocalSpacing.current

    var apiKey by remember { mutableStateOf("") }
    var revealKey by remember { mutableStateOf(false) }

    LaunchedEffect(state.selectedProviderId, state.hasApiKeyForSelected) {
        if (state.selectedProviderId == providerId && state.hasApiKeyForSelected && apiKey.isBlank()) {
            Toast.makeText(context, R.string.model_provider_detail_saved_toast, Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    val baseUrl = when (providerId) {
        "deepseek" -> "https://api.deepseek.com/anthropic"
        "minimax" -> "https://api.minimaxi.com"
        "mimo" -> "https://api.xiaomimimo.com"
        else -> "—"
    }
    val defaultModel = when (providerId) {
        "deepseek" -> "deepseek-v4-flash"
        "minimax" -> "MiniMax-M2.7-highspeed"
        "mimo" -> "mimo-v2.5-flash"
        else -> "—"
    }
    val displayName = when (providerId) {
        "deepseek" -> stringResource(R.string.model_provider_deepseek)
        "minimax" -> stringResource(R.string.model_provider_minimax)
        "mimo" -> stringResource(R.string.model_provider_mimo)
        else -> providerId
    }

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
            Text(
                text = stringResource(R.string.model_provider_detail_base_url),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = baseUrl, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "默认 model: $defaultModel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(spacing.md))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
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
                Spacer(Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.model_provider_detail_save))
            }
        }
    }
}
