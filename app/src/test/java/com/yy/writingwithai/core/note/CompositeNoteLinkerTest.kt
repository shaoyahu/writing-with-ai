package com.yy.writingwithai.core.note

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yy.writingwithai.core.data.db.AppDatabase
import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.NoteTagDao
import com.yy.writingwithai.core.data.db.dao.NoteLinkDao
import com.yy.writingwithai.core.data.db.entity.NoteEntity
import com.yy.writingwithai.core.data.db.entity.NoteTagCrossRef
import com.yy.writingwithai.core.note.impl.CompositeNoteLinker
import com.yy.writingwithai.core.note.impl.LocalNoteLinker
import com.yy.writingwithai.core.note.impl.WikilinkIndexer
import com.yy.writingwithai.feature.settings.NoteAssociationSettings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CompositeNoteLinkerTest {

    private lateinit var db: AppDatabase
    private lateinit var noteDao: NoteDao
    private lateinit var noteTagDao: NoteTagDao
    private lateinit var noteLinkDao: NoteLinkDao
    private lateinit var linker: CompositeNoteLinker

    @BeforeEach
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).build()
        noteDao = db.noteDao()
        noteTagDao = db.noteTagDao()
        noteLinkDao = db.noteLinkDao()
        val local = LocalNoteLinker(noteDao, noteTagDao)
        val wiki = WikilinkIndexer(noteDao)
        val llm: com.yy.writingwithai.core.note.impl.LlmNoteLinkExtractor = mockk()
        every { llm.extractAndPersist(any(), any()) } returns Unit
        val settings: NoteAssociationSettings = mockk()
        every { settings.isEnabled() } returns false
        linker = CompositeNoteLinker(noteLinkDao, local, wiki, llm, settings)
    }

    @AfterEach
    fun teardown() {
        db.close()
    }

    @Test
    fun `shared tag → related`() = runTest {
        seed("A", listOf("shared"))
        seed("B", listOf("shared"))
        linker.recomputeForNote("A")
        assertEquals(1, linker.getRelated("A").size)
        assertEquals("B", linker.getRelated("A").first().noteId)
    }

    @Test
    fun `disjoint tags → empty`() = runTest {
        seed("A", listOf("x"))
        seed("B", listOf("y"))
        linker.recomputeForNote("A")
        assertTrue(linker.getRelated("A").isEmpty())
    }

    @Test
    fun `recompute idempotent`() = runTest {
        seed("A", listOf("t"))
        seed("B", listOf("t"))
        linker.recomputeForNote("A")
        val c1 = noteLinkDao.countForNote("A")
        linker.recomputeForNote("A")
        assertEquals(c1, noteLinkDao.countForNote("A"))
    }

    private suspend fun seed(id: String, tags: List<String>) {
        noteDao.upsert(
            NoteEntity(
                id = id,
                title = "T-$id",
                content = "C-$id",
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        tags.forEach { noteTagDao.add(NoteTagCrossRef(id, it)) }
    }
}
