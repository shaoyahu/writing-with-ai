package com.yy.writingwithai.core.feishu.converter

import javax.inject.Inject
import javax.inject.Singleton

/**
 * markdown-docx-converter · Markdown → FeishuBlock 列表。
 *
 * 纯函数 regex 行优先 + 块级解析(design D2)。无 Android / 网络 / IO 依赖。
 *
 * spec: openspec/changes/markdown-docx-converter/spec.md
 * design: openspec/changes/markdown-docx-converter/design.md D2 / D3 / D5
 */
@Singleton
class MarkdownToDocxConverterImpl @Inject constructor() : MarkdownToDocxConverter {

    override suspend fun convert(markdown: String): List<FeishuBlock> {
        val blocks = mutableListOf<FeishuBlock>()
        val lines = markdown.lines()

        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            // r2 修:统一 trimStart 一次，后续分支只看 trimmed line
            val line = raw.trimStart()

            // 空行跳过
            if (line.isBlank()) {
                i++
                continue
            }

            // 代码块:连续 ``` 包围
            // fix-2026-06-26-review-r3 HIGH H10:open fence 也用 isFenceOpen 校验，要求行首
            // 无缩进，避免误把 4+ 空格缩进的 ``` 当作代码块开始。
            if (isFenceOpen(raw)) {
                val (codeBlock, next) = parseCodeBlock(lines, i)
                blocks += codeBlock
                i = next
                continue
            }

            // 表格:连续 | 行
            if (line.contains("|") && i + 1 < lines.size && TABLE_SEPARATOR.matches(lines[i + 1].trim())) {
                val (tableBlock, next) = parseTable(lines, i)
                blocks += tableBlock
                i = next
                continue
            }

            // 图片:行内 `![alt](path)`
            if (line.startsWith("![")) {
                blocks += parseImage(line)
                i++
                continue
            }

            // heading / quote / divider / list / paragraph 单行处理
            when {
                HEADING.matches(line) -> {
                    val m = HEADING.matchEntire(line)!!
                    val level = m.groupValues[1].length
                    val text = m.groupValues[2]
                    blocks += FeishuBlock.Heading(level, parseRuns(text))
                }
                line.startsWith(">") -> {
                    // 收集连续 quote 行;每行剥一个 `> ` 前缀(spec 不要求嵌套 quote)
                    val runs = StringBuilder()
                    while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                        val text = lines[i].trimStart().removePrefix(">").trimStart()
                        if (runs.isNotEmpty()) runs.append('\n')
                        runs.append(text)
                        i++
                    }
                    blocks += FeishuBlock.Quote(parseRuns(runs.toString()))
                    continue
                }
                DIVIDER.matches(line.trim()) -> blocks += FeishuBlock.Divider
                BULLET.matches(line) -> {
                    // 收集连续 bullet
                    val items = mutableListOf<List<Run>>()
                    while (i < lines.size && BULLET.matches(lines[i].trimStart())) {
                        val m = BULLET.matchEntire(lines[i].trimStart())!!
                        items += parseRuns(m.groupValues[1])
                        i++
                    }
                    blocks += FeishuBlock.Bullet(items)
                    continue
                }
                ORDERED.matches(line) -> {
                    val items = mutableListOf<List<Run>>()
                    while (i < lines.size && ORDERED.matches(lines[i].trimStart())) {
                        val m = ORDERED.matchEntire(lines[i].trimStart())!!
                        items += parseRuns(m.groupValues[1])
                        i++
                    }
                    blocks += FeishuBlock.Ordered(items)
                    continue
                }
                else -> {
                    // 段落:合并连续非空非特殊行
                    val para = StringBuilder(raw)
                    i++
                    while (i < lines.size && isParagraphContinuation(lines[i])) {
                        para.append(' ').append(lines[i])
                        i++
                    }
                    blocks += FeishuBlock.Paragraph(parseRuns(para.toString()))
                    continue
                }
            }
            i++
        }
        return blocks
    }

    private fun parseCodeBlock(lines: List<String>, start: Int): Pair<FeishuBlock, Int> {
        val openLine = lines[start].trimStart()
        // ```lang
        val language = openLine.removePrefix("```").trim()
        val sb = StringBuilder()
        var i = start + 1
        // fix-2026-06-26-review-r3 HIGH H10:code-fence close 必须行首无空白。
        // CommonMark 规范:缩进的 ``` (4 空格) 是普通文本,不是 fence close。
        // 之前 `lines[i].trimStart().startsWith("```")` 会把 `    ```rust` 误判为 close,
        // 导致代码块在缩进 fence 行提前终止。
        while (i < lines.size && !isFenceClose(lines[i])) {
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(lines[i])
            i++
        }
        // 跳过闭合 ```
        if (i < lines.size) i++
        // mermaid 等不支持类型 → 转 Unsupported
        if (language.lowercase() == "mermaid") {
            return FeishuBlock.Unsupported("```$language\n${sb.toString().trimEnd()}\n```") to i
        }
        return FeishuBlock.CodeBlock(language = language, text = sb.toString()) to i
    }

    private fun parseTable(lines: List<String>, start: Int): Pair<FeishuBlock, Int> {
        // 收集连续 | 行(含表头 + separator + 数据行)
        val rows = mutableListOf<List<String>>()
        var i = start
        while (i < lines.size && lines[i].contains("|")) {
            val cells = lines[i].split("|").map { it.trim() }.filter { it.isNotEmpty() }
            if (cells.isNotEmpty()) rows += cells
            i++
        }
        // 去掉纯 `---` separator 行
        val dataRows = rows.filterNot { it.all { c -> c.matches(Regex("^-+$")) } }
        return FeishuBlock.Table(dataRows) to i
    }

    /**
     * 段落边界判定:对齐主 when 分支的判定逻辑，允许首字符缩进。
     * 规则:非空 + 不匹配 heading/quote/divider/bullet/ordered/code 起始。
     */
    private fun isParagraphContinuation(line: String): Boolean {
        if (line.isBlank()) return false
        val trimmed = line.trimStart()
        // fix-2026-06-26-review-r3 HIGH H10:与 fence 检测保持一致 — 只在行首无空白时算 fence
        if (isFenceOpen(line)) return false
        if (HEADING.matches(trimmed)) return false
        if (DIVIDER.matches(trimmed.trim())) return false
        if (BULLET.matches(trimmed)) return false
        if (ORDERED.matches(trimmed)) return false
        if (trimmed.startsWith(">")) return false
        return true
    }

    /**
     * fix-2026-06-26-review-r3 HIGH H10:code-fence 判定。
     * CommonMark 规范:open/close fence 行首最多 3 空格缩进;4+ 空格是普通文本。
     * 此处要求行首无空白，与 spec "严格 fence" 一致 — 避免误把缩进段落当作 fence。
     */
    private fun isFenceOpen(line: String): Boolean =
        !line.isBlank() && line.startsWith("```") && !line.startsWith("    ```")

    private fun isFenceClose(line: String): Boolean = isFenceOpen(line)

    private fun parseImage(line: String): FeishuBlock {
        val match = Regex("""!\[([^\]]*)\]\(([^)]+)\)""").find(line)
        val path = match?.groupValues?.getOrNull(2) ?: line
        // v1 spec "Image degrades to text placeholder":占位形式 `[图片：path]`
        return FeishuBlock.Image(placeholder = "图片：$path")
    }

    /**
     * 行内解析:`**bold**` / `*italic*` / `` `code` `` / `[text](url)`。
     *
     * 优先级:链接 > code(`) > bold(**) > italic(*)。
     * 实现:按 token 顺序扫，识别后切片，剩余字符串继续。
     */
    internal fun parseRuns(text: String): List<Run> {
        if (text.isEmpty()) return listOf(Run(text = ""))
        val runs = mutableListOf<Run>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            // 1. 链接
            val linkMatch = Regex("""\[([^\]]+)\]\(([^)]+)\)""").find(remaining)
            // 2. inline code
            val codeMatch = Regex("""`([^`]+)`""").find(remaining)
            // 3. bold ** —— fix-2026-06-30-full-review-r1 MEDIUM M7:非贪婪 + 允许内嵌 *,
            //    支持 `**bold *italic* bold**` 嵌套
            val boldMatch = Regex("""\*\*(.+?)\*\*""").find(remaining)
            // 4. italic * —— 同 M7
            val italicMatch = Regex("""\*(.+?)\*""").find(remaining)

            val first = listOfNotNull(
                linkMatch?.let { Triple(it.range.first, it.range.last + 1, "link") },
                codeMatch?.let { Triple(it.range.first, it.range.last + 1, "code") },
                boldMatch?.let { Triple(it.range.first, it.range.last + 1, "bold") },
                italicMatch?.let { Triple(it.range.first, it.range.last + 1, "italic") }
            ).minByOrNull { it.first }

            if (first == null) {
                runs += Run(text = remaining)
                break
            }
            val (start, end, kind) = first
            if (start > 0) runs += Run(text = remaining.substring(0, start))
            val inner = remaining.substring(start, end)
            when (kind) {
                "link" -> {
                    val m = Regex("""\[([^\]]+)\]\(([^)]+)\)""").matchEntire(inner)!!
                    runs += Run(text = m.groupValues[1], linkUrl = m.groupValues[2])
                }
                "code" -> runs += Run(text = inner.removeSurrounding("`"), code = true)
                "bold" -> runs += Run(text = inner.removeSurrounding("**"), bold = true)
                "italic" -> runs += Run(text = inner.removeSurrounding("*"), italic = true)
            }
            remaining = remaining.substring(end)
        }
        return runs
    }

    companion object {
        private val HEADING = Regex("""^(#{1,6})\s+(.+)$""")
        private val DIVIDER = Regex("""^(-{3,}|\*{3,}|_{3,})$""")
        private val BULLET = Regex("""^[-*]\s+(.+)$""")
        private val ORDERED = Regex("""^\d+\.\s+(.+)$""")
        private val TABLE_SEPARATOR = Regex("""^\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$""")
    }
}
