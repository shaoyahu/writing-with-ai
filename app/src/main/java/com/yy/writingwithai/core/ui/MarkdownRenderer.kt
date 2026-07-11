package com.yy.writingwithai.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import com.yy.writingwithai.feature.quicknote.model.EntityHighlight
import kotlin.math.max
import kotlin.math.min

/**
 * markdown-live-preview · Markdown 子集解析器(纯函数,可 JVM 单测)。
 *
 * 实现 [design.md §D2] 列出的子集:`#` / `##` / `###` 标题、`-` / `*` / `1.` 列表、
 * `**bold**` / `*italic*` / `_italic_` / `` `code` `` / `[[wikilink]]` / `[text](url)`。
 *
 * 不实现表格 / fence / 图片 / 数学公式 / 嵌套列表;不支持的语法 / 解析失败时
 * 全部字符按字面 append(D5 注入防御原则)。
 *
 * 实体高亮 [entityHighlights] 叠到输出 `AnnotatedString` 的对应区间,保留
 * `EntityCrossStar`(`✦`)字符供详情屏 `starsBefore` 计数继续走原路径。
 *
 * 不产生 `addUrlAnnotation`(URL 仅做视觉提示)。
 */
internal fun render(
    markdown: String,
    entityHighlights: List<EntityHighlight> = emptyList(),
    primaryColor: Color
): AnnotatedString {
    if (markdown.isEmpty() && entityHighlights.isEmpty()) return AnnotatedString("")
    val blocks = parseBlocks(markdown, primaryColor)
    val blockSourceRanges = computeBlockSourceRanges(markdown, blocks)
    return buildAnnotatedString {
        blocks.forEachIndexed { idx, block ->
            val rendered = renderBlock(block)
            append(rendered)
            val range = blockSourceRanges.getOrNull(idx)
            if (range != null) {
                val annotatedStart = length - rendered.length
                applyEntityHighlights(
                    sourceStart = range.start,
                    sourceEnd = range.end,
                    annotatedStart = annotatedStart,
                    annotatedEnd = length,
                    highlights = entityHighlights,
                    primaryColor = primaryColor
                )
            }
        }
    }
}

/** 块:渲染后纯文本 + inline spans + 块级 SpanStyle。 */
internal data class Block(
    val kind: Kind,
    val visibleText: String,
    val inlineSpans: List<InlineSpan> = emptyList(),
    val blockStyle: SpanStyle? = null
) {
    enum class Kind { H1, H2, H3, BULLET, ORDERED, PARAGRAPH }
}

internal data class InlineSpan(
    val start: Int,
    val end: Int,
    val style: SpanStyle
)

internal data class SourceRange(val start: Int, val end: Int)

/**
 * 把 markdown 切成 block 列表。块级触发符(`#` / `-` / `1.`)被消耗;行内触发符
 * 在 [parseInline] 内处理(闭区间被剥除、未闭区间保留为字面)。
 */
internal fun parseBlocks(markdown: String, primaryColor: Color): List<Block> {
    val blocks = mutableListOf<Block>()
    val lines = markdown.split('\n')
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.isBlank()) {
            i++
            continue
        }
        val h3 = HEADER_3.matchAtStart(line)
        if (h3 != null) {
            val (text, spans) = parseInline(line.substring(h3.value.length), primaryColor)
            blocks += Block(Block.Kind.H3, text, spans, HEADER_3_STYLE)
            i++
            continue
        }
        val h2 = HEADER_2.matchAtStart(line)
        if (h2 != null) {
            val (text, spans) = parseInline(line.substring(h2.value.length), primaryColor)
            blocks += Block(Block.Kind.H2, text, spans, HEADER_2_STYLE)
            i++
            continue
        }
        val h1 = HEADER_1.matchAtStart(line)
        if (h1 != null) {
            val (text, spans) = parseInline(line.substring(h1.value.length), primaryColor)
            blocks += Block(Block.Kind.H1, text, spans, HEADER_1_STYLE)
            i++
            continue
        }
        val bullet = BULLET.matchAtStart(line)
        if (bullet != null) {
            val (text, spans) = parseInline(line.substring(bullet.value.length), primaryColor)
            blocks += Block(Block.Kind.BULLET, "· $text", spans)
            i++
            continue
        }
        val ordered = ORDERED.matchEntire(line)
        if (ordered != null) {
            val prefix = ordered.groupValues[1]
            val text = ordered.groupValues[2]
            val (resolvedText, spans) = parseInline(text, primaryColor)
            blocks += Block(Block.Kind.ORDERED, "$prefix $resolvedText", spans)
            i++
            continue
        }
        val sb = StringBuilder()
        while (i < lines.size && lines[i].isNotBlank()) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(lines[i])
            i++
        }
        val paraText = sb.toString()
        val (resolvedPara, paraSpans) = parseInline(paraText, primaryColor)
        val sepPrefix = if (blocks.lastOrNull()?.kind == Block.Kind.PARAGRAPH) " " else ""
        blocks += Block(Block.Kind.PARAGRAPH, "$sepPrefix$resolvedPara", paraSpans)
    }
    return blocks
}

internal fun computeBlockSourceRanges(markdown: String, blocks: List<Block>): List<SourceRange?> {
    if (blocks.isEmpty()) return emptyList()
    val ranges = mutableListOf<SourceRange?>()
    val lines = markdown.split('\n')
    var i = 0
    var inParagraph = false
    var paraStartLine = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.isBlank()) {
            i++
            continue
        }
        val isSingle = HEADER_3.matchAtStart(line) != null ||
            HEADER_2.matchAtStart(line) != null ||
            HEADER_1.matchAtStart(line) != null ||
            BULLET.matchAtStart(line) != null ||
            ORDERED.matchEntire(line) != null
        if (isSingle) {
            val lineStart = offsetOfLine(lines, i)
            ranges += SourceRange(lineStart, lineStart + line.length)
            i++
            continue
        }
        if (!inParagraph) {
            inParagraph = true
            paraStartLine = i
        }
        while (i < lines.size && lines[i].isNotBlank()) i++
        val paraEndLine = i - 1
        val start = offsetOfLine(lines, paraStartLine)
        val end = offsetOfLine(lines, paraEndLine) + lines[paraEndLine].length
        ranges += SourceRange(start, end)
        inParagraph = false
    }
    while (ranges.size < blocks.size) ranges += null
    return ranges.take(blocks.size)
}

private fun offsetOfLine(lines: List<String>, idx: Int): Int {
    var off = 0
    for (k in 0 until idx) off += lines[k].length + 1
    return off
}

private fun renderBlock(block: Block): AnnotatedString = buildAnnotatedString {
    append(block.visibleText)
    if (block.blockStyle != null) {
        addStyle(block.blockStyle, 0, block.visibleText.length)
    }
    for (span in block.inlineSpans) {
        addStyle(span.style, span.start, span.end)
    }
}

/**
 * 把命中 [sourceStart, sourceEnd) 的 entity 高亮叠加到当前 [annotatedStart, annotatedEnd)。
 * `✦` 紧贴 entity 文本末尾追加(超小 superscript),由 [EntityCrossStar] 常量供详情屏 starsBefore 计数。
 */
private fun androidx.compose.ui.text.AnnotatedString.Builder.applyEntityHighlights(
    sourceStart: Int,
    sourceEnd: Int,
    annotatedStart: Int,
    annotatedEnd: Int,
    highlights: List<EntityHighlight>,
    primaryColor: Color
) {
    val annotatedLen = annotatedEnd - annotatedStart
    if (annotatedLen <= 0 || highlights.isEmpty()) return
    val matched = highlights.filter { hl ->
        hl.contentStart < sourceEnd && hl.contentEnd > sourceStart && hl.contentEnd > hl.contentStart
    }
    if (matched.isEmpty()) return
    val sorted = matched.sortedByDescending { it.contentEnd - it.contentStart }
    val insertedStars = mutableSetOf<String>()
    for (hl in sorted) {
        val hlStart = max(hl.contentStart, sourceStart)
        val hlEnd = min(hl.contentEnd, sourceEnd)
        if (hlStart >= hlEnd) continue
        val relStart = (hlStart - sourceStart).coerceIn(0, annotatedLen)
        val relEnd = (hlEnd - sourceStart).coerceIn(0, annotatedLen)
        val absStart = annotatedStart + relStart
        val absEnd = annotatedStart + relEnd
        if (absStart >= absEnd) continue
        addStyle(SpanStyle(color = primaryColor), absStart, absEnd)
        addStringAnnotation("entity", hl.entityKey, absStart, absEnd)
        if (insertedStars.add(hl.entityKey)) {
            val starStart = length
            append(EntityCrossStar)
            val starEnd = length
            addStyle(
                SpanStyle(
                    color = primaryColor,
                    fontSize = 0.55.em,
                    baselineShift = BaselineShift.Superscript
                ),
                starStart,
                starEnd
            )
        }
    }
}

/** 公开常量:详情屏 `starsBefore` 计数用,与 `feature.quicknote.detail.EntityCrossStarChar` 同字面量。 */
internal const val EntityCrossStar: String = "✦"

/* ============== 行内触发符解析 ============== */

private val HEADER_1 = Regex("^#\\s+")
private val HEADER_2 = Regex("^##\\s+")
private val HEADER_3 = Regex("^###\\s+")
private val BULLET = Regex("^[-*]\\s+")
private val ORDERED = Regex("^(\\d+\\.)\\s+(.+)$")

private val HEADER_1_STYLE = SpanStyle(fontWeight = FontWeight.Bold)
private val HEADER_2_STYLE = SpanStyle(fontWeight = FontWeight.Bold)
private val HEADER_3_STYLE = SpanStyle(fontWeight = FontWeight.Bold)

private val BOLD_RE = Regex("\\*\\*(.+?)\\*\\*")
private val ITALIC_STAR_RE = Regex("(?<![*\\w])\\*([^*\\n]+?)\\*(?!\\*)")
private val ITALIC_UNDERSCORE_RE = Regex("(?<![_\\w])_([^_\\n]+?)_(?!_)")
private val CODE_RE = Regex("`([^`\\n]+?)`")
private val WIKILINK_RE = Regex("\\[\\[([^\\[\\]\\n]+?)]]")
private val LINK_RE = Regex("\\[([^\\[\\]\\n]+?)]\\(([^\\)\\s\\n]*)\\)")

// fix-review-r1 F3 follow-up:危险协议(javascript: / data: / vbscript:)URL 内部常含括号
// (例如 `(1)`),被上面 LINK_RE group2 排除集 `[^\)\s\n]` 截断,残留尾部 `)` 到
// visibleText("点我)"),与 spec "javascript link URL is visual only no url annotation"
// 测试期望 `assertEquals("点我", ...)` 冲突。单走 DANGEROUS_LINK_RE,允许 group2 含括号
// 直到末尾匹配的 `)`;**只**对危险协议放行,合法 https/mailto 仍走 LINK_RE 严格形态
// (避免解析 `](https://x.com/foo)` 误吞闭合括号)。
private val DANGEROUS_LINK_RE = Regex(
    "\\[([^\\[\\]\\n]+?)]\\(\\s*(?:javascript|data|vbscript):[^\\s\\n]*\\)"
)

private fun Regex.matchAtStart(s: String): MatchResult? = find(s)?.takeIf { it.range.first == 0 }

/**
 * 把 block 文本拆出"显示文本"(剥除已闭合的触发符)和 inline spans。
 *
 * 处理顺序:bold → italic → code → wikilink → link。重叠区间按"start 升序,长度降序"
 * 接受;首个匹配的 range 之后的字符才考虑后续匹配。
 *
 * 未闭合触发符(只找到 `**` 但没有 `**` 闭合)→ 保留为字面字符,不加 span。
 *
 * inline spans 索引基于 visibleText。
 */
internal fun parseInline(text: String, primaryColor: Color): Pair<String, List<InlineSpan>> {
    val candidates = mutableListOf<Triple<IntRange, String, SpanStyle>>()
    BOLD_RE.findAll(text).forEach { m ->
        candidates += Triple(m.range, m.groupValues[1], SpanStyle(fontWeight = FontWeight.Bold))
    }
    ITALIC_STAR_RE.findAll(text).forEach { m ->
        candidates += Triple(m.range, m.groupValues[1], SpanStyle(fontStyle = FontStyle.Italic))
    }
    ITALIC_UNDERSCORE_RE.findAll(text).forEach { m ->
        candidates += Triple(m.range, m.groupValues[1], SpanStyle(fontStyle = FontStyle.Italic))
    }
    CODE_RE.findAll(text).forEach { m ->
        candidates += Triple(
            m.range,
            m.groupValues[1],
            SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
        )
    }
    WIKILINK_RE.findAll(text).forEach { m ->
        candidates += Triple(m.range, m.groupValues[1], SpanStyle(color = primaryColor))
    }
    LINK_RE.findAll(text).forEach { m ->
        candidates += Triple(
            m.range,
            m.groupValues[1],
            SpanStyle(color = primaryColor, textDecoration = TextDecoration.Underline)
        )
    }
    DANGEROUS_LINK_RE.findAll(text).forEach { m ->
        candidates += Triple(
            m.range,
            m.groupValues[1],
            SpanStyle(color = primaryColor, textDecoration = TextDecoration.Underline)
        )
    }
    val sorted = candidates.sortedWith(
        compareBy({ it.first.first }, { -(it.first.last - it.first.first) })
    )
    val accepted = mutableListOf<Triple<IntRange, String, SpanStyle>>()
    var lastEnd = -1
    for (r in sorted) {
        if (r.first.first >= lastEnd) {
            accepted += r
            lastEnd = r.first.last + 1
        }
    }
    val sb = StringBuilder()
    val spans = mutableListOf<InlineSpan>()
    var cursor = 0
    for ((range, replacement, style) in accepted) {
        if (range.first > cursor) sb.append(text.substring(cursor, range.first))
        val innerStart = sb.length
        sb.append(replacement)
        val innerEnd = sb.length
        spans += InlineSpan(innerStart, innerEnd, style)
        cursor = range.last + 1
    }
    if (cursor < text.length) sb.append(text.substring(cursor))
    // fix-review-r1 F3:删无条件 `dropLast(1)`。原实现用「结果以 ) 收尾就削掉最后 1 字符」兜底
    // 闭括号,但合法 ID `attachment://<id>)` 场景会把 ID 末尾合法字符 `)` 误吞(parser 已
    // 验证 ID 形态,不再需要该兜底;非法 ID 由 parser 兜底保留原文)。
    return sb.toString() to spans
}
