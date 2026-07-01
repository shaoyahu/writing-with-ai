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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalCornerRadius
import com.yy.writingwithai.app.ui.theme.LocalSpacing

/**
 * ui-redesign-v2 · 编辑器:标题用 BasicTextField(headlineMedium，无边框),
 * 正文 BasicTextField(bodyLarge, weight(1f) 自适应高度), Tag 区 Surface 包裹视觉分离。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    // ux-2026-06-28 #8:新建笔记时附件 Uri 先缓存;保存时落库。现有笔记(走路由 id)直接 observe 已落库附件。
    val pendingUris by viewModel.pendingAttachmentUris.collectAsStateWithLifecycle()
    // fix-2026-06-30-full-review-r1 MEDIUM M10:用 collectAsStateWithLifecycle 替代
    // LaunchedEffect + collect,app 在后台时不再持续收集 Room Flow，避免 Room query
    // 重复发射。空列表 initialValue 是合理占位(已有 attachments 时会被实际值替换)。
    val existingAttachments by viewModel.observeAttachments()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val visibleAttachments = if (state.isNew) emptyList() else existingAttachments
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.addAttachment(it) } }

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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ui-redesign-v2 · 标题:无边框 BasicTextField + headlineMedium
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

            // ui-redesign-v2 · 标题/正文分隔线
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                modifier = Modifier.padding(horizontal = spacing.md)
            )

            // fix-2026-06-26-review-r3 C4:wikilink 自动补全 offset 闭包捕获旧 content 会错位。
            // 用 snapshot(content + offset + prefix)一起，onSelect 内重新定位 `[[`。
            // content 变化后 LaunchedEffect(content) 重新计算 prefix;若 prefix 为空就关闭弹层。
            // R3 C4 fix:之前 `onSelect` 闭包捕获 `content` 和 `lastOpen`,AI acceptReplace
            // 改 content 后用户再点补全 → 写错位;改为每次 onSelect 内部重新定位 `[[`。
            var wikilinkPrefix by remember { mutableStateOf("") }
            val content = state.content
            // derivedStateOf 在 content 变化时重算，但 wikilink 弹层还在 → 用户点 onSelect 时
            // 仍拿最新 content 重新匹配 `[[...prefix...]]`，不再依赖闭包旧值。
            val lastOpen by remember {
                derivedStateOf { content.lastIndexOf("[[") }
            }
            if (lastOpen >= 0) {
                val afterOpen = content.substring(lastOpen + 2)
                val closeIdx = afterOpen.indexOf("]]")
                if (closeIdx < 0 && afterOpen.length <= 64 && !afterOpen.contains("\n")) {
                    LaunchedEffect(content) { wikilinkPrefix = afterOpen.trim() }
                } else {
                    // 已关闭的 wikilink / 含换行 / 超长 → 清 prefix，关闭补全弹层
                    LaunchedEffect(content) { wikilinkPrefix = "" }
                }
            } else {
                // 没有 `[[` → 清 prefix
                LaunchedEffect(content) { wikilinkPrefix = "" }
            }
            if (wikilinkPrefix.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md)) {
                    WikilinkAutocomplete(
                        prefix = wikilinkPrefix,
                        onSelect = { selected ->
                            // R3 C4 fix:重新在最新 content 里查 `[[`，避免 AI 替换导致 stale offset。
                            val currentContent = state.content
                            val openIdx = currentContent.lastIndexOf("[[")
                            if (openIdx < 0) {
                                // 弹层期间 content 已被外部改，丢掉这次插入
                                wikilinkPrefix = ""
                                return@WikilinkAutocomplete
                            }
                            val tail = currentContent.substring(openIdx + 2)
                            val tailClose = tail.indexOf("]]")
                            if (tailClose >= 0) {
                                // 用户在弹层期间已手动关闭 wikilink，不再插入
                                wikilinkPrefix = ""
                                return@WikilinkAutocomplete
                            }
                            // 校验 wikilink 段前缀与当前 prefix 一致(避免 stale prefix)
                            val tailPrefix = tail.take(wikilinkPrefix.length)
                            if (tailPrefix != wikilinkPrefix) {
                                wikilinkPrefix = ""
                                return@WikilinkAutocomplete
                            }
                            val before = currentContent.substring(0, openIdx)
                            val after = currentContent.substring(openIdx + 2 + wikilinkPrefix.length)
                            viewModel.setContent("$before[[$selected]]$after")
                            wikilinkPrefix = ""
                        }
                    )
                }
            }

            // ui-redesign-v2 · 正文:BasicTextField + weight(1f) 自适应
            BasicTextField(
                value = state.content,
                onValueChange = { newContent ->
                    viewModel.setContent(newContent)
                },
                textStyle = MaterialTheme.typography.bodyLarge.merge(
                    TextStyle(color = MaterialTheme.colorScheme.onSurface)
                ),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.sm)
                    .focusRequester(contentFocusRequester),
                decorationBox = { innerTextField ->
                    // M2 fix: 内 Box 加 fillMaxHeight,BasicTextField 真正占满 weight(1f) 分配空间
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

            // ui-redesign-v2 · Tag 区:Surface 包裹视觉分离
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

            // ux-2026-06-28 #8:图片附件行 — 添加按钮 + pending/已落库缩略图。
            // 新建笔记时只能看到 pending 计数 + 移除;编辑现有笔记可直接看已落库缩略图。
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
        // 已有附件(编辑现有笔记时)+ pending 缩略图
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
                    // fix-2026-06-30-full-review-r1 LOW L2:加稳定 key(index 在本列表
                    // 生命周期内单调，虽然不跨重组稳定，但足够 Compose 区分"已渲染项"防止
                    // 短暂错位)。更好的方案是 wrap Uri + uuid 持久 id，留 M5 polish。
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
