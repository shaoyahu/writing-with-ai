package com.yy.writingwithai.core.note

import android.content.Context
import com.yy.writingwithai.core.ai.api.AiGateway
import com.yy.writingwithai.core.data.db.AiHistoryDao
import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.dao.NoteLinkDao
import com.yy.writingwithai.core.data.db.entity.NoteEntity
import com.yy.writingwithai.core.note.impl.LlmNoteLinkExtractor
import com.yy.writingwithai.core.prefs.SecureApiKeyStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class LlmNoteLinkExtractorTest {
    private val gateway: AiGateway = mockk()
    private val noteLinkDao: NoteLinkDao = mockk()
    private val noteDao: NoteDao = mockk()
    private val apikeyStore: SecureApiKeyStore = mockk()
    private val aiHistoryDao: AiHistoryDao = mockk()
    private val ctx: Context = mockk()

    private val extractor = LlmNoteLinkExtractor(
        gateway,
        noteLinkDao,
        noteDao,
        apikeyStore,
        aiHistoryDao,
        ctx
    )

    @Test fun `no apikey returns early`() = runTest {
        coEvery { noteDao.getById("A") } returns note("A", "t", "hi")
        every { apikeyStore.getActiveProviderId() } returns "d"
        coEvery { apikeyStore.getApiKey("d") } returns null
        every { ctx.getSharedPreferences(any(), any()) } returns mockk(relaxed = true)
        extractor.extractAndPersist("A")
        coVerify(exactly = 0) { gateway.streamWritingOp(any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `note not found returns early`() = runTest {
        coEvery { noteDao.getById("A") } returns null
        extractor.extractAndPersist("A")
        coVerify(exactly = 0) { apikeyStore.getActiveProviderId() }
    }

    @Test fun `rate limit enforced`() {
        val prefs = mockk<android.content.SharedPreferences>()
        every { prefs.getLong("last_A", 0L) } returns System.currentTimeMillis() - 1000
        every { ctx.getSharedPreferences("note_assoc_llm_rate", any()) } returns prefs
        assert(extractor.isRateLimited("A"))
    }

    private fun note(id: String, title: String, content: String) =
        NoteEntity(id = id, title = title, content = content, createdAt = 0L, updatedAt = 0L)
}
