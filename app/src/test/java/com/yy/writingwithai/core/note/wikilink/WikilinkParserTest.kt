package com.yy.writingwithai.core.note.wikilink

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WikilinkParserTest {
    @Test fun `parses single`() = assertEquals(
        listOf(WikilinkMatch(target = "A", alias = null)),
        WikilinkParser.parse("see [[A]]")
    )

    @Test fun `parses multiple`() = assertEquals(
        listOf(
            WikilinkMatch(target = "A", alias = null),
            WikilinkMatch(target = "B", alias = null)
        ),
        WikilinkParser.parse("see [[A]] and [[B]]")
    )

    @Test fun `incomplete bracket`() = assertTrue(WikilinkParser.parse("see [[A").isEmpty())

    @Test fun `single bracket`() = assertTrue(WikilinkParser.parse("[A]").isEmpty())

    @Test fun `multiline skipped`() = assertTrue(WikilinkParser.parse("[[a\nb]]").isEmpty())

    @Test fun `trims whitespace`() = assertEquals(
        listOf(WikilinkMatch(target = "A", alias = null)),
        WikilinkParser.parse("[[  A  ]]")
    )

    @Test fun `empty content`() = assertTrue(WikilinkParser.parse("").isEmpty())

    // fix-2026-06-25-review-r1 H2:[[Alias|Target]] 形态:target 解析为段尾,alias 取段中。
    @Test fun `parses alias target`() = assertEquals(
        listOf(WikilinkMatch(target = "NoteA", alias = "显示别名")),
        WikilinkParser.parse("see [[显示别名|NoteA]]")
    )

    @Test fun `alias absent on plain link`() {
        val first = WikilinkParser.parse("[[A]]").single()
        assertNull(first.alias)
    }

    @Test fun `alias with trailing pipe-target omitted`() = assertTrue(
        WikilinkParser.parse("[[A|]]").isEmpty()
    )
}
