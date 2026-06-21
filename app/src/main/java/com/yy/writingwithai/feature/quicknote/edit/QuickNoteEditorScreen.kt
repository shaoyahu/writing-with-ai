package com.yy.writingwithai.feature.quicknote.edit

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val tagsSummary by viewModel.tagsSummary.collectAsStateWithLifecycle()
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
            // note-association P3:wikilink 自动补全 — 检测 content 中 [[ 并弹候选
            var wikilinkPrefix by remember { mutableStateOf("") }
            val content = state.content
            val lastOpen = content.lastIndexOf("[[")
            if (lastOpen >= 0) {
                val afterOpen = content.substring(lastOpen + 2)
                val closeIdx = afterOpen.indexOf("]]")
                if (closeIdx < 0 && afterOpen.length <= 64 && !afterOpen.contains("\n")) {
                    // 未闭合,单行,不超过 64 字符 → 激活补全
                    LaunchedEffect(content) { wikilinkPrefix = afterOpen.trim() }
                }
            }
            if (wikilinkPrefix.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md)) {
                    WikilinkAutocomplete(
                        prefix = wikilinkPrefix,
                        onSelect = { selected ->
                            val before = content.substring(0, lastOpen)
                            val after = content.substring(lastOpen + 2 + wikilinkPrefix.length)
                            viewModel.setContent("$before[[$selected]]$after")
                            wikilinkPrefix = ""
                        }
                    )
                }
            }
            OutlinedTextField(
                value = state.content,
                onValueChange = { newContent ->
                    viewModel.setContent(newContent)
                    // 每次输入重算 wikilink prefix
                },
                placeholder = { Text(stringResource(R.string.quicknote_editor_content_hint)) },
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .focusRequester(contentFocusRequester)
                    .padding(horizontal = spacing.md, vertical = spacing.sm / 2)
            )
            // fix-quicknote-tags-and-search · 标签计数 label
            Text(
                text = stringResource(R.string.quicknote_editor_tags_count_fmt, state.tags.size),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm / 2)
            )
            TagInputRow(
                tags = state.tags,
                onAddTag = viewModel::addTag,
                onRemoveTag = viewModel::removeTag,
                input = state.tagInputText,
                onInputChange = viewModel::setTagInput
            )
            if (tagsSummary.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.quicknote_editor_tags_saved_fmt, tagsSummary),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm / 2)
                )
            }
        }
    }
}
