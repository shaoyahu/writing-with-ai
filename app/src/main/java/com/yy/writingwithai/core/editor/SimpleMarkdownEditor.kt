package com.yy.writingwithai.core.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

/**
 * rich-text-editor · v1 简单 Markdown 编辑器(BasicTextField + 纯文本预览)。
 * v2 替换为真正的 Markdown 渲染编辑器。
 */
class SimpleMarkdownEditor : MarkdownEditor {
    @Composable
    override fun Editor(text: String, onTextChange: (String) -> Unit, modifier: Modifier, placeholder: String) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                innerTextField()
            }
        )
    }

    @Composable
    override fun Preview(markdown: String, modifier: Modifier) {
        // v1: 纯文本预览; v2: Markdown 渲染
        Text(
            text = markdown,
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
