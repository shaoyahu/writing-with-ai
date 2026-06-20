package com.yy.writingwithai.core.data.export

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * M4-3 · ZipHelper 单元测试(JUnit5,无 MockK,纯字节 round-trip)。
 *
 * 覆盖 spec §"测试覆盖导出 / 导入 / 去重" 场景:
 * - writeZip → readZip 一致
 * - 嵌套路径(`notes/n1.md`)正常
 */
class ZipHelperTest {
    private val zipHelper = ZipHelper()

    @Test
    fun writeZip_then_readZip_returns_same_entries() {
        val out = ByteArrayOutputStream()
        val entries =
            mapOf(
                "a.txt" to "hello".toByteArray(Charsets.UTF_8),
                "b.json" to "{}".toByteArray(Charsets.UTF_8)
            )
        zipHelper.writeZip(entries, out)

        val roundTrip = zipHelper.readZip(ByteArrayInputStream(out.toByteArray()))
        assertEquals(entries.keys, roundTrip.keys)
        assertEquals(entries["a.txt"]!!.toString(Charsets.UTF_8), roundTrip["a.txt"]!!.toString(Charsets.UTF_8))
        assertEquals(entries["b.json"]!!.toString(Charsets.UTF_8), roundTrip["b.json"]!!.toString(Charsets.UTF_8))
    }

    @Test
    fun readZip_handles_nested_paths() {
        val out = ByteArrayOutputStream()
        val entries =
            mapOf(
                "notes/n1.md" to "# title\n\nbody".toByteArray(Charsets.UTF_8),
                "notes/n2.md" to "# other\n\nmore".toByteArray(Charsets.UTF_8),
                "notes.json" to "[]".toByteArray(Charsets.UTF_8)
            )
        zipHelper.writeZip(entries, out)

        val roundTrip = zipHelper.readZip(ByteArrayInputStream(out.toByteArray()))
        assertEquals(3, roundTrip.size)
        assertTrue(roundTrip.containsKey("notes/n1.md"))
        assertTrue(roundTrip.containsKey("notes/n2.md"))
        assertEquals("body", roundTrip["notes/n1.md"]!!.toString(Charsets.UTF_8).substringAfter("\n\n"))
    }

    @Test
    fun writeZip_empty_entries_produces_valid_empty_zip() {
        val out = ByteArrayOutputStream()
        zipHelper.writeZip(emptyMap(), out)

        val roundTrip = zipHelper.readZip(ByteArrayInputStream(out.toByteArray()))
        assertTrue(roundTrip.isEmpty())
    }
}
