package com.yy.writingwithai.core.feishu.converter

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * markdown-docx-converter · converter 单测。
 *
 * spec: openspec/changes/markdown-docx-converter/spec.md
 * tasks: openspec/changes/markdown-docx-converter/tasks.md §4.2 / §4.3
 */
class MarkdownDocxConverterTest {

    private val md2docx = MarkdownToDocxConverterImpl()
    private val docx2md = DocxToMarkdownConverterImpl()

    @Test
    fun `heading converts to Heading block`() = runTest {
        val blocks = md2docx.convert("# 标题")
        assertEquals(1, blocks.size)
        val h = blocks[0] as FeishuBlock.Heading
        assertEquals(1, h.level)
        assertEquals("标题", h.runs[0].text)
    }

    @Test
    fun `bullet list converts to Bullet block`() = runTest {
        val blocks = md2docx.convert("- 一\n- 二")
        assertEquals(1, blocks.size)
        val b = blocks[0] as FeishuBlock.Bullet
        assertEquals(2, b.items.size)
        assertEquals("一", b.items[0][0].text)
    }

    @Test
    fun `image degrades to Image block with placeholder`() = runTest {
        val blocks = md2docx.convert("![alt](assets/foo.png)")
        assertEquals(1, blocks.size)
        val img = blocks[0] as FeishuBlock.Image
        assertEquals("图片：assets/foo.png", img.placeholder)
    }

    @Test
    fun `round-trip preserves zh_basic sample`() = runTest {
        val sample = javaClass.classLoader!!.getResourceAsStream("converter/samples/zh_basic.md")!!
            .bufferedReader().readText()
        val blocks = md2docx.convert(sample)
        val reversed = docx2md.convert(blocks)
        // 关键元素断言(不要求字符 1:1,因为空白规范化由 normalize() 负责)
        assertTrue(reversed.contains("# 一级标题"))
        assertTrue(reversed.contains("**粗体**"))
        assertTrue(reversed.contains("*斜体*"))
        assertTrue(reversed.contains("## 二级标题"))
        assertTrue(reversed.contains("- 列表项一"))
        assertTrue(reversed.contains("1. 有序一"))
        assertTrue(reversed.contains("> 引用内容"))
        assertTrue(reversed.contains("---"))
        assertTrue(reversed.contains("```"))
        assertTrue(reversed.contains("[外链文字](https://example.com)"))
    }

    @Test
    fun `unsupported html degrades to paragraph raw`() = runTest {
        val blocks = md2docx.convert("<div>raw html</div>")
        // 无 matched prefix → 走 paragraph 默认分支,保留 raw 文本
        assertEquals(1, blocks.size)
        val p = blocks[0] as FeishuBlock.Paragraph
        assertTrue(p.runs[0].text.contains("<div>raw html</div>"))
    }

    @Test
    fun `mermaid code block degrades to Unsupported`() = runTest {
        val md = "```mermaid\ngraph TD; A-->B\n```"
        val blocks = md2docx.convert(md)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is FeishuBlock.Unsupported)
    }

    @Test
    fun `backward converter handles all supported types`() = runTest {
        val blocks = listOf(
            FeishuBlock.Heading(2, listOf(Run("标题"))),
            FeishuBlock.Paragraph(listOf(Run("段落"))),
            FeishuBlock.Bullet(listOf(listOf(Run("a")), listOf(Run("b")))),
            FeishuBlock.Ordered(listOf(listOf(Run("1")), listOf(Run("2")))),
            FeishuBlock.CodeBlock("kotlin", "val x = 1"),
            FeishuBlock.Quote(listOf(Run("引文"))),
            FeishuBlock.Divider,
            FeishuBlock.Image("图片：path")
        )
        val md = docx2md.convert(blocks)
        assertTrue(md.contains("## 标题"))
        assertTrue(md.contains("段落"))
        assertTrue(md.contains("- a"))
        assertTrue(md.contains("1. 1"))
        assertTrue(md.contains("```kotlin"))
        assertTrue(md.contains("> 引文"))
        assertTrue(md.contains("---"))
        assertTrue(md.contains("[图片：path]"))
    }

    @Test
    fun `inline bold italic code link parsed correctly`() = runTest {
        val runs = md2docx.parseRuns("这是 **粗** 和 *斜* 和 `码` 和 [链](https://x.com)")
        assertTrue(runs.any { it.text == "粗" && it.bold })
        assertTrue(runs.any { it.text == "斜" && it.italic })
        assertTrue(runs.any { it.text == "码" && it.code })
        assertTrue(runs.any { it.text == "链" && it.linkUrl == "https://x.com" })
    }
}
