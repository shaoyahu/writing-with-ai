package com.yy.writingwithai.core.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * rich-text-editor · Markdown 编辑器接口。
 * v1: 简单封装 BasicTextField + Markdown 预览切换。
 * v2: 引入第三方 Markdown 渲染库或自建 AnnotatedString 渲染。
 */
interface MarkdownEditor {
    @Composable
    fun Editor(text: String, onTextChange: (String) -> Unit, modifier: Modifier, placeholder: String = "")

    @Composable
    fun Preview(markdown: String, modifier: Modifier)
}
