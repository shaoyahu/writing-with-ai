package com.yy.writingwithai.core.data.export

import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.core.data.repo.AiHistoryRepository
import com.yy.writingwithai.core.data.repo.NoteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

/**
 * M4-3 · NoteImporter 单元测试(JUnit5 + MockK)。
 *
 * 覆盖 spec §"NoteImporter imports zip via id-dedup" + "失败条目收集到 import_report.md" 场景:
 * - 导入新笔记 → successCount += 1
 * - 已存在 id → skippedCount += 1(不覆盖)
 * - 输出 zip 含 `import_report.md` entry(Markdown 报告)
 * - 失败条目 → failedNotes 收集
 */
class NoteImporterTest {
    private val noteRepository: NoteRepository = mockk()
    private val aiHistoryRepository: AiHistoryRepository = mockk()
    private val zipHelper = ZipHelper()
    private val importer =
        NoteImporter(
            noteRepository = noteRepository,
            aiHistoryRepository = aiHistoryRepository,
            zipHelper = zipHelper
        )

    /** 构造测试用 zip(包含 notes.json + tags.json，无 ai_history.json)。 */
    private fun buildZipBytes(
        notes: List<ExportNote>,
        tags: Map<String, List<String>> = emptyMap(),
        aiHistories: List<ExportAiHistory> = emptyList()
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val entries = mutableMapOf<String, ByteArray>()
        entries["notes.json"] =
            ExportJsonFormat.encodeToString(ExportNoteListSerializer, notes)
                .toByteArray(Charsets.UTF_8)
        entries["tags.json"] =
            ExportJsonFormat.encodeToString(ExportTags.serializer(), ExportTags(tags))
                .toByteArray(Charsets.UTF_8)
        if (aiHistories.isNotEmpty()) {
            entries["ai_history.json"] =
                ExportJsonFormat
                    .encodeToString(ExportAiHistoryListSerializer, aiHistories)
                    .toByteArray(Charsets.UTF_8)
        }
        zipHelper.writeZip(entries, out)
        return out.toByteArray()
    }

    private fun readEntries(zipBytes: ByteArray): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                map[e.name] = zin.readBytes()
                e = zin.nextEntry
            }
        }
        return map
    }

    @Test
    fun importFromZip_imports_new_note_and_writes_report() = runTest {
        val bytes =
            buildZipBytes(
                notes = listOf(ExportNote(id = "n1", title = "新", content = "内容")),
                tags = mapOf("n1" to listOf("灵感"))
            )
        coEvery { noteRepository.getNote("n1") } returns null
        coEvery { noteRepository.upsert(any<Note>(), any()) } returns Unit

        val out = ByteArrayOutputStream()
        val report = importer.importFromZip(ByteArrayInputStream(bytes), out)

        assertEquals(1, report.successCount)
        assertEquals(0, report.skippedCount)
        assertEquals(0, report.failedCount)
        coVerify(exactly = 1) {
            noteRepository.upsert(
                match { it.id == "n1" && it.title == "新" && it.content == "内容" },
                match { it == listOf("灵感") }
            )
        }

        val outEntries = readEntries(out.toByteArray())
        assertTrue(outEntries.containsKey("import_report.md"), "输出 zip MUST 含 import_report.md")
        val reportText = outEntries["import_report.md"]!!.toString(Charsets.UTF_8)
        assertTrue(reportText.contains("# writing-with-ai 导入报告"))
        assertTrue(reportText.contains("- 成功:1"))
        assertTrue(reportText.contains("- 跳过(id 重复):0"))
        assertTrue(reportText.contains("- 失败:0"))

        // 原 entries 也保留(报告 zip 是 zip-in-zip 完整副本)
        assertTrue(outEntries.containsKey("notes.json"))
        assertTrue(outEntries.containsKey("tags.json"))
    }

    @Test
    fun importFromZip_skips_existing_note_id() = runTest {
        val bytes =
            buildZipBytes(
                notes =
                listOf(
                    ExportNote(id = "n1", title = "新", content = "内容"),
                    ExportNote(id = "n2", title = "已存在", content = "老的")
                )
            )
        coEvery { noteRepository.getNote("n1") } returns null
        coEvery { noteRepository.getNote("n2") } returns
            Note(
                id = "n2",
                title = "老的",
                content = "老的",
                createdAt = 0L,
                updatedAt = 0L,
                isPinned = false,
                lastAiOp = null,
                lastAiAt = null
            )
        coEvery { noteRepository.upsert(any<Note>(), any()) } returns Unit

        val out = ByteArrayOutputStream()
        val report = importer.importFromZip(ByteArrayInputStream(bytes), out)

        assertEquals(1, report.successCount, "n1 应被导入")
        assertEquals(1, report.skippedCount, "n2 应被跳过")
        assertEquals(0, report.failedCount)
        coVerify(exactly = 1) {
            noteRepository.upsert(match { it.id == "n1" }, any())
        }
        coVerify(exactly = 0) {
            noteRepository.upsert(match { it.id == "n2" }, any())
        }
    }

    @Test
    fun importFromZip_collects_failed_note_on_upsert_exception() = runTest {
        val bytes =
            buildZipBytes(notes = listOf(ExportNote(id = "bad", title = "坏的", content = "x")))
        coEvery { noteRepository.getNote("bad") } returns null
        coEvery { noteRepository.upsert(any<Note>(), any()) } throws RuntimeException("simulated UNIQUE")

        val out = ByteArrayOutputStream()
        val report = importer.importFromZip(ByteArrayInputStream(bytes), out)

        assertEquals(0, report.successCount)
        assertEquals(0, report.skippedCount)
        assertEquals(1, report.failedCount)
        assertEquals(1, report.failedNotes.size)
        assertEquals("bad", report.failedNotes[0].noteId)
        assertEquals("坏的", report.failedNotes[0].title)
        assertTrue(report.failedNotes[0].error.contains("simulated UNIQUE"))

        val outEntries = readEntries(out.toByteArray())
        assertNotNull(outEntries["import_report.md"])
        val reportText = outEntries["import_report.md"]!!.toString(Charsets.UTF_8)
        assertTrue(reportText.contains("## 失败详情"))
        assertTrue(reportText.contains("note id=bad"))
        assertTrue(reportText.contains("simulated UNIQUE"))
    }

    @Test
    fun importFromZip_empty_notes_returns_zero_report() = runTest {
        val bytes = buildZipBytes(notes = emptyList())

        val out = ByteArrayOutputStream()
        val report = importer.importFromZip(ByteArrayInputStream(bytes), out)

        assertEquals(0, report.successCount)
        assertEquals(0, report.skippedCount)
        assertEquals(0, report.failedCount)
        assertTrue(report.failedNotes.isEmpty())

        val outEntries = readEntries(out.toByteArray())
        val reportText = outEntries["import_report.md"]!!.toString(Charsets.UTF_8)
        assertTrue(reportText.contains("- 失败:0"))
        assertTrue(!reportText.contains("## 失败详情"))
    }

    @Test
    fun importFromZip_cancellation_propagates_without_recording_failure() = runTest {
        // H1:r1 review。catch (Exception) 之前必须有 catch (CancellationException) { throw e },
        // 否则协程取消会被吞进 failedNotes。
        coEvery { noteRepository.getNote("c1") } returns null
        coEvery { noteRepository.upsert(any<Note>(), any()) } throws
            kotlinx.coroutines.CancellationException("test cancel")

        val bytes = buildZipBytes(notes = listOf(ExportNote(id = "c1", title = "c", content = "x")))
        val out = ByteArrayOutputStream()

        try {
            importer.importFromZip(ByteArrayInputStream(bytes), out)
            fail("expected CancellationException")
        } catch (e: kotlinx.coroutines.CancellationException) {
            assertEquals("test cancel", e.message)
        }
    }
}
