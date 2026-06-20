package com.yy.writingwithai.feature.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * M4-4 极简 Markdown 渲染(参考 M4-4 design D6)。
 *
 * 支持子集:
 * - `# H1` / `## H2` 标题
 * - 普通段落(空行分隔)
 * - `- item` 无序列表
 * - `**bold**` 行内粗体
 *
 * 不支持:链接 / 图片 / 代码块 / 表格(隐私条款用到表格是 `|` 分隔的简表,
 * 当前解析为纯文本段落,已可读;完整 Markdown 留 M5 polish)。
 */
internal sealed interface MarkdownBlock {
    val id: Int

    data class Heading(
        override val id: Int,
        val level: Int,
        val text: AnnotatedString
    ) : MarkdownBlock

    data class Paragraph(
        override val id: Int,
        val text: AnnotatedString
    ) : MarkdownBlock

    data class ListItem(
        override val id: Int,
        val text: AnnotatedString
    ) : MarkdownBlock
}

internal fun parseSimpleMarkdown(raw: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = raw.lines()
    var id = 0
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.startsWith("# ") -> {
                blocks += MarkdownBlock.Heading(id++, level = 1, text = parseInline(line.removePrefix("# ").trim()))
                i++
            }
            line.startsWith("## ") -> {
                blocks += MarkdownBlock.Heading(id++, level = 2, text = parseInline(line.removePrefix("## ").trim()))
                i++
            }
            line.startsWith("- ") -> {
                blocks += MarkdownBlock.ListItem(id++, text = parseInline(line.removePrefix("- ").trim()))
                i++
            }
            line.isBlank() -> {
                i++
            }
            else -> {
                val sb = StringBuilder(line.trim())
                i++
                while (i < lines.size && lines[i].isNotBlank() &&
                    !lines[i].startsWith("# ") && !lines[i].startsWith("## ") && !lines[i].startsWith("- ")
                ) {
                    sb.append(' ').append(lines[i].trim())
                    i++
                }
                blocks += MarkdownBlock.Paragraph(id++, text = parseInline(sb.toString()))
            }
        }
    }
    return blocks
}

private fun parseInline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        if (text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end > i + 2) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(text.substring(i + 2, end))
                }
                i = end + 2
            } else {
                append(text[i])
                i++
            }
        } else {
            append(text[i])
            i++
        }
    }
}

@Composable
internal fun MarkdownBlockView(block: MarkdownBlock) {
    when (block) {
        is MarkdownBlock.Heading -> {
            val style =
                if (block.level == 1) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.titleMedium
                }
            Text(text = block.text, style = style, fontWeight = FontWeight.SemiBold)
        }
        is MarkdownBlock.Paragraph -> {
            Text(text = block.text, style = MaterialTheme.typography.bodyMedium)
        }
        is MarkdownBlock.ListItem -> {
            Text(text = "• " + block.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
