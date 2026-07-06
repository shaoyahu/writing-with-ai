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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * feishu-folder-migration · folder token 变更检测 + 迁移 push 测试。
 *
 * 验证:
 * - push 首次创建文档时 ref.folderToken 正确记录
 * - push 检测到 folder token 变更时抛 FolderTokenMismatch
 * - pushWithFolderMigration 两种选择(DELETE_AND_RECREATE / UPDATE_IN_PLACE)行为正确
 * - DELETE_AND_RECREATE 路径:远端删除失败 → 抛 ServerError,本地 ref 保留
 */
class FeishuFolderMigrationTest {
    private val api = FakeFeishuApiClient()
    private val notes = mockk<NoteRepository>(relaxed = true)
    private val refs = FakeFeishuRefDao()
    private val events = FakeFeishuSyncEventDao()
    private val xml = FakeXmlConverter()

    // 当前 folder token,测试中切换模拟用户修改设置
    private var currentFolderToken: String? = null

    // 控制 fake deleteFile 是否抛错,验证 DELETE_FAILED 路径
    private var deleteFileShouldFail: Boolean = false

    private val docService = FeishuDocService(api, xml, refs, events, FakeNoteAttachmentDao())
    private val authStore = FakeFeishuAuthStore(folderTokenSnapshot = { currentFolderToken })
    private val noteDao = mockk<com.yy.writingwithai.core.data.db.NoteDao>(relaxed = true)
    private val passthroughTx = object : TransactionExecutor {
        override suspend fun <R> execute(block: suspend () -> R): R = block()
    }
    private val conflictResolver = FeishuConflictResolver()
    private val service = FeishuSyncService(
        notes,
        docService,
        refs,
        events,
        authStore,
        noteDao,
        passthroughTx,
        conflictResolver
    )

    @BeforeEach
    fun setup() {
        currentFolderToken = null
        deleteFileShouldFail = false
    }

    // ---- folderToken 记录 ----

    @Test
    fun `push creates doc with folderToken in ref`() = runTest {
        currentFolderToken = "fldcnABC123"
        coEvery { notes.getNote("n1") } returns sampleNote("n1", content = "# hello")

        service.push("n1")

        val ref = refs.getByNoteId("n1")
        assertNotNull(ref)
        assertEquals("fldcnABC123", ref?.folderToken)
    }

    @Test
    fun `push creates doc with null folderToken when no folder set`() = runTest {
        currentFolderToken = null
        coEvery { notes.getNote("n1") } returns sampleNote("n1", content = "# hello")

        service.push("n1")

        val ref = refs.getByNoteId("n1")
        assertNotNull(ref)
        assertEquals(null, ref?.folderToken)
    }

    // ---- folder token mismatch 检测 ----

    @Test
    fun `push throws FolderTokenMismatch when folder token changed`() = runTest {
        // 先在 folder A 下创建文档
        currentFolderToken = "fldcnFolderA"
        coEvery { notes.getNote("n1") } returns sampleNote("n1", content = "content")
        service.push("n1")

        // 模拟用户修改 folder token 为 folder B
        currentFolderToken = "fldcnFolderB"

        // 再次 push 应检测到 mismatch
        val ex = assertThrows(FeishuError.FolderTokenMismatch::class.java) {
            kotlinx.coroutines.runBlocking { service.push("n1") }
        }
        assertEquals("n1", ex.noteId)
        assertEquals("fldcnFolderB", ex.currentFolderToken)
        assertEquals("fldcnFolderA", ex.refFolderToken)
    }

    @Test
    fun `push does not throw mismatch when folder token unchanged`() = runTest {
        currentFolderToken = "fldcnSameFolder"
        coEvery { notes.getNote("n1") } returns sampleNote("n1", content = "content")

        // 首次 push
        service.push("n1")

        // 同一 folder token 再次 push 不应抛 mismatch
        // (可能因 conflict 检测抛 Conflict,但不应是 FolderTokenMismatch)
        try {
            service.push("n1")
        } catch (e: FeishuError.FolderTokenMismatch) {
            throw AssertionError("不应抛 FolderTokenMismatch", e)
        } catch (_: FeishuError.Conflict) {
            // conflict 是允许的(folder token 没变但远端有修改)
        }
    }

    @Test
    fun `push does not throw mismatch when both folder tokens are null`() = runTest {
        currentFolderToken = null
        coEvery { notes.getNote("n1") } returns sampleNote("n1", content = "content")

        // 首次 push(folderToken = null)
        service.push("n1")

        // 再次 push(folderToken 仍为 null)
        try {
            service.push("n1")
        } catch (e: FeishuError.FolderTokenMismatch) {
            throw AssertionError("不应抛 FolderTokenMismatch", e)
        } catch (_: FeishuError.Conflict) {
            // conflict 是允许的
        }
    }

    @Test
    fun `push migrates legacy null ref when setting has new value (no dialog)`() = runTest {
        // 先在无 folder 下创建(ref.folderToken = null)
        currentFolderToken = null
        coEvery { notes.getNote("n1") } returns sampleNote("n1", content = "content")
        service.push("n1")

        // 用户设置了 folder token — 旧数据视为"未知 → 已知"迁移,不应弹 FolderMigration Dialog
        currentFolderToken = "fldcnNewFolder"

        // 不应抛 FolderTokenMismatch,直接走 updateDoc 成功路径
        val ref = kotlinx.coroutines.runBlocking { service.push("n1") }
        assertEquals("fldcnNewFolder", ref.folderToken)
    }

    @Test
    fun `push throws mismatch when ref has value but setting is null`() = runTest {
        // 先在有 folder 下创建
        currentFolderToken = "fldcnOldFolder"
        coEvery { notes.getNote("n1") } returns sampleNote("n1", content = "content")
        service.push("n1")

        // 用户清空了 folder token
        currentFolderToken = null

        val ex = assertThrows(FeishuError.FolderTokenMismatch::class.java) {
            kotlinx.coroutines.runBlocking { service.push("n1") }
        }
        assertEquals(null, ex.currentFolderToken)
        assertEquals("fldcnOldFolder", ex.refFolderToken)
    }

    // ---- pushWithFolderMigration ----

    @Test
    fun `pushWithFolderMigration DELETE_AND_RECREATE deletes old and creates new`() = runTest {
        // 先在 folder A 下创建
        currentFolderToken = "fldcnFolderA"
        coEvery { notes.getNote("n1") } returns sampleNote("n1", content = "content")
        service.push("n1")

        val oldDocId = refs.getByNoteId("n1")?.docId
        assertNotNull(oldDocId)

        // 用户修改 folder token
        currentFolderToken = "fldcnFolderB"

        // 选择删除+新建
        val result = service.pushWithFolderMigration("n1", FolderMigrationChoice.DELETE_AND_RECREATE)
        assertTrue(result.docUrl.isNotBlank())

        // 应调了 deleteFile
        assertEquals(1, api.deleteFileCalls)
        assertEquals(oldDocId, api.deletedFileTokens[0])

        // 新 ref 应指向新文档(v2 create 会生成新 docId)
        val newRef = refs.getByNoteId("n1")
        assertNotNull(newRef)
        assertTrue(newRef!!.docId != oldDocId)
        assertEquals("fldcnFolderB", newRef.folderToken)
    }

    @Test
    fun `pushWithFolderMigration UPDATE_IN_PLACE updates content without changing folder`() = runTest {
        // 先在 folder A 下创建
        currentFolderToken = "fldcnFolderA"
        coEvery { notes.getNote("n1") } returns sampleNote("n1", content = "old content")
        service.push("n1")

        val originalDocId = refs.getByNoteId("n1")?.docId
        assertNotNull(originalDocId)

        // 用户修改 folder token,但选择原地更新
        currentFolderToken = "fldcnFolderB"

        val result = service.pushWithFolderMigration("n1", FolderMigrationChoice.UPDATE_IN_PLACE)
        assertTrue(result.docUrl.isNotBlank())

        // 不应调 deleteFile
        assertEquals(0, api.deleteFileCalls)

        // docId 应不变(仍在原文件夹)
        val ref = refs.getByNoteId("n1")
        assertNotNull(ref)
        assertEquals(originalDocId, ref!!.docId)
        // folderToken 不变(仍然是原来的)
        assertEquals("fldcnFolderA", ref.folderToken)
    }

    @Test
    fun `pushWithFolderMigration throws when no existing ref`() = runTest {
        coEvery { notes.getNote("n1") } returns sampleNote("n1", content = "content")

        assertThrows(FeishuError.BadRequest::class.java) {
            kotlinx.coroutines.runBlocking {
                service.pushWithFolderMigration("n1", FolderMigrationChoice.DELETE_AND_RECREATE)
            }
        }
    }

    /**
     * 删远端失败 → 抛 ServerError,本地 ref **不应** 被删(用户可重试或选 UPDATE_IN_PLACE)。
     * 修复前:deleteDoc 失败后 refDao.deleteByNoteId + createDoc 仍执行,导致远端旧文档
     * 残留 + 本地 ref 指向新文档的不一致。
     */
    @Test
    fun `DELETE_AND_RECREATE preserves ref when remote delete fails`() = runTest {
        // 1. 先在 folder A 下创建
        currentFolderToken = "fldcnFolderA"
        coEvery { notes.getNote("n1") } returns sampleNote("n1", content = "content")
        service.push("n1")

        val originalDocId = refs.getByNoteId("n1")?.docId
        assertNotNull(originalDocId)

        // 2. 让远端删除失败
        currentFolderToken = "fldcnFolderB"
        api.deleteFileShouldFail = true

        // 3. 应抛 ServerError
        assertThrows(FeishuError.ServerError::class.java) {
            kotlinx.coroutines.runBlocking {
                service.pushWithFolderMigration("n1", FolderMigrationChoice.DELETE_AND_RECREATE)
            }
        }

        // 4. 本地 ref 保留(指向旧 docId),不指向新文档
        val refAfterFail = refs.getByNoteId("n1")
        assertNotNull(refAfterFail)
        assertEquals(originalDocId, refAfterFail!!.docId, "ref 应保留指向旧文档")
        assertEquals("fldcnFolderA", refAfterFail.folderToken, "folder token 不应变")

        // 5. 事件流应有 DELETE_FAILED + DELETE_AND_RECREATE_ABORTED 两条
        val statuses = events.store.map { it.status }
        assertTrue("DELETE_FAILED" in statuses, "应记录 DELETE_FAILED 事件: $statuses")
        assertTrue("DELETE_AND_RECREATE_ABORTED" in statuses, "应记录 ABORTED 事件: $statuses")

        // 6. 当远端恢复后,用户重试应能成功
        api.deleteFileShouldFail = false
        val result = service.pushWithFolderMigration("n1", FolderMigrationChoice.DELETE_AND_RECREATE)
        assertTrue(result.docUrl.isNotBlank())
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
