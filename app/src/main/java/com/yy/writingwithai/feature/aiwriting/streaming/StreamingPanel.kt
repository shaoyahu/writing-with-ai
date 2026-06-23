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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.yy.writingwithai.feature.aiwriting.error.toDisplayMessage

/**
 * AI 流式操作面板(M3 + ai-writing-ux-polish)。
 *
 * 挂载在详情屏之上(不走 NavController,见 quick-note spec "Navigation routes unchanged")。
 * 5 态:
 * - Idle → 不渲染内容
 * - Streaming → 顶部 op + "进行中" + typing indicator / partialText + 取消按钮
 * - Done → 顶部 op + "完成" + diff 高亮 finalText + 接受 / 拒绝 / 再生成 + token chip
 * - Failed → 顶部 "出错" + error.toDisplayMessage() + 重试/去设置/关闭
 * - Replaced → 已替换提示 + 撤回按钮
 *
 * `skipPartiallyExpanded = true`,back / sheet 外点击触发 [onDismiss]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamingPanel(
    state: AiActionUiState,
    onAccept: () -> Unit,
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
                    HeaderRow(
                        title =
                        stringResource(opTitleRes(state.op)) +
                            " · " +
                            stringResource(R.string.aiwriting_panel_streaming),
                        usage = null
                    )
                    if (state.partialText.isEmpty()) {
                        TypingIndicator()
                    } else {
                        ScrollableBody(text = state.partialText)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onCancel) {
                            Text(stringResource(R.string.aiwriting_panel_cancel))
                        }
                    }
                }
                is AiActionUiState.Done -> {
                    HeaderRow(
                        title = stringResource(opTitleRes(state.op)),
                        usage = state.usage
                    )
                    if (state.finalText.isBlank()) {
                        ScrollableBody(text = stringResource(R.string.aiwriting_panel_empty_result))
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
                    } else {
                        ScrollableAnnotatedBody(
                            text = diffHighlight(state.originalText, state.finalText)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                        ) {
                            TextButton(onClick = onRegenerate) {
                                Text(stringResource(R.string.aiwriting_panel_regenerate))
                            }
                            TextButton(onClick = onReject) {
                                Text(stringResource(R.string.aiwriting_panel_reject))
                            }
                            Button(
                                onClick = onAccept,
                                enabled = state.finalText.isNotBlank()
                            ) {
                                Text(stringResource(R.string.aiwriting_panel_accept))
                            }
                        }
                    }
                }
                is AiActionUiState.Failed -> {
                    val ctx = LocalContext.current
                    HeaderRow(title = stringResource(R.string.aiwriting_panel_failed_title), usage = null)
                    ScrollableBody(text = state.error.toDisplayMessage(ctx))
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
