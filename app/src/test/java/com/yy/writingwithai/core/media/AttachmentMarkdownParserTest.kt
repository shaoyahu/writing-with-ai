package com.yy.writingwithai.core.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AttachmentMarkdownParserTest {

    @Test
    fun `parse returns empty list for empty content`() {
        assertEquals(emptyList<MarkdownSegment>(), AttachmentMarkdownParser.parse(""))
    }

    @Test
    fun `parse wraps plain text in single Text segment`() {
        val result = AttachmentMarkdownParser.parse("普通笔记,没有任何图片")
        assertEquals(listOf(MarkdownSegment.Text("普通笔记,没有任何图片")), result)
    }

    @Test
    fun `parse extracts single attachment image with leading text`() {
        val result = AttachmentMarkdownParser.parse("看这张 ![](attachment://abc123)")
        assertEquals(
            listOf(
                MarkdownSegment.Text("看这张 "),
                MarkdownSegment.AttachmentImage("abc123")
            ),
            result
        )
    }

    @Test
    fun `parse handles mixed text and multiple attachment images`() {
        val result = AttachmentMarkdownParser.parse("before ![](attachment://xyz) middle ![](attachment://foo) after")
        assertEquals(
            listOf(
                MarkdownSegment.Text("before "),
                MarkdownSegment.AttachmentImage("xyz"),
                MarkdownSegment.Text(" middle "),
                MarkdownSegment.AttachmentImage("foo"),
                MarkdownSegment.Text(" after")
            ),
            result
        )
    }

    @Test
    fun `parse preserves content when scheme is not attachment`() {
        val raw = "外链 ![](https://example.com/foo.png)"
        val result = AttachmentMarkdownParser.parse(raw)
        assertEquals(1, result.size)
        val first = result.first()
        assertTrue(first is MarkdownSegment.Text, "expected Text, got $first")
        assertEquals(raw, (first as MarkdownSegment.Text).raw)
    }

    @Test
    fun `parse preserves content when attachment id violates SAFE_ID regex`() {
        val raw = "![恶意](attachment://../../etc/passwd)"
        val result = AttachmentMarkdownParser.parse(raw)
        assertEquals(1, result.size)
        val first = result.first()
        assertTrue(first is MarkdownSegment.Text, "expected Text, got $first")
        assertEquals(raw, (first as MarkdownSegment.Text).raw)
    }

    @Test
    fun `parse drops empty text segments around lone attachment`() {
        val result = AttachmentMarkdownParser.parse("![](attachment://solo)")
        assertEquals(
            listOf(MarkdownSegment.AttachmentImage("solo")),
            result
        )
    }
}
