package com.yy.writingwithai.core.note

import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.NoteTagDao
import com.yy.writingwithai.core.data.db.entity.LinkType
import com.yy.writingwithai.core.data.db.entity.NoteEntity
import com.yy.writingwithai.core.data.db.entity.NoteTagCrossRef
import com.yy.writingwithai.core.note.impl.LocalNoteLinker
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalNoteLinkerTest {

    private val noteDao: NoteDao = mockk()
    private val noteTagDao: NoteTagDao = mockk()
    private val linker = LocalNoteLinker(noteDao, noteTagDao)

    @Test
    fun `jaccard = 0_5`() = runTest {
        coEvery { noteDao.getById("A") } returns note("A", "t", "b")
        coEvery { noteDao.search(any()) } returns flowOf(emptyList())
        coEvery { noteTagDao.observeTagsFor("A") } returns flowOf(listOf("x", "y", "z"))
        coEvery { noteTagDao.observeAllCrossRefs() } returns flowOf(
            listOf(NoteTagCrossRef("B", "y"), NoteTagCrossRef("B", "z"), NoteTagCrossRef("B", "w"))
        )
        val r = linker.compute("A")
        val t = r.first { it.linkType == LinkType.TAG_OVERLAP }
        assertEquals(0.5f, t.weight, 0.01f)
    }

    @Test
    fun `jaccard = 1_0`() = runTest {
        coEvery { noteDao.getById("A") } returns note("A", "t", "b")
        coEvery { noteDao.search(any()) } returns flowOf(emptyList())
        coEvery { noteTagDao.observeTagsFor("A") } returns flowOf(listOf("x", "y"))
        coEvery { noteTagDao.observeAllCrossRefs() } returns flowOf(
            listOf(NoteTagCrossRef("B", "x"), NoteTagCrossRef("B", "y"))
        )
        val r = linker.compute("A")
        val t = r.first { it.linkType == LinkType.TAG_OVERLAP }
        assertEquals(1.0f, t.weight, 0.01f)
    }

    @Test
    fun `disjoint tags no candidate`() = runTest {
        coEvery { noteDao.getById("A") } returns note("A", "t", "b")
        coEvery { noteDao.search(any()) } returns flowOf(emptyList())
        coEvery { noteTagDao.observeTagsFor("A") } returns flowOf(listOf("x"))
        coEvery { noteTagDao.observeAllCrossRefs() } returns flowOf(listOf(NoteTagCrossRef("B", "z")))
        assertTrue(linker.compute("A").none { it.linkType == LinkType.TAG_OVERLAP })
    }

    @Test
    fun `keywordOverlapWeight`() {
        val w = LocalNoteLinker.keywordOverlapWeight("hello world", "hello there world today")
        assertEquals(1.0f, w, 0.01f)
    }

    @Test
    fun `keywordOverlapWeight partial`() {
        val w = LocalNoteLinker.keywordOverlapWeight("hello world kotlin", "hello there")
        assertTrue(w > 0f && w < 1f)
    }

    private fun note(id: String, title: String, content: String) =
        NoteEntity(id = id, title = title, content = content, createdAt = 0L, updatedAt = 0L)
}
