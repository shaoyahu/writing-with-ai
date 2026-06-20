package com.yy.writingwithai.feature.aiwriting.streaming

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.R
import com.yy.writingwithai.core.ai.api.AiStreamEvent
import com.yy.writingwithai.core.ai.api.WritingOp
import com.yy.writingwithai.feature.aiwriting.error.toDisplayMessage

/**
 * AI 流式操作面板(M3)。
 *
 * 挂载在详情屏之上(不走 NavController,见 quick-note spec "Navigation routes unchanged")。
 * 4 态:
 * - Idle → 不渲染内容
 * - Streaming → 顶部 op + "进行中" + partialText + 取消按钮
 * - Done → 顶部 op + "完成" + finalText + 接受 / 拒绝 / 再生成 + token chip
 * - Failed → 顶部 "出错" + error.toDisplayMessage() + 关闭按钮
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
    onDismiss: () -> Unit
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
                    // L3 修:HeaderRow 已经有"· 进行中…"副标题,body 区不再重复,
                    // partialText 空时用占位三个点(用户看到"等 AI 输出"自然状态)。
                    ScrollableBody(text = state.partialText.ifBlank { "…" })
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
                    ScrollableBody(text = state.finalText)
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
                is AiActionUiState.Failed -> {
                    val ctx = LocalContext.current
                    HeaderRow(title = stringResource(R.string.aiwriting_panel_failed_title), usage = null)
                    ScrollableBody(text = state.error.toDisplayMessage(ctx))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(onClick = onClose) {
                            Text(stringResource(R.string.aiwriting_panel_close))
                        }
                    }
                }
                AiActionUiState.Idle -> Unit
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
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun opTitleRes(op: WritingOp): Int = when (op) {
    WritingOp.EXPAND -> R.string.aiwriting_panel_title_expand
    WritingOp.POLISH -> R.string.aiwriting_panel_title_polish
    WritingOp.ORGANIZE -> R.string.aiwriting_panel_title_organize
}
