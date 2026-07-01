package com.yy.writingwithai.core.ai.fake

import com.yy.writingwithai.core.ai.api.AiStreamEvent
import java.util.concurrent.atomic.AtomicReference

/**
 * FakeProvider 的运行时配置(单测 / UI 验收用，M5 真联调时不改此处)。
 */
data class FakeConfig(
    val text: String = "Fake AI response for testing",
    val delayMs: Long = 0L,
    val errorAfterTokens: Int? = null,
    val tokenCounts: AiStreamEvent.Usage? = null
)

/**
 * 全局 hook，让单测 / UI 验收在运行时改 FakeProvider 行为。
 *
 * M3 UI 验收用;M5 真联调时不再调用。
 *
 * fix-review-r3-medium M8:原版 `var config: FakeConfig` 非 volatile，多个测试 / 协程并发
 * 改写时一个线程的更新可能对另一个线程不可见;并发读取可能看到撕裂 / 旧值。改用
 * [AtomicReference] 替换，set / get 都是 atomic。
 */
object FakeConfigHolder {
    private val ref = AtomicReference(FakeConfig())

    val config: FakeConfig
        get() = ref.get()

    fun set(
        text: String,
        delayMs: Long = 0L,
        errorAfterTokens: Int? = null,
        tokenCounts: AiStreamEvent.Usage = AiStreamEvent.Usage(0, 0, 0)
    ) {
        ref.set(FakeConfig(text, delayMs, errorAfterTokens, tokenCounts))
    }

    fun reset() {
        ref.set(FakeConfig())
    }
}
