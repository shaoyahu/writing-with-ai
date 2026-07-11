package com.yy.writingwithai.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.yy.writingwithai.feature.quicknote.model.EntityHighlight

/**
 * markdown-live-preview · 可复用 Markdown 渲染 Composable。
 *
 * 调 [render] 把 markdown 转 AnnotatedString,再用 Text 渲染。供详情屏和编辑器
 * 预览屏共用;详情屏在 entity 命中时仍走外层 `detectTapGestures` + `starsBefore`,
 * 行为与 M5 之前 `Text(buildEntityAnnotatedString(...))` 等价(渲染管线内已叠加
 * entity span + `✦`,`EntityCrossStar` 字符保留在输出文本里)。
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    entityHighlights: List<EntityHighlight> = emptyList(),
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge
) {
    val annotated = remember(markdown, entityHighlights, primaryColor) {
        render(
            markdown = markdown,
            entityHighlights = entityHighlights,
            primaryColor = primaryColor
        )
    }
    Text(text = annotated, modifier = modifier, style = style)
}
