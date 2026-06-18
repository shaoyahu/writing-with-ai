package com.yy.writingwithai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * writing-with-ai · 占位单测。
 *
 * 验证 JUnit5 Jupiter 引擎 + `useJUnitPlatform()` 跑通;
 * 跑 `./gradlew :app:testDebugUnitTest` 应看到本测试 SUCCESSFUL。
 *
 * 后续 change (`quick-note-feature` / `ai-abstraction-layer` 等)在此包按 feature 分目录加测试。
 */
class PlaceholderTest {
    @Test
    fun `2 + 2 equals 4`() {
        assertEquals(4, 2 + 2)
    }
}
