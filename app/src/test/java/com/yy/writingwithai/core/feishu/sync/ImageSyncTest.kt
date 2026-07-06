package com.yy.writingwithai.core.feishu.sync

import com.yy.writingwithai.core.data.db.entity.NoteAttachmentEntity
import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.core.feishu.api.FeishuApiClient
import com.yy.writingwithai.core.feishu.api.FeishuError
import io.mockk.coEvery
import io.mockk.mockk
import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * feishu-sync-image-support · syncAttachments 单元测试(tasks.md §4)。
 *
 * 覆盖 5 个 case:
 * - A: 0 附件 → Success,不发任何 API
 * - B: 1 张图 uploadMedia + appendChildren 都成功 → Success,无 event
 * - C: 2 张图第 2 张 uploadMedia 抛 ServerError → 降级:全部转占位符 + IMAGE_FAIL_PARTIAL event
 * - D: uploadMedia 全部 OK 但 appendChildren 抛 ServerError → 降级:全部转占位符 + IMAGE_FAIL_PARTIAL event
 * - E: uploadMedia 文件 > 20 MB 直接抛 BadRequest(模拟 FeishuApiClientImpl 入口校验)
 *   → syncAttachments 走降级 → IMAGE_FAIL_PARTIAL event
 */
class ImageSyncTest {
    private lateinit var api: FakeFeishuApiClient
    private lateinit var refs: FakeFeishuRefDao
    private lateinit var events: FakeFeishuSyncEventDao
    private lateinit var attachments: FakeNoteAttachmentDao
    private lateinit var service: FeishuDocService
    private lateinit var ref: FeishuRefEntity
    private lateinit var note: Note

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        api = FakeFeishuApiClient()
        refs = FakeFeishuRefDao()
        events = FakeFeishuSyncEventDao()
        attachments = FakeNoteAttachmentDao()
        service = FeishuDocService(api, FakeXmlConverter(), refs, events, attachments)
        note = Note(
            id = "note-1",
            title = "test",
            content = "# hello",
            createdAt = 100L,
            updatedAt = 200L,
            isPinned = false,
            lastAiOp = null,
            lastAiAt = null
        )
        ref = FeishuRefEntity(
            noteId = note.id,
            docId = "doc-target",
            docUrl = "https://f.cn/doc-target",
            lastSyncedAt = 100L,
            syncDirection = SyncDirection.PUSH,
            localRevision = note.updatedAt,
            remoteRevision = "rev-1",
            status = FeishuRefStatus.SYNCED,
            folderToken = null
        )
    }

    /**
     * 真实 multipart 构造在 [com.yy.writingwithai.core.feishu.api.FeishuApiClientImpl],
     * 这里 fake 不读 bytes,只关心 path 存在 + size 字段对应 entity.fileSize。
     * 写 0 字节模拟"该 path 是 image"语义,Case E 改成自定义 fake 直接抛 BadRequest。
     */
    private fun writeImageFile(name: String): String {
        val file = File(tempDir.toFile(), name)
        file.writeBytes(ByteArray(1))
        return file.absolutePath
    }

    private fun attachment(id: String, path: String, createdAt: Long): NoteAttachmentEntity = NoteAttachmentEntity(
        id = id,
        noteId = note.id,
        mimeType = "image/jpeg",
        localPath = path,
        fileSize = 1L,
        createdAt = createdAt
    )

    // ---- Case A:0 附件 → 直接 Success,无 API 调用 ----
    @Test
    fun `Case A zero attachments returns Success without any API call`() = runTest {
        attachments.store[note.id] = emptyList()
        val beforeUpload = api.uploadMediaCalls.size
        val beforeAppend = api.appendedDocIds.size
        val beforeEvent = events.store.size

        val outcome = service.syncAttachments(note, ref)

        assertEquals(ImageSyncOutcome.Success, outcome)
        assertEquals(beforeUpload, api.uploadMediaCalls.size)
        assertEquals(beforeAppend, api.appendedDocIds.size)
        assertEquals(beforeEvent, events.store.size)
    }

    // ---- Case B:1 张图 upload + createBlock 全成功 → Success,无 event ----
    @Test
    fun `Case B single image succeeds with no event`() = runTest {
        val path = writeImageFile("a.jpg")
        attachments.store[note.id] = listOf(attachment("att-1", path, createdAt = 10L))

        val outcome = service.syncAttachments(note, ref)

        assertEquals(ImageSyncOutcome.Success, outcome)
        assertEquals(1, api.uploadMediaCalls.size)
        assertEquals("doc-target", api.uploadMediaCalls[0].first)
        assertEquals(1, api.createBlockCalls.size, "应调用 createBlock 创建 image block")
        assertTrue(events.store.isEmpty(), "success 路径不应记 IMAGE_FAIL_PARTIAL event")
    }

    // ---- Case C:uploadMedia 任一次抛 ServerError → 全部降级 + event ----
    @Test
    fun `Case C uploadMedia failure triggers fallback for all attachments`() = runTest {
        val pathA = writeImageFile("a.jpg")
        val pathB = writeImageFile("b.jpg")
        attachments.store[note.id] = listOf(
            attachment("att-1", pathA, createdAt = 10L),
            attachment("att-2", pathB, createdAt = 20L)
        )
        api.uploadMediaShouldFail = true

        val outcome = service.syncAttachments(note, ref)

        assertTrue(outcome is ImageSyncOutcome.PartialFail)
        val fail = outcome as ImageSyncOutcome.PartialFail
        assertEquals(listOf("att-1", "att-2"), fail.failedIds)
        assertTrue(fail.reason.startsWith("image_fail:"), "实际 reason=${fail.reason}")
        assertTrue(events.store.any { it.status == "IMAGE_FAIL_PARTIAL" })
    }

    // ---- Case D:createBlock 失败 → 两张图都标记失败 + event ----
    @Test
    fun `Case D createBlock fails triggers fallback`() = runTest {
        val pathA = writeImageFile("a.jpg")
        val pathB = writeImageFile("b.jpg")
        attachments.store[note.id] = listOf(
            attachment("att-1", pathA, createdAt = 10L),
            attachment("att-2", pathB, createdAt = 20L)
        )
        api.createBlockShouldFail = true

        val outcome = service.syncAttachments(note, ref)

        assertTrue(outcome is ImageSyncOutcome.PartialFail)
        val fail = outcome as ImageSyncOutcome.PartialFail
        assertEquals(listOf("att-1", "att-2"), fail.failedIds)
        assertTrue(fail.reason.startsWith("image_fail:"), "实际 reason=${fail.reason}")
        // createBlock 失败时不会执行到 uploadMedia，所以 uploadMediaCalls 应为 0
        assertEquals(0, api.uploadMediaCalls.size, "createBlock 失败不应执行 uploadMedia")
        assertTrue(events.store.any { it.status == "IMAGE_FAIL_PARTIAL" })
    }

    // ---- Case E:uploadMedia 抛 BadRequest(file > 20 MB) → 走降级 + event ----
    @Test
    fun `Case E upload BadRequest triggers fallback and writes event`() = runTest {
        // 模拟 FeishuApiClientImpl.uploadMedia 在 size > 20MB 时抛 BadRequest 的行为。
        // 用 mockk relaxed 让 appendBlockV2 / updateDocumentV2 等其他方法 no-op default,
        // 然后单独覆盖 uploadMedia 抛错。
        val customApi = mockk<FeishuApiClient>(relaxed = true)
        coEvery { customApi.uploadMedia(any(), any(), any(), any()) } throws
            FeishuError.BadRequest(0, "file > 20 MB")
        // fix: mock getBlocks 返回正确结构
        coEvery { customApi.getBlocks(any()) } returns """{"data":{"items":[{"block_id":"root"}]}}"""
        val customService = FeishuDocService(
            customApi,
            FakeXmlConverter(),
            refs,
            events,
            attachments
        )
        val path = writeImageFile("big.jpg")
        attachments.store[note.id] = listOf(attachment("att-big", path, createdAt = 10L))

        val outcome = customService.syncAttachments(note, ref)

        assertTrue(outcome is ImageSyncOutcome.PartialFail)
        val fail = outcome as ImageSyncOutcome.PartialFail
        assertEquals(listOf("att-big"), fail.failedIds)
        // reason 形如 "image_fail:att-big"
        assertTrue(
            fail.reason.contains("image_fail:att-big"),
            "实际 reason=${fail.reason}"
        )
        assertTrue(events.store.any { it.status == "IMAGE_FAIL_PARTIAL" })
    }
}
