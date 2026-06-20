package com.yy.writingwithai.core.data.export

import com.yy.writingwithai.core.data.db.NoteTagDao
import com.yy.writingwithai.core.data.model.AiHistory
import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.core.data.model.NoteWithTags
import com.yy.writingwithai.core.data.repo.AiHistoryRepository
import com.yy.writingwithai.core.data.repo.NoteRepository
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * M4-3 · NoteExporter 单元测试(JUnit5 + MockK)。
 *
 * 覆盖 spec §"NoteExporter exports notes + ai_history + tags to JSON zip" 场景:
 * - 导出 zip 含 4 JSON 文件 + notes/<id>.md
 * - meta.json 含 timestamp / appVersion / schemaVersion
 * - tags.json 是 noteId → List<String>
 * - 返回 Int = notes.size
 */
class NoteExporterTest {
    private val noteRepository: NoteRepository = mockk()
    private val aiHistoryRepository: AiHistoryRepository = mockk()
    private val noteTagDao: NoteTagDao = mockk()
    private val zipHelper = ZipHelper()
    private val exporter =
        NoteExporter(
            noteRepository = noteRepository,
            aiHistoryRepository = aiHistoryRepository,
            noteTagDao = noteTagDao,
            zipHelper = zipHelper
        )

    @Test
    fun exportToJsonZip_writes_four_json_files_and_markdown_per_note() = runTest {
        val notes =
            listOf(
                Note(
                    id = "n1",
                    title = "晨跑",
                    content = "5 公里",
                    createdAt = 100L,
                    updatedAt = 100L,
                    isPinned = false,
                    lastAiOp = null,
                    lastAiAt = null
                ),
                Note(
                    id = "n2",
                    title = "",
                    content = "空标题 fallback 测试",
                    createdAt = 200L,
                    updatedAt = 200L,
                    isPinned = false,
                    lastAiOp = null,
                    lastAiAt = null
                )
            )
        val withTags = notes.map { NoteWithTags(it, emptyList()) }
        every { noteRepository.observeNotesWithTags(null, null) } returns flowOf(withTags)
        every { aiHistoryRepository.observeAll(Int.MAX_VALUE) } returns
            flowOf(
                listOf(
                    AiHistory(
                        id = "h1",
                        noteId = "n1",
                        providerId = "fake",
                        model = "fake-model",
                        op = "POLISH",
                        inputTokens = 10,
                        outputTokens = 20,
                        totalTokens = 30,
                        durationMs = 100L,
                        createdAt = 100L,
                        inputSnapshot = "in",
                        outputSnapshot = "out",
                        truncated = false,
                        error = null
                    )
                )
            )
        every { noteTagDao.observeAllCrossRefs() } returns flowOf(emptyList())

        val out = ByteArrayOutputStream()
        val count = exporter.exportToJsonZip(out)

        assertEquals(2, count, "exportToJsonZip MUST 返回 notes.size")

        val zipBytes = out.toByteArray()
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                entries[e.name] = zin.readBytes()
                e = zin.nextEntry
            }
        }

        assertEquals(6, entries.size, "zip MUST 含 4 JSON + 2 Markdown = 6 文件")
        assertTrue(entries.containsKey("notes.json"))
        assertTrue(entries.containsKey("ai_history.json"))
        assertTrue(entries.containsKey("tags.json"))
        assertTrue(entries.containsKey("meta.json"))
        assertTrue(entries.containsKey("notes/n1.md"))
        assertTrue(entries.containsKey("notes/n2.md"))

        val notesJson =
            ExportJsonFormat
                .decodeFromString(ExportNoteListSerializer, entries["notes.json"]!!.toString(Charsets.UTF_8))
        assertEquals(2, notesJson.size)
        assertEquals("晨跑", notesJson[0].title)
        assertEquals("", notesJson[1].title)

        val md2 = entries["notes/n2.md"]!!.toString(Charsets.UTF_8)
        assertTrue(md2.startsWith("# Untitled"), "空标题 MUST fallback '# Untitled'")

        val meta =
            ExportJsonFormat
                .decodeFromString(ExportMeta.serializer(), entries["meta.json"]!!.toString(Charsets.UTF_8))
        assertNotNull(meta.exportTimestamp)
        assertEquals("0.4.0", meta.appVersion)
        assertEquals("1", meta.schemaVersion)
    }

    @Test
    fun exportToJsonZip_empty_notes_returns_zero_and_no_markdown() = runTest {
        every { noteRepository.observeNotesWithTags(null, null) } returns flowOf(emptyList())
        every { aiHistoryRepository.observeAll(Int.MAX_VALUE) } returns flowOf(emptyList())
        every { noteTagDao.observeAllCrossRefs() } returns flowOf(emptyList())

        val out = ByteArrayOutputStream()
        val count = exporter.exportToJsonZip(out)

        assertEquals(0, count)
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                entries[e.name] = zin.readBytes()
                e = zin.nextEntry
            }
        }
        assertEquals(4, entries.size, "无 notes 时 MUST 只有 4 个 JSON,无 notes/*.md")
        assertTrue(entries.keys.none { it.startsWith("notes/") })
    }
}
