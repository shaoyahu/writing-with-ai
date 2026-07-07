@file:Suppress("FunctionNaming")

package com.yy.writingwithai.feature.my.devmode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperModeScreen(
    onBack: () -> Unit,
    onNavigateToPromptEditor: () -> Unit,
    viewModel: DeveloperModeViewModel = hiltViewModel()
) {
    val enabled by viewModel.isEnabled.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dev_options_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.dev_options_enable_label)) },
                trailingContent = { Switch(checked = enabled, onCheckedChange = { viewModel.setEnabled(it) }) }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.dev_options_prompt_editor_title)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.dev_options_prompt_editor_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    TextButton(onClick = onNavigateToPromptEditor) {
                        Text(stringResource(R.string.dev_options_prompt_editor_open))
                    }
                }
            )
        }
    }
}
