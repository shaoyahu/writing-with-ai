package com.yy.writingwithai.feature.aiwriting.streaming

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.R
import com.yy.writingwithai.core.ai.api.AiError
import com.yy.writingwithai.core.ai.api.AiStreamEvent
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.feature.aiwriting.error.toDisplayMessageRes

/**
 * AI 流式操作面板(M3 + ai-writing-ux-polish + ai-regenerate-versions)。
 *
 * 挂载在详情屏之上(不走 NavController，见 quick-note spec "Navigation routes unchanged")。
 * 6 态:
 * - Idle → 不渲染内容
 * - Streaming → 顶部 op + tab(版本 1..N)+ 当前版本 typing / partial + 取消
 * - PartialDone → 顶部 op + tab + 已 Done 的版本显示 finalText + 接受按钮;仍 Streaming
 *   的版本 tab 显示进行中状态;允许"早接受"已 Done 的位置
 * - Done → 顶部 op + tab + 各版本 finalText + 接受(默认 position=0)/拒绝/再生成/token chip
 * - Failed → 顶部 "出错" + error.toDisplayMessage() + 重试/去设置/关闭
 * - Replaced → 已替换提示 + 撤回按钮
 *
 * `skipPartiallyExpanded = true`,back / sheet 外点击触发 [onDismiss]。
 *
 * ai-regenerate-versions:增加 `onSelectVersion(position)` 回调,tab 切换 position
 * (不改 versions 内容,只切 UI 高亮);`onAccept` 接受当前 selectedPosition 的版本
 * (由 VM `acceptReplace(position=0)` 决定,本组件传当前 selectedPosition 入参)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamingPanel(
    state: AiActionUiState,
    onAccept: (Int) -> Unit,
    onSelectVersion: (Int) -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
    onRegenerate: () -> Unit,
    onClose: () -> Unit,
    onDismiss: () -> Unit,
    onUndo: () -> Unit = {},
    onDismissReplace: () -> Unit = {},
    onRetry: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    if (state is AiActionUiState.Idle) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (state) {
                is AiActionUiState.Streaming -> {
                    VersionBody(
                        op = state.op,
                        versions = state.versions,
                        selectedPosition = state.selectedPosition,
                        onSelectVersion = onSelectVersion,
                        onAccept = onAccept,
                        onReject = onReject,
                        onRegenerate = onRegenerate,
                        onClose = onClose,
                        onCancel = onCancel,
                        showCancel = true
                    )
                }
                is AiActionUiState.PartialDone -> {
                    VersionBody(
                        op = state.op,
                        versions = state.versions,
                        selectedPosition = state.selectedPosition,
                        onSelectVersion = onSelectVersion,
                        onAccept = onAccept,
                        onReject = onReject,
                        onRegenerate = onRegenerate,
                        onClose = onClose,
                        onCancel = onCancel,
                        // PartialDone:没取消按钮(底层协程仍在跑),"拒绝"按钮 abort 整 N-version。
                        showCancel = false
                    )
                }
                is AiActionUiState.Done -> {
                    VersionBody(
                        op = state.op,
                        versions = state.versions,
                        selectedPosition = state.selectedPosition,
                        onSelectVersion = onSelectVersion,
                        onAccept = onAccept,
                        onReject = onReject,
                        onRegenerate = onRegenerate,
                        onClose = onClose,
                        onCancel = onCancel,
                        showCancel = false
                    )
                }
                is AiActionUiState.Failed -> {
                    val ctx = LocalContext.current
                    HeaderRow(title = stringResource(R.string.aiwriting_panel_failed_title), usage = null)
                    ScrollableBody(text = ctx.getString(state.error.toDisplayMessageRes()))
                    FailedActionRow(
                        error = state.error,
                        onRetry = onRetry,
                        onNavigateToSettings = onNavigateToSettings,
                        onClose = onClose
                    )
                }
                is AiActionUiState.Replaced -> {
                    HeaderRow(
                        title = stringResource(opTitleRes(state.op)) +
                            " · " +
                            stringResource(R.string.aiwriting_panel_replaced),
                        usage = null
                    )
                    ScrollableBody(text = stringResource(R.string.aiwriting_panel_replaced_hint))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        TextButton(onClick = onDismissReplace) {
                            Text(stringResource(R.string.aiwriting_panel_close))
                        }
                        Button(onClick = onUndo) {
                            Text(stringResource(R.string.aiwriting_panel_undo))
                        }
                    }
                }
                AiActionUiState.Idle -> Unit
            }
        }
    }
}

/**
 * ai-regenerate-versions:多版本共用的 body 渲染 — 顶部 HeaderRow + SecondaryTabRow(版本 tab)
 * + 当前选中版本的累积文本 + 底部按钮行。
 *
 * Streaming:显示 TypingIndicator(若 delta 为空)/ partialText
 * PartialDone/Done:显示 finalText + diff 高亮 + 接受按钮(enabled = isAcceptable)
 *
 * 按钮矩阵(state-aware):
 * - Streaming  + showCancel:仅"取消"
 * - PartialDone:可"拒绝"(abort 整 N-version);当前选中版本若 Done 则"接受"
 * - Done:       "再生成" + "拒绝" + "接受"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VersionBody(
    op: WritingOp,
    versions: List<AiVersion>,
    selectedPosition: Int,
    onSelectVersion: (Int) -> Unit,
    onAccept: (Int) -> Unit,
    onReject: () -> Unit,
    onRegenerate: () -> Unit,
    onClose: () -> Unit,
    onCancel: () -> Unit,
    showCancel: Boolean
) {
    val safePos = selectedPosition.coerceIn(0, versions.lastIndex.coerceAtLeast(0))
    val current = versions[safePos]
    val titleSuffix = when (current.state) {
        AiVersion.State.Streaming -> stringResource(R.string.aiwriting_panel_streaming)
        AiVersion.State.Done -> stringResource(R.string.aiwriting_panel_done_suffix)
        AiVersion.State.Failed -> stringResource(R.string.aiwriting_panel_failed_title)
    }
    HeaderRow(
        title = stringResource(opTitleRes(op)) + " · " + titleSuffix,
        usage = current.usage
    )
    if (versions.size > 1) {
        VersionTabs(
            versions = versions,
            selectedPosition = safePos,
            onSelect = onSelectVersion
        )
    }
    // 当前选中版本的累积文本。
    // - Streaming:跟原来一样,UI 用 remember 拼接 delta
    // - PartialDone/Done:finalText 已完整,直接渲染(diff 高亮)
    when (current.state) {
        AiVersion.State.Streaming -> {
            // fix-review-r1 F8 4.8:LaunchedEffect key 同时绑 safePos + accumulatedLength,
            // 原 key=contentLen 单一项:切到第 N 个版本后回到版本 1,如果新版本的
            // accumulatedLength 恰好跟旧版本遗留值一样,LaunchedEffect 不重启,
            // 旧版 accumulated 字符串不会被清空 → 面板瞬间出现版本串号 bug。
            // 包进 Pair key,把"切换版本"显式纳入重置条件。
            var accumulated by remember { mutableStateOf("") }
            LaunchedEffect(safePos, current.accumulatedLength) {
                accumulated = ""
            }
            LaunchedEffect(current.delta) {
                val chunk = current.delta
                if (chunk.isNotEmpty()) {
                    accumulated = accumulated + chunk
                }
            }
            val displayText = accumulated
            if (displayText.isEmpty()) {
                TypingIndicator()
            } else {
                ScrollableBody(text = displayText)
            }
        }
        AiVersion.State.Done -> {
            if (current.finalText.isBlank()) {
                ScrollableBody(text = stringResource(R.string.aiwriting_panel_empty_result))
            } else {
                ScrollableAnnotatedBody(text = diffHighlight(current.finalText, current.finalText))
            }
        }
        AiVersion.State.Failed -> {
            val ctx = LocalContext.current
            val msg = current.error?.error?.toDisplayMessageRes()
            val text = msg?.let { ctx.getString(it) }
                ?: stringResource(R.string.aiwriting_version_failed)
            ScrollableBody(text = text)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
    ) {
        when {
            // Streaming 态:仅取消(没取消就空行)
            showCancel -> {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.aiwriting_panel_cancel))
                }
            }
            // PartialDone:任一 tab 接受按钮 + 拒绝 abort
            current.state == AiVersion.State.Done -> {
                TextButton(onClick = onReject) {
                    Text(stringResource(R.string.aiwriting_panel_reject))
                }
                Button(
                    onClick = { onAccept(safePos) },
                    enabled = current.isAcceptable
                ) {
                    Text(stringResource(R.string.aiwriting_panel_accept))
                }
            }
            // 当前选中版本 Failed:无接受,只拒 / 关
            current.state == AiVersion.State.Failed -> {
                TextButton(onClick = onReject) {
                    Text(stringResource(R.string.aiwriting_panel_reject))
                }
            }
            // 仍 Streaming(PartialDone 下选中非 Done 的位置)
            else -> {
                TextButton(onClick = onReject) {
                    Text(stringResource(R.string.aiwriting_panel_reject))
                }
            }
        }
    }
    // Done 态额外加"再生成 / 关闭"行(只在全部终态 + 没在上面绘制时)
    if (versions.all { it.state != AiVersion.State.Streaming }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.aiwriting_panel_close))
            }
            TextButton(onClick = onRegenerate) {
                Text(stringResource(R.string.aiwriting_panel_regenerate))
            }
        }
    }
}

/**
 * ai-regenerate-versions:多版本 tab 行。`SecondaryTabRow` 是 Material 3 推荐 API,
 * 内容跟高亮都由 [selectedPosition] 控制。每个 tab 标题 "版本 N",尾部加状态点(✓/✗/·)。
 *
 * Failed tab 不禁用(用户仍可点查看错误);仅当 current 是 Done 才在 tab 文本加 ✓。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VersionTabs(versions: List<AiVersion>, selectedPosition: Int, onSelect: (Int) -> Unit) {
    SecondaryTabRow(selectedTabIndex = selectedPosition) {
        versions.forEachIndexed { idx, version ->
            val statusMark = when (version.state) {
                AiVersion.State.Streaming -> stringResource(R.string.aiwriting_version_mark_streaming)
                AiVersion.State.Done -> stringResource(R.string.aiwriting_version_mark_done)
                AiVersion.State.Failed -> stringResource(R.string.aiwriting_version_mark_failed)
            }
            Tab(
                selected = idx == selectedPosition,
                onClick = { onSelect(idx) },
                text = {
                    Text(
                        text = stringResource(R.string.aiwriting_version_tab_title, idx + 1) + statusMark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

/** 3 圆点脉冲 typing indicator(Streaming 态 partialText 为空时显示)。 */
@Composable
private fun TypingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 600
                        0.3f at 0
                        1f at 200
                        0.3f at 400
                    },
                    initialStartOffset = androidx.compose.animation.core.StartOffset(index * 200)
                ),
                label = "dot_$index"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        shape = MaterialTheme.shapes.extraSmall
                    )
            )
        }
    }
}

/** Failed 态按钮行:根据错误类型显示重试/去设置/关闭。 */
@Composable
private fun FailedActionRow(
    error: AiError,
    onRetry: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
    ) {
        when (error) {
            is AiError.Network, is AiError.Timeout -> {
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.aiwriting_panel_close))
                }
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.aiwriting_panel_retry))
                }
            }
            is AiError.Auth -> {
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.aiwriting_panel_close))
                }
                Button(onClick = onNavigateToSettings) {
                    Text(stringResource(R.string.aiwriting_panel_go_settings))
                }
            }
            is AiError.InsufficientBalance -> {
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.aiwriting_panel_close))
                }
                Button(onClick = onNavigateToSettings) {
                    Text(stringResource(R.string.aiwriting_panel_go_settings))
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.aiwriting_panel_balance_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                Button(onClick = onClose) {
                    Text(stringResource(R.string.aiwriting_panel_close))
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(title: String, usage: AiStreamEvent.Usage?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = true)
        )
        if (usage != null) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        stringResource(
                            R.string.aiwriting_panel_usage_fmt,
                            usage.inputTokens,
                            usage.outputTokens
                        )
                    )
                },
                colors = AssistChipDefaults.assistChipColors()
            )
        }
    }
}

@Composable
private fun ScrollableBody(text: String) {
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .animateContentSize()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ScrollableAnnotatedBody(text: AnnotatedString) {
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .animateContentSize()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** diff 高亮:新增部分用 primaryContainer 背景标记。 */
@Composable
private fun diffHighlight(original: String, modified: String): AnnotatedString {
    val highlightColor = MaterialTheme.colorScheme.primaryContainer
    if (original.isEmpty() || original == modified) {
        return AnnotatedString(modified)
    }
    val commonPrefixLen = commonPrefixLength(original, modified)
    val commonSuffixLen = commonSuffixLength(
        original.substring(commonPrefixLen),
        modified.substring(commonPrefixLen)
    )
    val addedStart = commonPrefixLen
    val addedEnd = modified.length - commonSuffixLen

    return buildAnnotatedString {
        append(modified)
        if (addedStart < addedEnd) {
            addStyle(
                style = SpanStyle(background = highlightColor),
                start = addedStart,
                end = addedEnd
            )
        }
    }
}

private fun commonPrefixLength(a: String, b: String): Int {
    val maxLen = minOf(a.length, b.length)
    var i = 0
    while (i < maxLen && a[i] == b[i]) i++
    return i
}

private fun commonSuffixLength(a: String, b: String): Int {
    var i = 0
    while (i < a.length && i < b.length && a[a.lastIndex - i] == b[b.lastIndex - i]) i++
    return i
}

private fun opTitleRes(op: WritingOp): Int = when (op) {
    WritingOp.EXPAND -> R.string.aiwriting_panel_title_expand
    WritingOp.POLISH -> R.string.aiwriting_panel_title_polish
    WritingOp.ORGANIZE -> R.string.aiwriting_panel_title_organize
    WritingOp.SUMMARIZE -> R.string.aiwriting_panel_title_summarize
    WritingOp.TRANSLATE -> R.string.aiwriting_panel_title_translate
}

// ===== Previews =====

@androidx.compose.ui.tooling.preview.Preview(name = "Streaming Single", showBackground = true)
@Composable
private fun StreamingPanelStreamingSinglePreview() {
    MaterialTheme {
        StreamingPanel(
            state = AiActionUiState.Streaming(
                op = WritingOp.EXPAND,
                versions = listOf(AiVersion(position = 0, delta = "正在扩写中...", accumulatedLength = 6)),
                selectedPosition = 0,
                originalText = ""
            ),
            onAccept = {},
            onSelectVersion = {},
            onReject = {},
            onCancel = {},
            onRegenerate = {},
            onClose = {},
            onDismiss = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Streaming 3-version", showBackground = true)
@Composable
private fun StreamingPanelStreaming3Preview() {
    MaterialTheme {
        StreamingPanel(
            state = AiActionUiState.Streaming(
                op = WritingOp.EXPAND,
                versions = listOf(
                    AiVersion(
                        position = 0,
                        state = AiVersion.State.Done,
                        finalText = "版本 1 已完成",
                        actualModel = "deepseek-chat"
                    ),
                    AiVersion(position = 1, delta = "正在生成中", accumulatedLength = 5, actualModel = "deepseek-chat"),
                    AiVersion(position = 2, delta = "", accumulatedLength = 0, actualModel = "deepseek-chat")
                ),
                selectedPosition = 1,
                originalText = "源文本"
            ),
            onAccept = {},
            onSelectVersion = {},
            onReject = {},
            onCancel = {},
            onRegenerate = {},
            onClose = {},
            onDismiss = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Done 3-version", showBackground = true)
@Composable
private fun StreamingPanelDone3Preview() {
    MaterialTheme {
        StreamingPanel(
            state = AiActionUiState.Done(
                op = WritingOp.POLISH,
                versions = listOf(
                    AiVersion(
                        position = 0,
                        state = AiVersion.State.Done,
                        finalText = "今日天朗气清",
                        actualModel = "deepseek-chat"
                    ),
                    AiVersion(
                        position = 1,
                        state = AiVersion.State.Done,
                        finalText = "今日阳光明媚",
                        actualModel = "deepseek-chat"
                    ),
                    AiVersion(
                        position = 2,
                        state = AiVersion.State.Failed,
                        error = AiStreamEvent.Failed(AiError.Network(500, "timeout"), false),
                        actualModel = "deepseek-chat"
                    )
                ),
                selectedPosition = 0,
                originalText = "今天天气很好"
            ),
            onAccept = {},
            onSelectVersion = {},
            onReject = {},
            onCancel = {},
            onRegenerate = {},
            onClose = {},
            onDismiss = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Failed", showBackground = true)
@Composable
private fun StreamingPanelFailedPreview() {
    MaterialTheme {
        StreamingPanel(
            state = AiActionUiState.Failed(
                op = WritingOp.ORGANIZE,
                error = AiError.Network(code = 500, detail = "timeout")
            ),
            onAccept = {},
            onSelectVersion = {},
            onReject = {},
            onCancel = {},
            onRegenerate = {},
            onClose = {},
            onDismiss = {}
        )
    }
}
