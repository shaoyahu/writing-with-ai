package com.yy.writingwithai.core.note.wikilink

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WikilinkParserTest {
    @Test fun `parses single`() = assertEquals(listOf("A"), WikilinkParser.parse("see [[A]]"))

    @Test fun `parses multiple`() = assertEquals(listOf("A", "B"), WikilinkParser.parse("see [[A]] and [[B]]"))

    @Test fun `incomplete bracket`() = assertTrue(WikilinkParser.parse("see [[A").isEmpty())

    @Test fun `single bracket`() = assertTrue(WikilinkParser.parse("[A]").isEmpty())

    @Test fun `multiline skipped`() = assertTrue(WikilinkParser.parse("[[a\nb]]").isEmpty())

    @Test fun `trims whitespace`() = assertEquals(listOf("A"), WikilinkParser.parse("[[  A  ]]"))

    @Test fun `empty content`() = assertTrue(WikilinkParser.parse("").isEmpty())
}
