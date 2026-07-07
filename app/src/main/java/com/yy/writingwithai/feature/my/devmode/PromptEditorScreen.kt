@file:Suppress("FunctionNaming")

package com.yy.writingwithai.feature.my.devmode

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptEditorScreen(onBack: () -> Unit, viewModel: PromptEditorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMsg = stringResource(R.string.prompt_editor_saved)
    LaunchedEffect(state.saved) { if (state.saved) snackbarHostState.showSnackbar(savedMsg) }
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.prompt_editor_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(LocalSpacing.current.md)) {
            Text(
                text = stringResource(R.string.prompt_editor_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = LocalSpacing.current.sm)
            )
            OutlinedTextField(
                value = state.content,
                onValueChange = viewModel::updateContent,
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                enabled = !state.loading,
                placeholder = { Text(stringResource(R.string.prompt_editor_placeholder)) }
            )
            OutlinedButton(
                onClick = { viewModel.resetToDefault() },
                modifier = Modifier.fillMaxWidth().padding(top = LocalSpacing.current.sm)
            ) {
                Text(stringResource(R.string.prompt_editor_reset))
            }
            Button(
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth().padding(top = LocalSpacing.current.sm / 2)
            ) {
                Text(stringResource(R.string.prompt_editor_save))
            }
        }
    }
}
