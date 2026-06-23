package com.yy.writingwithai.core.ai.stream

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {
    @Test
    fun `normal SSE stream parsed correctly`() = runTest {
        val source = Buffer().writeUtf8(
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"text\":\"hello\"}}\n\n" +
                "data: {\"type\":\"content_block_delta\",\"delta\":{\"text\":\" world\"}}\n\n" +
                "data: [DONE]\n\n"
        )
        val events = SseParser.parse(source).toList()
        assertEquals(3, events.size)
        assertTrue(events[0] is SseEvent.Data)
        assertEquals(
            "{\"type\":\"content_block_delta\",\"delta\":{\"text\":\"hello\"}}",
            (events[0] as SseEvent.Data).content
        )
        assertTrue(events[1] is SseEvent.Data)
        assertTrue(events[2] is SseEvent.Done)
    }

    @Test
    fun `empty stream returns Done`() = runTest {
        val source = Buffer().writeUtf8("data: [DONE]\n\n")
        val events = SseParser.parse(source).toList()
        assertEquals(1, events.size)
        assertTrue(events[0] is SseEvent.Done)
    }

    @Test
    fun `multi-line data aggregated`() = runTest {
        val source = Buffer().writeUtf8(
            "data: line1\n" +
                "data: line2\n\n" +
                "data: [DONE]\n\n"
        )
        val events = SseParser.parse(source).toList()
        assertEquals(2, events.size)
        assertTrue(events[0] is SseEvent.Data)
        assertEquals("line1\nline2", (events[0] as SseEvent.Data).content)
    }
}
