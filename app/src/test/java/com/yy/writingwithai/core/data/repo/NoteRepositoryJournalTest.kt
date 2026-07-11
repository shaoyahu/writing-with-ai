package com.yy.writingwithai.core.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yy.writingwithai.core.data.db.AppDatabase
import com.yy.writingwithai.core.media.AttachmentStore
import com.yy.writingwithai.core.note.NoteLinker
import com.yy.writingwithai.core.widget.QuickNoteWidgetUpdater
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * morning-freewrite §4.5:验证 [NoteRepository.createJournalEntry] 落库 journal tag
 * + 触发 widget 刷新 + 写入 note 行。失败兜底(fallback=true)走原文路径走同方法验证,
 * 字段映射(op/at)在 [MorningFreewriteViewModelTest] VM 层测。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NoteRepositoryJournalTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var attachmentStore: AttachmentStore
    private lateinit var noteLinker: NoteLinker
    private lateinit var widgetUpdater: QuickNoteWidgetUpdater
    private lateinit var repo: NoteRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        attachmentStore = mockk(relaxed = true)
        noteLinker = mockk(relaxed = true)
        widgetUpdater = mockk(relaxed = false)
        coEvery { widgetUpdater.updateAll(any()) } returns Unit
        repo = NoteRepository(
            context = context,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            db = db,
            noteDao = db.noteDao(),
            noteTagDao = db.noteTagDao(),
            noteAttachmentDao = db.noteAttachmentDao(),
            widgetUpdater = widgetUpdater,
            noteLinker = noteLinker,
            attachmentStore = attachmentStore,
            aiHistoryDao = db.aiHistoryDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `createJournalEntry writes note + journal tag + triggers widget update (happy path)`() = runBlocking {
        repo.createJournalEntry(
            noteId = "journal-1",
            title = "2026-07-10",
            content = "polished body",
            lastAiOp = "organize",
            lastAiAt = 1_700_000_000_000L
        )

        // 1. note 行存在
        val note = db.noteDao().getById("journal-1")
        assertNotNull("journal note must be persisted", note)
        assertEquals("2026-07-10", note!!.title)
        assertEquals("polished body", note.content)
        assertEquals("organize", note.lastAiOp)
        assertEquals(1_700_000_000_000L, note.lastAiAt)

        // 2. note_tags 写入 journal
        val tags = withTimeoutOrNull(2_000) {
            db.noteTagDao().observeTagsFor("journal-1").first { it.isNotEmpty() }
        }
        assertEquals(listOf("journal"), tags)

        // 3. widget 刷新被触发一次
        coVerify(exactly = 1) { widgetUpdater.updateAll(context) }
    }

    @Test
    fun `createJournalEntry with null ai metadata still writes journal tag (fallback path)`() = runBlocking {
        repo.createJournalEntry(
            noteId = "journal-2",
            title = "2026-07-10",
            content = "raw body when AI failed",
            lastAiOp = null,
            lastAiAt = null
        )

        val note = db.noteDao().getById("journal-2")
        assertNotNull(note)
        assertEquals(null, note!!.lastAiOp)
        assertEquals(null, note.lastAiAt)

        val tags = withTimeoutOrNull(2_000) {
            db.noteTagDao().observeTagsFor("journal-2").first { it.isNotEmpty() }
        }
        assertEquals(listOf("journal"), tags)
    }
}
