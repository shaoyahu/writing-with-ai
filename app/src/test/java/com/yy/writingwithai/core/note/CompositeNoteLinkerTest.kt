package com.yy.writingwithai.core.note

import com.yy.writingwithai.core.data.db.dao.NoteLinkDao
import com.yy.writingwithai.core.data.db.dao.RelatedRow
import com.yy.writingwithai.core.data.db.entity.LinkType
import com.yy.writingwithai.core.data.db.entity.NoteLinkEntity
import com.yy.writingwithai.core.note.impl.CompositeNoteLinker
import com.yy.writingwithai.core.note.impl.LlmNoteLinkExtractor
import com.yy.writingwithai.core.note.impl.LocalLinkCandidate
import com.yy.writingwithai.core.note.impl.LocalNoteLinker
import com.yy.writingwithai.core.note.impl.WikilinkIndexer
import com.yy.writingwithai.core.prefs.NoteAssociationSettingsStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * note-association · CompositeNoteLinker 单测。
 *
 * 纯 JVM mock test,不依赖 Room/ApplicationProvider。之前版本用 Room.inMemoryDatabaseBuilder +
 * ApplicationProvider,在 JUnit Platform 下没有 instrumentation,导致 testDebugUnitTest 失败。
 */
class CompositeNoteLinkerTest {
    private lateinit var noteLinkDao: NoteLinkDao
    private lateinit var local: LocalNoteLinker
    private lateinit var wiki: WikilinkIndexer
    private lateinit var llm: LlmNoteLinkExtractor
    private lateinit var settings: NoteAssociationSettingsStore
    private lateinit var linker: CompositeNoteLinker

    @BeforeEach
    fun setup() {
        noteLinkDao = mockk(relaxed = true)
        local = mockk()
        wiki = mockk()
        llm = mockk(relaxed = true)
        settings = mockk()
        every { settings.isEnabled() } returns false
        linker = CompositeNoteLinker(noteLinkDao, local, wiki, llm, settings)
    }

    @Test
    fun `recompute deletes old rows and writes local plus wikilink rows`() = runTest {
        val localCandidate = LocalLinkCandidate(
            dstNoteId = "n2",
            linkType = LinkType.TAG_OVERLAP,
            weight = 0.75f,
            createdAt = 1L,
            updatedAt = 1L,
            evidence = "{\"sharedTags\":[\"x\"]}"
        )
        val wikiRow = NoteLinkEntity(
            srcNoteId = "n1",
            dstNoteId = "n3",
            linkType = LinkType.WIKILINK,
            weight = 1.0f,
            createdAt = 2L,
            updatedAt = 2L
        )
        coEvery { local.compute("n1") } returns listOf(localCandidate)
        coEvery { wiki.index("n1") } returns listOf(wikiRow)

        linker.recomputeForNote("n1")

        coVerify { noteLinkDao.deleteBySrc("n1") }
        coVerify {
            noteLinkDao.upsertAll(
                match { rows ->
                    rows.size == 2 &&
                        rows.any { it.dstNoteId == "n2" && it.linkType == LinkType.TAG_OVERLAP } &&
                        rows.any { it.dstNoteId == "n3" && it.linkType == LinkType.WIKILINK }
                }
            )
        }
        coVerify(exactly = 0) { llm.extractAndPersist(any(), any()) }
    }

    @Test
    fun `recompute invokes llm extractor only when setting enabled`() = runTest {
        every { settings.isEnabled() } returns true
        coEvery { local.compute("n1") } returns emptyList()
        coEvery { wiki.index("n1") } returns emptyList()
        coEvery { llm.extractAndPersist("n1", any()) } returns 1

        linker.recomputeForNote("n1")

        coVerify { noteLinkDao.deleteBySrc("n1") }
        coVerify(exactly = 0) { noteLinkDao.upsertAll(any()) }
        coVerify { llm.extractAndPersist("n1", any()) }
    }

    @Test
    fun `getRelated maps RelatedRow to domain model`() = runTest {
        coEvery { noteLinkDao.getRelated("n1", 20) } returns listOf(
            RelatedRow(
                noteId = "n2",
                title = "Title",
                preview = "Preview",
                score = 1.5f,
                signals = "WIKILINK,TAG_OVERLAP"
            )
        )

        val related = linker.getRelated("n1", 20)

        assertEquals(1, related.size)
        assertEquals("n2", related[0].noteId)
        assertEquals("Title", related[0].title)
        assertEquals("Preview", related[0].preview)
        assertEquals(1.5f, related[0].score)
        assertTrue(LinkType.WIKILINK in related[0].signals)
        assertTrue(LinkType.TAG_OVERLAP in related[0].signals)
    }

    @Test
    fun `getBacklinks delegates and maps RelatedRow`() = runTest {
        coEvery { noteLinkDao.getBacklinks("n1", 10) } returns listOf(
            RelatedRow(
                noteId = "n0",
                title = "Backlink",
                preview = "Ref",
                score = 1.0f,
                signals = "WIKILINK"
            )
        )

        val backlinks = linker.getBacklinks("n1", 10)

        assertEquals(1, backlinks.size)
        assertEquals("n0", backlinks[0].noteId)
        assertTrue(LinkType.WIKILINK in backlinks[0].signals)
    }
}
