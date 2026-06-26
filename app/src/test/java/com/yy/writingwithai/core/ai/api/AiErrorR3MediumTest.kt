package com.yy.writingwithai.core.ai.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * fix-review-r3-medium · [AiError.summary] Markdown 注入防御回归。
 *
 * M2:`detail` / `message` 字段会被 AiHistoryEntity 落库,history 页直接 Markdown 渲染。
 * 攻击者可构造恶意 provider 响应(嵌入 `**[xxx]` `[click](http://evil)` 等),让 history
 * 渲染出伪造的链接 / 列表 / 代码块。修后 summary() 必须转义所有 Markdown 控制字符。
 */
class AiErrorR3MediumTest {

    @Test
    fun `summary escapes markdown control chars in detail`() {
        val raw = "boom *bold* `code` [link](http://x) | pipe ~strike"
        val err = AiError.Network(-1, raw)
        val s = err.summary()
        // 所有控制字符前都应被反斜杠转义
        assertTrue(s.contains("\\*"), "asterisk should be escaped: $s")
        assertTrue(s.contains("\\`"), "backtick should be escaped: $s")
        assertTrue(s.contains("\\["), "bracket should be escaped: $s")
        assertTrue(s.contains("\\|"), "pipe should be escaped: $s")
        assertTrue(s.contains("\\~"), "tilde should be escaped: $s")
        // 不应在 summary 里以"裸"形式出现
        assertFalse(s.contains("`code`"), "raw backticks should not appear: $s")
        assertFalse(s.contains("[link]"), "raw brackets should not appear: $s")
    }

    @Test
    fun `summary preserves plain text content`() {
        val err = AiError.Timeout("read timed out after 30s")
        val s = err.summary()
        assertTrue(s.contains("read timed out after 30s"))
    }

    @Test
    fun `summary on no-detail variants is a static label`() {
        // 静态 label(无 detail/message)的分支不应被破坏
        assertEquals("ProviderNotConfigured", AiError.ProviderNotConfigured.summary())
        assertEquals("Cancellation", AiError.Cancellation.summary())
        assertEquals("RateLimited(retryAfter=42s)", AiError.RateLimited(42).summary())
    }
}
