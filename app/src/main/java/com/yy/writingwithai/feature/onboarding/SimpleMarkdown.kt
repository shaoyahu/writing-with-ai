package com.yy.writingwithai.feature.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
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
 * 不支持:链接 / 图片 / 代码块 / 表格(隐私条款用到表格是 `|` 分隔的简表，
 * 当前解析为纯文本段落，已可读;完整 Markdown 留 M5 polish)。
 */
sealed interface MarkdownBlock {
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

fun parseSimpleMarkdown(raw: String): List<MarkdownBlock> {
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
fun MarkdownBlockView(block: MarkdownBlock) {
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

/**
 * animation-system-and-consent-redesign §7:条款卡片分组。
 *
 * 把 [parseSimpleMarkdown] 输出的 MarkdownBlock 列表按 H2 标题切片，每个 H2 段构成一个
 * [ConsentSection]。H1 标题被忽略(条款 markdown 顶层 H1 是「使用条款」之类的全局标题，
 * 不属于任意 section)。
 *
 * - [title]:H2 文本(去 ## 前缀 / trim)
 * - [icon]:按 title 关键词映射的 [ImageVector](§7.2)
 * - [summaryRes]:摘要 stringRes(由调用方提供，onboarding-redesign 设计阶段规划 5 个
 *   卡片摘要:数据 / AI / 第三方 / 撤回 / 联系)
 * - [blocks]:H2 之后的 block 列表，直到下一个 H2 或文档末尾(不包含 H2 自身，
 *   不包含 H1)
 */
data class ConsentSection(
    val id: Int,
    val title: String,
    val icon: ImageVector,
    val summaryRes: Int,
    val blocks: List<MarkdownBlock>
)

/**
 * animation-system-and-consent-redesign §7.1 + §7.2:按 H2 分组 + 关键词 → 图标映射。
 *
 * 调用方传入 `summaryResolver: (title) -> Int?` 把 title 映射到对应 stringRes(放在
 * ViewModel / 业务层管理 string key，避免本纯解析模块耦合 strings.xml)。
 *
 * 未匹配到关键词的 section 默认 [Icons.Filled.Storage](兜底「数据」主题)。
 */
fun parseGroupedMarkdown(raw: String, summaryResolver: (title: String) -> Int?): List<ConsentSection> {
    val blocks = parseSimpleMarkdown(raw)
    val sections = mutableListOf<ConsentSection>()
    var sectionId = 0
    var currentTitle: String? = null
    val currentBlocks = mutableListOf<MarkdownBlock>()

    fun flush() {
        val title = currentTitle ?: return
        val summary = summaryResolver(title)
        // 无论 summary 是否为 null，都要清空 currentBlocks，避免泄漏到下一个 section。
        val blocksToFlush = currentBlocks.toList()
        currentBlocks.clear()
        if (summary == null) return
        // CR-FIX-M8:sectionId 只在 section 实际入列时递增，避免被丢弃的 section 占用 ID。
        val nextId = sectionId
        sectionId = nextId + 1
        sections += ConsentSection(
            id = nextId,
            title = title,
            icon = iconForTitle(title),
            summaryRes = summary,
            blocks = blocksToFlush
        )
    }

    for (block in blocks) {
        when (block) {
            is MarkdownBlock.Heading -> {
                if (block.level == 2) {
                    flush()
                    currentTitle = block.text.text
                }
                // level == 1 忽略(顶层全局标题)
            }
            else -> {
                if (currentTitle != null) currentBlocks += block
            }
        }
    }
    flush()
    return sections
}

/**
 * animation-system-and-consent-redesign §7.2:标题关键词 → ImageVector 映射。
 *
 * 关键词按顺序匹配，匹配到返回对应图标;未匹配到返回 [Icons.Filled.Storage](数据兜底)。
 * 设计阶段规划的 5 类:数据 / AI / 第三方 / 撤回 / 联系。
 */
private fun iconForTitle(title: String): ImageVector {
    val normalized = title.lowercase()
    return when {
        "数据" in title || "data" in normalized || "存储" in title -> Icons.Filled.Storage
        "ai" in normalized || "智能" in title || "模型" in title -> Icons.Filled.SmartToy
        "第三方" in title || "third" in normalized || "provider" in normalized -> Icons.Filled.Public
        "撤回" in title || "删除" in title || "撤销" in title || "withdraw" in normalized -> Icons.Filled.Undo
        "联系" in title || "联系" in normalized || "contact" in normalized || "邮箱" in title -> Icons.Filled.Email
        else -> Icons.Filled.Storage
    }
}
