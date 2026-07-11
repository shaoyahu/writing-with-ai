@file:Suppress("FunctionNaming")

package com.yy.writingwithai.feature.quicknote.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalCornerRadius
import com.yy.writingwithai.app.ui.theme.LocalSpacing
import com.yy.writingwithai.core.ui.MarkdownText
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * markdown-live-preview · 预览模式三态:EDIT / PREVIEW / SPLIT。
 *
 * 屏宽 ≥ 600dp 时 toggle 循环 EDIT → PREVIEW → SPLIT → EDIT;
 * 屏宽 < 600dp 时 toggle 仅在 EDIT ↔ PREVIEW 间循环(分屏被禁用,符合设计)。
 *
 * SPLIT 模式:左 BasicTextField 编辑,右 MarkdownText 渲染,中间 divider 分隔。
 * 预览内容走 snapshotFlow + 200ms debounce,跟手输入不卡。
 */
internal enum class PreviewMode { EDIT, PREVIEW, SPLIT }

/** design.md §D3 · 600dp 是分屏门槛(平板/横屏可用);窄屏自动回退到 PREVIEW。 */
private const val SPLIT_MIN_WIDTH_DP: Int = 600

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, FlowPreview::class)
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
    val cornerRadius = LocalCornerRadius.current
    val contentFocusRequester = remember { FocusRequester() }
    LaunchedEffect(prefillFocus) {
        if (prefillFocus) contentFocusRequester.requestFocus()
    }
    val pendingUris by viewModel.pendingAttachmentUris.collectAsStateWithLifecycle()
    val existingAttachments by viewModel.observeAttachments()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val visibleAttachments = if (state.isNew) emptyList() else existingAttachments
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.addAttachment(it) } }

    // markdown-live-preview · 屏宽 & 预览模式
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val canSplit = screenWidthDp >= SPLIT_MIN_WIDTH_DP
    // rememberSaveable: 旋转屏幕 / 切后台后保留选择
    var previewMode by rememberSaveable { mutableStateOf(PreviewMode.EDIT) }
    // 窄屏强制非 SPLIT
    LaunchedEffect(canSplit) {
        if (!canSplit && previewMode == PreviewMode.SPLIT) {
            previewMode = PreviewMode.PREVIEW
        }
    }

    // 200ms debounce · 预览内容用 snapshotFlow 跟踪 content
    var previewContent by remember { mutableStateOf(state.content) }
    LaunchedEffect(state.content) {
        snapshotFlow { state.content }
            .distinctUntilChanged()
            .debounce(200L)
            .collectLatest { previewContent = it }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val splitUnsupportedMsg = stringResource(R.string.quicknote_editor_preview_split_unsupported)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    // markdown-live-preview · 三态 toggle,循环 EDIT → PREVIEW → SPLIT → EDIT;
                    // 窄屏 / 非分屏时 SPLIT 被自动跳过,逻辑写在 onClick 内。
                    IconButton(onClick = {
                        val next = nextMode(previewMode, canSplit)
                        if (previewMode == PreviewMode.SPLIT && !canSplit) {
                            // 切到非分屏时给个反馈(虽然 LaunchedEffect 已自动回退,这里兜底)
                            scope.launch { snackbarHostState.showSnackbar(splitUnsupportedMsg) }
                        }
                        previewMode = next
                    }) {
                        Icon(
                            Icons.Filled.Visibility,
                            contentDescription = stringResource(R.string.quicknote_editor_preview_toggle_cd)
                        )
                    }
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 标题
            BasicTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                textStyle = MaterialTheme.typography.headlineMedium.merge(
                    TextStyle(color = MaterialTheme.colorScheme.onSurface)
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (state.title.isEmpty()) {
                            Text(
                                text = stringResource(R.string.quicknote_editor_title_hint),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                modifier = Modifier.padding(horizontal = spacing.md)
            )

            // 主体区域:三态
            when (previewMode) {
                PreviewMode.EDIT -> EditorBody(
                    state = state,
                    spacing = spacing,
                    focusRequester = contentFocusRequester,
                    onContentChange = viewModel::setContent
                )
                PreviewMode.PREVIEW -> PreviewBody(
                    markdown = previewContent,
                    spacing = spacing,
                    modifier = Modifier.weight(1f)
                )
                PreviewMode.SPLIT -> Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    EditorBody(
                        state = state,
                        spacing = spacing,
                        focusRequester = contentFocusRequester,
                        onContentChange = viewModel::setContent,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    VerticalDivider(thickness = 1)
                    PreviewBody(
                        markdown = previewContent,
                        spacing = spacing,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }

            // 标签 + 附件(原状保留)
            Text(
                text = stringResource(R.string.quicknote_editor_tags_count_fmt, state.tags.size),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs)
            )
            Surface(
                shape = RoundedCornerShape(cornerRadius.md),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.xs)
            ) {
                TagInputRow(
                    tags = state.tags,
                    onAddTag = viewModel::addTag,
                    onRemoveTag = viewModel::removeTag,
                    input = state.tagInputText,
                    onInputChange = viewModel::setTagInput
                )
            }
            if (tagsSummary.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.quicknote_editor_tags_saved_fmt, tagsSummary),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs)
                )
            }

            AttachmentRow(
                pendingUris = pendingUris,
                existingAttachments = visibleAttachments,
                onAddClick = {
                    attachmentPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRemovePending = viewModel::removePendingAttachment
            )
        }
    }
}

private fun nextMode(current: PreviewMode, canSplit: Boolean): PreviewMode = when (current) {
    PreviewMode.EDIT -> PreviewMode.PREVIEW
    PreviewMode.PREVIEW -> if (canSplit) PreviewMode.SPLIT else PreviewMode.EDIT
    PreviewMode.SPLIT -> PreviewMode.EDIT
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorBody(
    state: com.yy.writingwithai.feature.quicknote.model.NoteEditorUiState,
    spacing: com.yy.writingwithai.app.ui.theme.Spacing,
    focusRequester: FocusRequester,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // wikilink 自动补全相关 state(原状)
    var wikilinkPrefix by remember { mutableStateOf("") }
    val content = state.content
    val lastOpen by remember {
        derivedStateOf { content.lastIndexOf("[[") }
    }
    if (lastOpen >= 0) {
        val afterOpen = content.substring(lastOpen + 2)
        val closeIdx = afterOpen.indexOf("]]")
        if (closeIdx < 0 && afterOpen.length <= 64 && !afterOpen.contains("\n")) {
            LaunchedEffect(content) { wikilinkPrefix = afterOpen.trim() }
        } else {
            LaunchedEffect(content) { wikilinkPrefix = "" }
        }
    } else {
        LaunchedEffect(content) { wikilinkPrefix = "" }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md)) {
            if (wikilinkPrefix.isNotEmpty()) {
                WikilinkAutocomplete(
                    prefix = wikilinkPrefix,
                    onSelect = { selected ->
                        val currentContent = state.content
                        val openIdx = currentContent.lastIndexOf("[[")
                        if (openOpenGuard(openIdx)) {
                            wikilinkPrefix = ""
                            return@WikilinkAutocomplete
                        }
                        val tail = currentContent.substring(openIdx + 2)
                        val tailClose = tail.indexOf("]]")
                        if (tailClose >= 0) {
                            wikilinkPrefix = ""
                            return@WikilinkAutocomplete
                        }
                        val tailPrefix = tail.take(wikilinkPrefix.length)
                        if (tailPrefix != wikilinkPrefix) {
                            wikilinkPrefix = ""
                            return@WikilinkAutocomplete
                        }
                        val before = currentContent.substring(0, openIdx)
                        val after = currentContent.substring(openIdx + 2 + wikilinkPrefix.length)
                        onContentChange("$before[[$selected]]$after")
                        wikilinkPrefix = ""
                    }
                )
            }
        }
        BasicTextField(
            value = state.content,
            onValueChange = onContentChange,
            textStyle = MaterialTheme.typography.bodyLarge.merge(
                TextStyle(color = MaterialTheme.colorScheme.onSurface)
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm)
                .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.TopStart
                ) {
                    if (state.content.isEmpty()) {
                        Text(
                            text = stringResource(R.string.quicknote_editor_content_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

private fun openOpenGuard(openIdx: Int): Boolean = openIdx < 0

@Composable
private fun PreviewBody(
    markdown: String,
    spacing: com.yy.writingwithai.app.ui.theme.Spacing,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.md, vertical = spacing.sm)
    ) {
        MarkdownText(
            markdown = markdown,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun VerticalDivider(thickness: Int) {
    androidx.compose.material3.VerticalDivider(
        thickness = thickness.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

/**
 * ux-2026-06-28 #8:编辑器底部附件行。
 * - 「+ 图片」按钮 → 调起 photo picker。
 * - pending URIs / 已落库 NoteAttachmentEntity 各自以 chip 形式展示。
 * - pending 只有移除按钮(尚未压缩);已落库 chip 不提供移除(由 detail 屏统一管理)。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttachmentRow(
    pendingUris: List<android.net.Uri>,
    existingAttachments: List<com.yy.writingwithai.core.data.db.entity.NoteAttachmentEntity>,
    onAddClick: () -> Unit,
    onRemovePending: (android.net.Uri) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (existingAttachments.isNotEmpty() || pendingUris.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(existingAttachments, key = { it.id }) { att ->
                    ThumbnailChip(
                        label = att.localPath.substringAfterLast('/'),
                        onRemove = null
                    )
                }
                itemsIndexed(pendingUris) { index, uri ->
                    ThumbnailChip(
                        label = uri.lastPathSegment ?: uri.toString(),
                        onRemove = { onRemovePending(uri) }
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = onAddClick) {
                Text(stringResource(R.string.quicknote_detail_add_image))
            }
        }
    }
}

@Composable
private fun ThumbnailChip(label: String, onRemove: (() -> Unit)?) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(width = 120.dp, height = 64.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label.take(12),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
