package com.yy.writingwithai.core.ai.stream

import app.cash.turbine.test
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
        SseParser.parse(source).test {
            // 第 1 个 data
            val e1 = awaitItem()
            assertTrue(e1 is SseEvent.Data)
            assertEquals(
                "{\"type\":\"content_block_delta\",\"delta\":{\"text\":\"hello\"}}",
                (e1 as SseEvent.Data).content
            )
            // 第 2 个 data
            val e2 = awaitItem()
            assertTrue(e2 is SseEvent.Data)
            // [DONE] 终止
            val e3 = awaitItem()
            assertTrue(e3 is SseEvent.Done)
            awaitComplete()
        }
    }

    @Test
    fun `empty stream returns Done`() = runTest {
        val source = Buffer().writeUtf8("data: [DONE]\n\n")
        SseParser.parse(source).test {
            assertTrue(awaitItem() is SseEvent.Done)
            awaitComplete()
        }
    }

    @Test
    fun `multi-line data aggregated`() = runTest {
        val source = Buffer().writeUtf8(
            "data: line1\n" +
                "data: line2\n\n" +
                "data: [DONE]\n\n"
        )
        SseParser.parse(source).test {
            val e1 = awaitItem()
            assertTrue(e1 is SseEvent.Data)
            assertEquals("line1\nline2", (e1 as SseEvent.Data).content)
            val e2 = awaitItem()
            assertTrue(e2 is SseEvent.Done)
            awaitComplete()
        }
    }

    // L6/L7 修:以下 4 个新增 case — heartbeat / CRLF / BOM / comment。

    @Test
    fun `keep-alive heartbeat line is ignored`() = runTest {
        // RFC 8895:`:` 开头是 comment,典型用法是 `:keep-alive` 作为心跳续约。
        val source = Buffer().writeUtf8(
            ":keep-alive\n" +
                "data: {\"text\":\"hi\"}\n\n" +
                ":ping\n\n" +
                "data: [DONE]\n\n"
        )
        SseParser.parse(source).test {
            val e1 = awaitItem()
            assertTrue(e1 is SseEvent.Data)
            assertEquals("{\"text\":\"hi\"}", (e1 as SseEvent.Data).content)
            // 后续 :ping 与 [DONE]:只 emit Done
            val e2 = awaitItem()
            assertTrue(e2 is SseEvent.Done)
            awaitComplete()
        }
    }

    @Test
    fun `CRLF line endings are handled`() = runTest {
        // 真实服务端偶发用 \r\n;Okio readUtf8Line 默认 strip 末尾 \r。
        val source = Buffer().writeUtf8(
            "data: {\"text\":\"a\"}\r\n\r\n" +
                "data: {\"text\":\"b\"}\r\n\r\n" +
                "data: [DONE]\r\n\r\n"
        )
        SseParser.parse(source).test {
            val e1 = awaitItem()
            assertTrue(e1 is SseEvent.Data)
            assertEquals("{\"text\":\"a\"}", (e1 as SseEvent.Data).content)
            val e2 = awaitItem()
            assertTrue(e2 is SseEvent.Data)
            assertEquals("{\"text\":\"b\"}", (e2 as SseEvent.Data).content)
            val e3 = awaitItem()
            assertTrue(e3 is SseEvent.Done)
            awaitComplete()
        }
    }

    @Test
    fun `UTF-8 BOM at stream start is stripped`() = runTest {
        // L6 修:某些 provider(.NET / Windows default)首行带 BOM;若不剥,首条
        // data 事件因前缀 ﻿ 失败 startsWith("data:")。
        val source = Buffer().writeUtf8("﻿data: {\"text\":\"first\"}\n\ndata: [DONE]\n\n")
        SseParser.parse(source).test {
            val e1 = awaitItem()
            assertTrue(e1 is SseEvent.Data)
            assertEquals("{\"text\":\"first\"}", (e1 as SseEvent.Data).content)
            val e2 = awaitItem()
            assertTrue(e2 is SseEvent.Done)
            awaitComplete()
        }
    }

    @Test
    fun `comment lines starting with colon are ignored`() = runTest {
        // 注释行(spec / event / id 之外任意自定义 `:xxx`)直接 skip,不影响 data 聚合。
        val source = Buffer().writeUtf8(
            ": this is a comment\n" +
                "data: part1\n" +
                ":another comment\n" +
                "data: part2\n\n" +
                "data: [DONE]\n\n"
        )
        SseParser.parse(source).test {
            val e1 = awaitItem()
            assertTrue(e1 is SseEvent.Data)
            assertEquals("part1\npart2", (e1 as SseEvent.Data).content)
            val e2 = awaitItem()
            assertTrue(e2 is SseEvent.Done)
            awaitComplete()
        }
    }
}
