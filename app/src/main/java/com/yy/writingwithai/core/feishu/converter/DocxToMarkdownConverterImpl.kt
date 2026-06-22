package com.yy.writingwithai.core.feishu.converter

import javax.inject.Inject
import javax.inject.Singleton

/**
 * markdown-docx-converter · FeishuBlock 列表 → Markdown 字符串。
 *
 * spec: openspec/changes/markdown-docx-converter/spec.md
 * "Docx to Markdown conversion"
 */
@Singleton
class DocxToMarkdownConverterImpl @Inject constructor() : DocxToMarkdownConverter {

    override suspend fun convert(blocks: List<FeishuBlock>): String {
        val sb = StringBuilder()
        blocks.forEachIndexed { idx, block ->
            if (idx > 0) sb.append('\n')
            when (block) {
                is FeishuBlock.Heading -> {
                    sb.append("#".repeat(block.level)).append(' ').append(runsToMarkdown(block.runs))
                }
                is FeishuBlock.Paragraph -> sb.append(runsToMarkdown(block.runs))
                is FeishuBlock.Bullet -> {
                    block.items.forEach { item ->
                        sb.append("- ").append(runsToMarkdown(item)).append('\n')
                    }
                    if (sb.isNotEmpty() && sb.last() == '\n') sb.deleteCharAt(sb.length - 1)
                }
                is FeishuBlock.Ordered -> {
                    block.items.forEachIndexed { i, item ->
                        sb.append("${i + 1}. ").append(runsToMarkdown(item)).append('\n')
                    }
                    if (sb.isNotEmpty() && sb.last() == '\n') sb.deleteCharAt(sb.length - 1)
                }
                is FeishuBlock.CodeBlock -> {
                    sb.append("```").append(block.language).append('\n')
                    sb.append(block.text).append('\n')
                    sb.append("```")
                }
                is FeishuBlock.Quote -> sb.append("> ").append(runsToMarkdown(block.runs))
                is FeishuBlock.Divider -> sb.append("---")
                is FeishuBlock.Image -> sb.append("[${block.placeholder}]")
                is FeishuBlock.Table -> {
                    if (block.rows.isEmpty()) return@forEachIndexed
                    val cols = block.rows[0].size
                    block.rows.forEachIndexed { i, row ->
                        val padded = row + List((cols - row.size).coerceAtLeast(0)) { "" }
                        sb.append("| ").append(padded.joinToString(" | ")).append(" |\n")
                        if (i == 0) {
                            sb.append("|").append(List(cols) { "---" }.joinToString("|")).append("|\n")
                        }
                    }
                    if (sb.isNotEmpty() && sb.last() == '\n') sb.deleteCharAt(sb.length - 1)
                }
                is FeishuBlock.Unsupported -> sb.append(block.raw)
            }
        }
        return sb.toString()
    }

    private fun runsToMarkdown(runs: List<Run>): String {
        return runs.joinToString("") { run ->
            var text = run.text
            if (run.linkUrl != null) text = "[$text](${run.linkUrl})"
            if (run.code) text = "`$text`"
            if (run.italic) text = "*$text*"
            if (run.bold) text = "**$text**"
            text
        }
    }
}
