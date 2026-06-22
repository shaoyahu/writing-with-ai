package com.yy.writingwithai.core.feishu.sync

import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.core.feishu.api.FeishuError
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * feishu-bidir-sync · FeishuSyncService push/pull 集成测试(tasks §10.1)。
 *
 * NoteRepository 用 mockk(因为是 final class 且含 Hilt 构造器);其他依赖用共享 fake。
 */
class FeishuSyncServiceTest {
    private val api = FakeFeishuApiClient()
    private val notes = mockk<NoteRepository>(relaxed = true)
    private val refs = FakeFeishuRefDao()
    private val events = FakeFeishuSyncEventDao()
    private val md = FakeMdConverter()
    private val docx = FakeDocxConverter()
    private val resolver = FeishuConflictResolver()
    private val service = FeishuSyncService(notes, api, md, docx, refs, events, resolver)

    @Test
    fun `push with no existing ref creates new feishu doc`() = runTest {
        coEvery { notes.getNote("n1") } returns sampleNote("n1", content = "# hello")
        coEvery { notes.upsert(any(), any()) } returns Unit

        val result = service.push("n1")
        assertTrue(result.startsWith("同步完成"))
        val ref = refs.getByNoteId("n1")
        assertNotNull(ref)
        assertEquals("doc-new-1", ref?.docId)
        assertEquals(FeishuRefStatus.SYNCED, ref?.status)
        assertEquals(1, events.store.size)
        assertEquals("OK", events.store[0].status)
    }

    @Test
    fun `push with existing ref reuses docId`() = runTest {
        coEvery { notes.getNote("n1") } returns sampleNote("n1", content = "old")
        coEvery { notes.upsert(any(), any()) } returns Unit
        refs.upsert(
            FeishuRefEntity(
                noteId = "n1",
                docId = "doc-existing",
                docUrl = "u",
                lastSyncedAt = 0L,
                syncDirection = SyncDirection.PUSH,
                localRevision = 0L,
                remoteRevision = "rev-old",
                status = FeishuRefStatus.SYNCED
            )
        )
        service.push("n1")
        assertEquals(0, api.createdDocs.size)
        val ref = refs.getByNoteId("n1")
        assertEquals("doc-existing", ref?.docId)
        assertEquals("rev-new", ref?.remoteRevision)
    }

    @Test
    fun `push with missing note throws NotFound`() = runTest {
        coEvery { notes.getNote("missing") } returns null
        val ex = assertThrows(FeishuError.NotFound::class.java) {
            kotlinx.coroutines.runBlocking { service.push("missing") }
        }
        assertTrue(ex.message?.contains("missing") == true)
    }

    @Test
    fun `pull with empty blocks throws BadRequest empty content protection`() = runTest {
        api.blocksToReturn = ""
        val ex = assertThrows(FeishuError.BadRequest::class.java) {
            kotlinx.coroutines.runBlocking { service.pull("doc1", "https://f.cn/d1") }
        }
        assertTrue(ex.message?.contains("飞书端为空") == true)
    }

    @Test
    fun `pull with new docId creates new local note`() = runTest {
        api.blocksToReturn = "[{\"block_type\":\"text\",\"content\":\"hi\"}]"
        docx.markdownToReturn = "hi"
        coEvery { notes.upsert(any(), any()) } returns Unit

        val msg = service.pull("doc1", "https://f.cn/d1", titleHint = "from-feishu")
        assertTrue(msg.contains("拉取完成"))
        val ref = refs.getByDocId("doc1")
        assertNotNull(ref)
        assertEquals(FeishuRefStatus.SYNCED, ref?.status)
    }

    @Test
    fun `pull with existing docId updates local note content`() = runTest {
        coEvery { notes.getNote("n1") } returns sampleNote("n1", content = "old", title = "old-title")
        coEvery { notes.upsert(any(), any()) } returns Unit
        refs.upsert(
            FeishuRefEntity(
                noteId = "n1",
                docId = "doc-existing",
                docUrl = "u",
                lastSyncedAt = 0L,
                syncDirection = SyncDirection.PULL,
                localRevision = 0L,
                remoteRevision = "rev-old",
                status = FeishuRefStatus.SYNCED
            )
        )
        api.blocksToReturn = "[{\"block_type\":\"text\",\"content\":\"new\"}]"
        docx.markdownToReturn = "new"

        service.pull("doc-existing", "u", titleHint = "updated")

        val refAfter = refs.getByNoteId("n1")
        assertEquals(FeishuRefStatus.SYNCED, refAfter?.status)
    }
}

private fun sampleNote(id: String, content: String, title: String = "t-$id"): Note = Note(
    id = id,
    title = title,
    content = content,
    createdAt = 1000L,
    updatedAt = 1000L,
    isPinned = false,
    lastAiOp = null,
    lastAiAt = null
)
