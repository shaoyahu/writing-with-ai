package com.yy.writingwithai.core.ai.fake

import com.yy.writingwithai.core.ai.api.AiStreamEvent

/**
 * FakeProvider 的运行时配置(单测 / UI 验收用,M5 真联调时不改此处)。
 *
 * 非线程安全,仅限开发 / 测试环境使用。
 */
data class FakeConfig(
    val text: String = "Fake AI response for testing",
    val delayMs: Long = 0L,
    val errorAfterTokens: Int? = null,
    val tokenCounts: AiStreamEvent.Usage? = null
)

/**
 * 全局 hook,让单测 / UI 验收在运行时改 FakeProvider 行为。
 *
 * M3 UI 验收用;M5 真联调时不再调用。
 */
object FakeConfigHolder {
    var config: FakeConfig = FakeConfig()

    fun set(
        text: String,
        delayMs: Long = 0L,
        errorAfterTokens: Int? = null,
        tokenCounts: AiStreamEvent.Usage = AiStreamEvent.Usage(0, 0, 0)
    ) {
        config = FakeConfig(text, delayMs, errorAfterTokens, tokenCounts)
    }

    fun reset() {
        config = FakeConfig()
    }
}
