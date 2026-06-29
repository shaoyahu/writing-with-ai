package com.yy.writingwithai.feature.quicknote.share

import com.yy.writingwithai.core.feishu.api.FeishuError
import com.yy.writingwithai.core.feishu.sync.FeishuRefDao
import com.yy.writingwithai.core.feishu.sync.FeishuRefEntity
import com.yy.writingwithai.core.feishu.sync.FeishuRefStatus
import com.yy.writingwithai.core.feishu.sync.FeishuSyncService
import com.yy.writingwithai.core.feishu.sync.SyncDirection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * feishu-sync-end-to-end · FeishuShareViewModel 薄包装 JVM 单测(tasks §4)。
 *
 * 覆盖 sealed [ShareState] 6 个非 Idle 分支:
 * - Pushing → Pushed(ref 的 docUrl + service 返回消息)
 * - Pushing → Error(FeishuError 抛出)
 * - Pulling → Pulled(refDao 反查的 noteId + 截取 service 返回 title)
 * - Pulling → Error(FeishuError 抛出)
 * - resolveConflictKeepLocal:重置 ref.status=DIRTY + 触发 push + clearState
 * - clearState:任何态切回 Idle
 *
 * 不挂 UI、不触发 Hilt,纯 VM 行为验证。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeishuShareViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private val syncService: FeishuSyncService = mockk()
    private val refDao: FeishuRefDao = mockk()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * §4.2 · push OK → [ShareState.Pushed] 携带 ref 的 docUrl 与 service 返回消息。
     */
    @Test
    fun `push succeeds yields Pushed with ref docUrl`() = runTest {
        coEvery { syncService.push("n1") } returns "同步完成: https://bytedance.feishu.cn/docx/abc123"
        coEvery {
            syncService.getRef("n1")
        } returns sampleRef(noteId = "n1", docUrl = "https://bytedance.feishu.cn/docx/abc123")

        val vm = FeishuShareViewModel(syncService, refDao)
        vm.push("n1")

        val state = vm.state.value
        assertTrue(state is ShareState.Pushed) { "Expected Pushed, got $state" }
        state as ShareState.Pushed
        assertEquals("https://bytedance.feishu.cn/docx/abc123", state.docUrl)
        assertEquals("同步完成: https://bytedance.feishu.cn/docx/abc123", state.message)
    }

    /**
     * §4.3 · push fail → [ShareState.Error],service.getRef 不被调。
     */
    @Test
    fun `push on FeishuError yields Error and skips getRef`() = runTest {
        coEvery { syncService.push("n1") } throws FeishuError.NetworkError("timeout")

        val vm = FeishuShareViewModel(syncService, refDao)
        vm.push("n1")

        val state = vm.state.value
        assertTrue(state is ShareState.Error) { "Expected Error, got $state" }
        state as ShareState.Error
        assertEquals("飞书网络错误: timeout", state.message)
        coVerify(exactly = 0) { syncService.getRef(any()) }
    }

    /**
     * §4.4 · pull OK → [ShareState.Pulled] 携带 refDao 反查的 noteId 与 service 返回截取的 title。
     */
    @Test
    fun `pull succeeds yields Pulled with noteId and title from service message`() = runTest {
        val docUrl = "https://bytedance.feishu.cn/docx/xyz789"
        coEvery { syncService.pull("xyz789", docUrl, "来自飞书") } returns "拉取完成: 远端文档标题"
        coEvery { refDao.getByDocId("xyz789") } returns sampleRef(noteId = "n42", docId = "xyz789", docUrl = docUrl)

        val vm = FeishuShareViewModel(syncService, refDao)
        vm.pull(docUrl)

        val state = vm.state.value
        assertTrue(state is ShareState.Pulled) { "Expected Pulled, got $state" }
        state as ShareState.Pulled
        assertEquals("n42", state.noteId)
        assertEquals("远端文档标题", state.title)
        assertEquals("拉取完成: 远端文档标题", state.message)
    }

    /**
     * §4.5 · pull fail → [ShareState.Error],消息取自 FeishuError.message。
     */
    @Test
    fun `pull on FeishuError yields Error`() = runTest {
        val docUrl = "https://bytedance.feishu.cn/docx/missing"
        coEvery { syncService.pull(any(), any(), any()) } throws FeishuError.NotFound("doc not found")

        val vm = FeishuShareViewModel(syncService, refDao)
        vm.pull(docUrl)

        val state = vm.state.value
        assertTrue(state is ShareState.Error) { "Expected Error, got $state" }
        state as ShareState.Error
        assertEquals("飞书资源不存在: doc not found", state.message)
    }

    /**
     * §4.6a · clearState:任何态切回 Idle。
     */
    @Test
    fun `clearState resets to Idle`() = runTest {
        coEvery { syncService.push("n1") } returns "同步完成: https://bytedance.feishu.cn/docx/abc"
        coEvery { syncService.getRef("n1") } returns sampleRef(noteId = "n1")

        val vm = FeishuShareViewModel(syncService, refDao)
        vm.push("n1")
        assertTrue(vm.state.value is ShareState.Pushed)
        vm.clearState()
        assertEquals(ShareState.Idle, vm.state.value)
    }

    /**
     * §4.6b · resolveConflictKeepLocal:ref 存在 → upsert 切 DIRTY + clearState + 触发 push。
     */
    @Test
    fun `resolveConflictKeepLocal upserts DIRTY then pushes`() = runTest {
        val ref = sampleRef(noteId = "n1")
        coEvery { refDao.getByNoteId("n1") } returns ref
        coEvery { refDao.upsert(any()) } returns Unit
        coEvery { syncService.push("n1") } returns "同步完成: https://bytedance.feishu.cn/docx/abc"
        coEvery { syncService.getRef("n1") } returns ref.copy(status = FeishuRefStatus.DIRTY)

        val vm = FeishuShareViewModel(syncService, refDao)
        vm.resolveConflictKeepLocal("n1")

        coVerify(exactly = 1) {
            refDao.upsert(match { it.noteId == "n1" && it.status == FeishuRefStatus.DIRTY })
        }
        coVerify(exactly = 1) { syncService.push("n1") }
    }

    /**
     * 边界 · resolveConflictKeepLocal ref 不存在 → Error。
     */
    @Test
    fun `resolveConflictKeepLocal yields Error when ref missing`() = runTest {
        coEvery { refDao.getByNoteId("missing") } returns null

        val vm = FeishuShareViewModel(syncService, refDao)
        vm.resolveConflictKeepLocal("missing")

        val state = vm.state.value
        assertTrue(state is ShareState.Error) { "Expected Error, got $state" }
        state as ShareState.Error
        assertTrue(state.message.contains("missing"))
    }

    private fun sampleRef(
        noteId: String = "n1",
        docId: String = "doc1",
        docUrl: String = "https://bytedance.feishu.cn/docx/doc1",
        status: FeishuRefStatus = FeishuRefStatus.SYNCED
    ) = FeishuRefEntity(
        noteId = noteId,
        docId = docId,
        docUrl = docUrl,
        lastSyncedAt = 1_700_000_000_000L,
        syncDirection = SyncDirection.PUSH,
        localRevision = 1_700_000_000_000L,
        remoteRevision = "rev-1",
        status = status
    )
}
