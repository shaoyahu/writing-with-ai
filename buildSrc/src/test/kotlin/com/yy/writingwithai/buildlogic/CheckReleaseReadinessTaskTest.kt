// release-preflight-automation:JVM 单测，覆盖 parseGrepOutput。
// 4 项 preflight 检查的 Gradle Task 自身(doLast)不便单测(依赖 Gradle runtime),
// 关键逻辑 grep 解析抽出后，4 个 case 足以锁住行为。
package com.yy.writingwithai.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CheckReleaseReadinessTaskTest {

    @Test
    fun `parseGrepOutput of empty string returns empty list`() {
        val result = parseGrepOutput("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseGrepOutput of multiline grep output returns 2 records`() {
        val text = """
            file1.kt:10: match1
            file2.kt:20: match2
        """.trimIndent()
        val result = parseGrepOutput(text)
        assertEquals(2, result.size)
        assertEquals("file1.kt", result[0].file)
        assertEquals(10, result[0].line)
        assertEquals("match1", result[0].message)
        assertEquals("file2.kt", result[1].file)
        assertEquals(20, result[1].line)
        assertEquals("match2", result[1].message)
    }

    @Test
    fun `parseGrepOutput ignores non-matching lines`() {
        val text = """
            file1.kt:5: hit
            some random warning text
            file2.kt:7: another hit
        """.trimIndent()
        val result = parseGrepOutput(text)
        assertEquals(2, result.size)
        assertEquals("file1.kt", result[0].file)
        assertEquals("file2.kt", result[1].file)
    }

    @Test
    fun `parseGrepOutput of blank-only string returns empty list`() {
        val result = parseGrepOutput("   \n  \n")
        assertTrue(result.isEmpty())
    }
}
