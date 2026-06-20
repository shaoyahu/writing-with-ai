package com.yy.writingwithai.feature.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * M4-4 · SimpleMarkdown 解析单测(覆盖 # / ## / 段落 / `-` 列表 / `**bold**`)。
 */
class OnboardingSimpleMarkdownTest {
    @Test
    fun `parses h1 and h2 headings`() {
        val md =
            """
            # Title
            ## Sub
            """.trimIndent()
        val blocks = parseSimpleMarkdown(md)
        assertEquals(2, blocks.size)
        val h1 = blocks[0] as MarkdownBlock.Heading
        assertEquals(1, h1.level)
        assertEquals("Title", h1.text.text)
        val h2 = blocks[1] as MarkdownBlock.Heading
        assertEquals(2, h2.level)
        assertEquals("Sub", h2.text.text)
    }

    @Test
    fun `parses bullet list`() {
        val md =
            """
            - item 1
            - item 2
            """.trimIndent()
        val blocks = parseSimpleMarkdown(md)
        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it is MarkdownBlock.ListItem })
        assertEquals("item 1", (blocks[0] as MarkdownBlock.ListItem).text.text)
        assertEquals("item 2", (blocks[1] as MarkdownBlock.ListItem).text.text)
    }

    @Test
    fun `merges consecutive lines into one paragraph`() {
        val md =
            """
            First line.
            Second line.
            Third.
            """.trimIndent()
        val blocks = parseSimpleMarkdown(md)
        assertEquals(1, blocks.size)
        val p = blocks[0] as MarkdownBlock.Paragraph
        assertEquals("First line. Second line. Third.", p.text.text)
    }

    @Test
    fun `parses inline bold markers`() {
        val md = "This is **bold** text."
        val blocks = parseSimpleMarkdown(md)
        assertEquals(1, blocks.size)
        val p = blocks[0] as MarkdownBlock.Paragraph
        val spans = p.text.spanStyles.filter { it.item.fontWeight != null }
        assertTrue(spans.isNotEmpty(), "expected at least one bold span")
    }
}
