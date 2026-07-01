package com.yy.writingwithai.core.feishu.converter

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * fix-2026-06-25-review-r1 M10 · MarkdownToXmlConverter 单测。
 *
 * 重点覆盖 M10 review 修的"嵌套 marker 在 heading / quote / 段落里"。
 * 同时 regression 已有功能:heading / paragraph / bold / code / quote。
 *
 * spec: openspec/changes/feishu-doc-v2/spec.md(不直接对应，作为 converter 工具测试)。
 */
class MarkdownToXmlConverterTest {

    private val converter = MarkdownToXmlConverter()

    @Test
    fun `title is escaped and wrapped in title tag`() {
        val xml = converter.convert("body", title = "T & T < > \"x\"")
        assertTrue(xml.contains("<title>T &amp; T &lt; &gt; &quot;x&quot;</title>"), xml)
    }

    @Test
    fun `plain paragraph`() {
        val xml = converter.convert("hello world", title = "T")
        assertTrue(xml.contains("<p>hello world</p>"), xml)
    }

    @Test
    fun `h1 with nested bold marker is parsed recursively`() {
        // M10 修:之前 `# **bold heading**` 输出 `<h1>**bold heading**</h1>`(标记没解析)
        val xml = converter.convert("# **bold heading**", title = "T")
        assertTrue(xml.contains("<h1><b>bold heading</b></h1>"), "actual: $xml")
        assertFalse(xml.contains("**"), "marker should not leak: $xml")
    }

    @Test
    fun `h2 with inline code marker`() {
        val xml = converter.convert("## heading with `code`", title = "T")
        assertTrue(xml.contains("<h2>heading with <code>code</code></h2>"), "actual: $xml")
    }

    @Test
    fun `h3 with italic`() {
        val xml = converter.convert("### _italic_ heading", title = "T")
        assertTrue(xml.contains("<h3><em>italic</em> heading</h3>"), "actual: $xml")
    }

    @Test
    fun `quote block uses inline tokenizer too`() {
        val xml = converter.convert("> **quoted**", title = "T")
        assertTrue(
            xml.contains("<blockquote><p><b>quoted</b></p></blockquote>"),
            "actual: $xml"
        )
    }

    @Test
    fun `paragraph with bold + code wikilink`() {
        val xml = converter.convert("**a** and `b` and [[c]]", title = "T")
        assertTrue(xml.contains("<p>"), "actual: $xml")
        assertTrue(xml.contains("<b>a</b>"), "actual: $xml")
        assertTrue(xml.contains("<code>b</code>"), "actual: $xml")
        assertTrue(xml.contains("c"), "wikilink should expand to plain text: $xml")
    }

    @Test
    fun `code fence wraps multi-line in pre code`() {
        // fix-2026-06-26-review-r3 HIGH H11:code block 走 CDATA 包裹，避免
        // `&`/`<`/`>`/控制字符在 v2 API HTML 嵌入时产出 malformed XML。
        val xml = converter.convert("```\nval x = 1\nval y = 2\n```", title = "T")
        assertTrue(
            xml.contains("<pre><code><![CDATA[val x = 1\nval y = 2]]></code></pre>"),
            "actual: $xml"
        )
    }

    @Test
    fun `empty lines are skipped`() {
        val xml = converter.convert("\n\nhello\n\n", title = "T")
        // 不应出现 `<p>   </p>` 空段
        assertFalse(xml.contains("<p></p>"), "actual: $xml")
    }

    @Test
    fun `unpaired bold marker is treated as literal`() {
        // `**foo` 没有配对 → 当字面量输出
        val xml = converter.convert("a **foo bar", title = "T")
        assertTrue(xml.contains("**foo bar"), "unpaired should remain literal: $xml")
    }

    @Test
    fun `document envelope is well formed`() {
        val xml = converter.convert("body", title = "T")
        assertTrue(xml.startsWith("<document>"), "actual: $xml")
        assertTrue(xml.endsWith("</document>"), "actual: $xml")
    }
}
