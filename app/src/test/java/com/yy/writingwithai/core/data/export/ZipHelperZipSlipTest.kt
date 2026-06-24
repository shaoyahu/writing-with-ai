package com.yy.writingwithai.core.data.export

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ZipHelperZipSlipTest {

    private val zipHelper = ZipHelper()

    @Test
    fun `readZip rejects dotdot traversal entry`() {
        val evilEntries = mapOf(
            "notes.json" to "[]".toByteArray(),
            "../../../etc/passwd" to "evil".toByteArray()
        )
        val baos = ByteArrayOutputStream()
        zipHelper.writeZip(evilEntries, baos)

        assertThrows(ImportRejected::class.java) {
            zipHelper.readZip(ByteArrayInputStream(baos.toByteArray()))
        }
    }

    @Test
    fun `readZip rejects absolute path entry`() {
        val evilEntries = mapOf(
            "notes.json" to "[]".toByteArray(),
            "/etc/passwd" to "evil".toByteArray()
        )
        val baos = ByteArrayOutputStream()
        zipHelper.writeZip(evilEntries, baos)

        assertThrows(ImportRejected::class.java) {
            zipHelper.readZip(ByteArrayInputStream(baos.toByteArray()))
        }
    }

    @Test
    fun `readZip rejects windows-style traversal`() {
        val evilEntries = mapOf(
            "notes.json" to "[]".toByteArray(),
            "..\\..\\evil.exe" to "evil".toByteArray()
        )
        val baos = ByteArrayOutputStream()
        zipHelper.writeZip(evilEntries, baos)

        assertThrows(ImportRejected::class.java) {
            zipHelper.readZip(ByteArrayInputStream(baos.toByteArray()))
        }
    }

    @Test
    fun `readZip accepts benign entries`() {
        val okEntries = mapOf(
            "notes.json" to "[]".toByteArray(),
            "ai_history.json" to "[]".toByteArray(),
            "tags.json" to "{}".toByteArray(),
            "meta.json" to "{}".toByteArray()
        )
        val baos = ByteArrayOutputStream()
        zipHelper.writeZip(okEntries, baos)

        val out = zipHelper.readZip(ByteArrayInputStream(baos.toByteArray()))
        assertEquals(okEntries.size, out.size)
        assertTrue(out.containsKey("notes.json"))
    }
}
