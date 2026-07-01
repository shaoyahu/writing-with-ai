package com.yy.writingwithai.feature.aiwriting.action

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.yy.writingwithai.R

/**
 * AI 操作 ActionSheet(M3 fix - Popup + arrow)。
 *
 * 详情屏选中文本时，从底部栏 ✨ 按钮弹出的操作菜单。提供 6 项:
 * - 扩写 / 润色 / 整理 / 摘要 / 翻译(走 ViewModel 触发 AiGateway)
 * - 复制(走 ClipboardManager，不走 AiGateway)
 *
 * 见 ai-actions spec "ActionSheet shows available AI operations on selection"。
 */
@Composable
fun ActionSheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onExpand: () -> Unit,
    onPolish: () -> Unit,
    onOrganize: () -> Unit,
    onSummarize: () -> Unit,
    onTranslate: () -> Unit,
    onCopy: () -> Unit
) {
    if (!expanded) return

    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val arrowHeight = 8.dp
    val arrowWidth = 16.dp

    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(x = 0, y = -16),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Menu card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = containerColor,
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    ActionSheetItem(
                        icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                        text = stringResource(R.string.aiwriting_action_expand),
                        onClick = {
                            onDismiss()
                            onExpand()
                        }
                    )
                    ActionSheetItem(
                        icon = { Icon(Icons.Filled.TextFields, contentDescription = null) },
                        text = stringResource(R.string.aiwriting_action_polish),
                        onClick = {
                            onDismiss()
                            onPolish()
                        }
                    )
                    ActionSheetItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        text = stringResource(R.string.aiwriting_action_organize),
                        onClick = {
                            onDismiss()
                            onOrganize()
                        }
                    )
                    ActionSheetItem(
                        icon = { Icon(Icons.Filled.ShortText, contentDescription = null) },
                        text = stringResource(R.string.aiwriting_action_summarize),
                        onClick = {
                            onDismiss()
                            onSummarize()
                        }
                    )
                    ActionSheetItem(
                        icon = { Icon(Icons.Filled.Translate, contentDescription = null) },
                        text = stringResource(R.string.aiwriting_action_translate),
                        onClick = {
                            onDismiss()
                            onTranslate()
                        }
                    )
                    ActionSheetItem(
                        icon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                        text = stringResource(R.string.aiwriting_action_copy),
                        onClick = {
                            onDismiss()
                            onCopy()
                        }
                    )
                }
            }

            // Arrow triangle pointing down to the button
            Canvas(modifier = Modifier.size(width = arrowWidth, height = arrowHeight)) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(x = w / 2f, y = h) // bottom center (tip)
                    lineTo(x = 0f, y = 0f) // top-left
                    lineTo(x = w, y = 0f) // top-right
                    close()
                }
                drawPath(path = path, color = containerColor)
            }
        }
    }
}

@Composable
private fun ActionSheetItem(icon: @Composable () -> Unit, text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(Modifier.width(12.dp))
            Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "ActionSheet", showBackground = true)
@Composable
private fun ActionSheetPreview() {
    MaterialTheme {
        ActionSheet(expanded = true, onDismiss = {
        }, onExpand = {}, onPolish = {}, onOrganize = {}, onSummarize = {}, onTranslate = {}, onCopy = {})
    }
}
