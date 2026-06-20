package com.yy.writingwithai.feature.quicknote.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickNoteEditorScreen(
    onBack: () -> Unit,
    onSaved: (id: String) -> Unit,
    prefillFocus: Boolean = false,
    viewModel: QuickNoteEditorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    // M4-1:widget "新建"启动时,自动 focus 正文输入框。
    val contentFocusRequester = remember { FocusRequester() }
    LaunchedEffect(prefillFocus) {
        if (prefillFocus) contentFocusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isNew) {
                            stringResource(R.string.quicknote_editor_title_new)
                        } else {
                            stringResource(R.string.quicknote_editor_title_edit)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.quicknote_editor_cancel)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.save(onSaved = onSaved) },
                        enabled = state.isLoaded && !state.isSaving
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = stringResource(R.string.quicknote_editor_save)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                placeholder = { Text(stringResource(R.string.quicknote_editor_title_hint)) },
                singleLine = true,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.sm / 2)
            )
            OutlinedTextField(
                value = state.content,
                onValueChange = viewModel::setContent,
                placeholder = { Text(stringResource(R.string.quicknote_editor_content_hint)) },
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .focusRequester(contentFocusRequester)
                    .padding(horizontal = spacing.md, vertical = spacing.sm / 2)
            )
            TagInputRow(
                tags = state.tags,
                onAddTag = viewModel::addTag,
                onRemoveTag = viewModel::removeTag
            )
        }
    }
}
