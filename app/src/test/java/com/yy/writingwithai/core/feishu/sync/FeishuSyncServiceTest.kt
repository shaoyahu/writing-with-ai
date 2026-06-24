package com.yy.writingwithai.core.feishu.sync

import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.core.feishu.api.FeishuError
import com.yy.writingwithai.core.feishu.auth.FeishuAuthState
import com.yy.writingwithai.core.feishu.auth.FeishuAuthStore
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
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
 *
 * feishu-doc-service-refactor(M3):FeishuDocService 改为 facade,内部委托 FeishuDocService。
 * 测试构造 FeishuDocService(api, xml, refs, events) 把 4 个底层依赖传入。
 */
class FeishuSyncServiceTest {
    private val api = FakeFeishuApiClient()
    private val notes = mockk<NoteRepository>(relaxed = true)
    private val refs = FakeFeishuRefDao()
    private val events = FakeFeishuSyncEventDao()
    private val xml = FakeXmlConverter()
    private val docService = FeishuDocService(api, xml, refs, events)
    private val fakeAuthStore = object : FeishuAuthStore {
        override val appId: Flow<String?> = flowOf(null)
        override val folderToken: Flow<String?> = flowOf(null)
        override val accessToken: Flow<String?> = flowOf(null)
        override val refreshToken: Flow<String?> = flowOf(null)
        override val expiresAt: Flow<Long?> = flowOf(null)
        override val authState: StateFlow<FeishuAuthState> =
            MutableStateFlow(FeishuAuthState.CONFIGURED)
        override val prefsInitError: Throwable? = null
        override suspend fun setOAuthCredentials(a: String, ac: String, rt: String, e: Long) {}
        override suspend fun setAuthState(s: FeishuAuthState) {}
        override suspend fun clearAll() {}
        override fun getAccessTokenSnapshot(): Pair<String, Long>? = null
        override fun getRefreshTokenSnapshot(): String? = null
        override fun getFolderTokenSnapshot(): String? = null
        override fun getAppIdAndRefreshToken(): Pair<String, String>? = null
        override suspend fun persistAppSecret(secret: String) {}
        override suspend fun clearAppSecret() {}
        override fun getAppSecretSnapshot(): String? = null
        override fun getAppIdAndSecret(): Pair<String, String>? = null
    }
    private val service = FeishuSyncService(notes, docService, refs, events, fakeAuthStore)

    @Test
    fun `push with no existing ref creates new feishu doc via v2`() = runTest {
        coEvery { notes.getNote("n1") } returns sampleNote("n1", content = "# hello")
        coEvery { notes.upsert(any(), any()) } returns Unit

        val result = service.push("n1")
        assertTrue(result.startsWith("同步完成"))
        val ref = refs.getByNoteId("n1")
        assertNotNull(ref)
        assertEquals("doc-v2-1", ref?.docId)
        assertEquals(FeishuRefStatus.SYNCED, ref?.status)
        assertEquals(1, events.store.size)
        assertEquals("OK", events.store[0].status)
        // push 应走 v2 createDocumentV2(xml),不走 v1
        assertEquals(1, api.v2CreateCalls)
    }

    @Test
    fun `push with existing ref updates via v2 overwrite`() = runTest {
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
        assertEquals(1, api.v2UpdateCalls)
        // 不应再调 v1 batch_delete(回归 C3:第二次同步 404)
        val ref = refs.getByNoteId("n1")
        assertEquals("doc-existing", ref?.docId)
        assertEquals("rev-2", ref?.remoteRevision)
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
    fun `pull with empty markdown throws BadRequest empty content protection`() = runTest {
        api.markdownToReturn = ""
        val ex = assertThrows(FeishuError.BadRequest::class.java) {
            kotlinx.coroutines.runBlocking { service.pull("doc1", "https://f.cn/d1") }
        }
        assertTrue(ex.message?.contains("飞书端为空") == true)
    }

    @Test
    fun `pull with new docId creates new local note using fetched markdown`() = runTest {
        api.markdownToReturn = "# fetched\n\nbody from feishu"
        coEvery { notes.upsert(any(), any()) } returns Unit

        val msg = service.pull("doc1", "https://f.cn/d1", titleHint = "from-feishu")
        assertTrue(msg.contains("拉取完成"))
        val ref = refs.getByDocId("doc1")
        assertNotNull(ref)
        assertEquals(FeishuRefStatus.SYNCED, ref?.status)
        // 应调 v2 fetchDocumentV2 拿 markdown(回归 C2:之前 pull 写空)
        assertEquals(1, api.v2FetchCalls)
    }

    @Test
    fun `pull with existing docId updates local note content with fetched markdown`() = runTest {
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
        api.markdownToReturn = "new-from-feishu"

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
