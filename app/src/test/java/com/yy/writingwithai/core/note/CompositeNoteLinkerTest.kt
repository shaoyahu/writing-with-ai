package com.yy.writingwithai.core.note

import com.yy.writingwithai.core.data.db.AppDatabase
import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.dao.NoteLinkDao
import com.yy.writingwithai.core.data.db.dao.RelatedRow
import com.yy.writingwithai.core.data.db.entity.LinkType
import com.yy.writingwithai.core.data.db.entity.NoteLinkEntity
import com.yy.writingwithai.core.note.impl.CompositeNoteLinker
import com.yy.writingwithai.core.note.impl.EntityBacklinker
import com.yy.writingwithai.core.note.impl.LocalLinkCandidate
import com.yy.writingwithai.core.note.impl.LocalNoteLinker
import com.yy.writingwithai.core.note.impl.SemanticNoteLinker
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
    private lateinit var noteDao: NoteDao
    private lateinit var local: LocalNoteLinker
    private lateinit var wiki: WikilinkIndexer
    private lateinit var entity: EntityBacklinker
    private lateinit var llm: SemanticNoteLinker
    private lateinit var settings: NoteAssociationSettingsStore
    private lateinit var db: AppDatabase
    private lateinit var linker: CompositeNoteLinker

    @BeforeEach
    fun setup() {
        noteLinkDao = mockk(relaxed = true)
        noteDao = mockk()
        local = mockk()
        wiki = mockk()
        entity = mockk()
        llm = mockk(relaxed = true)
        settings = mockk()
        db = mockk(relaxed = true)
        every { settings.isEnabled() } returns false
        // entity-extraction-polish §2.2:CompositeNoteLinker 现在调 settings.threshold() 传 DAO / NoteLinkCap。
        every { settings.threshold() } returns 0.10f
        coEvery { entity.compute(any()) } returns emptyList()
        // 默认:每个 noteId 的 local/wiki/entity 都返空,这样 recomputeForNote 安全 noop。
        coEvery { local.compute(any()) } returns emptyList()
        coEvery { wiki.index(any()) } returns emptyList()
        // fix-2026-06-30-full-review-r1 CRITICAL C1:delete + upsert 现在走 db.withTransaction。
        // withTransaction 是 Room 扩展,单元测试中直接同步执行 block 即可。
        coEvery { db.withTransaction<Boolean>(any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val block = args[0] as suspend () -> Unit
            kotlinx.coroutines.runBlocking { block() }
            true
        }
        linker = CompositeNoteLinker(db, noteLinkDao, noteDao, local, wiki, entity, llm, settings)
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
        coEvery { entity.compute("n1") } returns emptyList()

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
        coEvery { entity.compute("n1") } returns emptyList()
        coEvery { llm.extractAndPersist("n1", any()) } returns 1

        linker.recomputeForNote("n1")

        coVerify { noteLinkDao.deleteBySrc("n1") }
        coVerify(exactly = 0) { noteLinkDao.upsertAll(any()) }
        coVerify { llm.extractAndPersist("n1", any()) }
    }

    @Test
    fun `getRelated maps RelatedRow to domain model`() = runTest {
        coEvery { noteLinkDao.getRelated("n1", 20, any()) } returns listOf(
            RelatedRow(
                noteId = "n2",
                title = "Title",
                preview = "Preview",
                score = 1.5f,
                signals = "WIKILINK,TAG_OVERLAP",
                evidence = "{\"sharedEntities\":[\"x\"]}"
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
        coEvery { noteLinkDao.getBacklinks("n1", 10, any()) } returns listOf(
            RelatedRow(
                noteId = "n0",
                title = "Backlink",
                preview = "Ref",
                score = 1.0f,
                signals = "WIKILINK",
                evidence = null
            )
        )

        val backlinks = linker.getBacklinks("n1", 10)

        assertEquals(1, backlinks.size)
        assertEquals("n0", backlinks[0].noteId)
        assertTrue(LinkType.WIKILINK in backlinks[0].signals)
    }

    /**
     * R3 fix M8 回归:recomputeAll 不再是 `return 0` 的死 SPI。
     * 期望:遍历 noteDao.getAllIds() 返回的每个 id,逐条调 recomputeForNote,
     * 返回成功处理的 count。
     */
    @Test
    fun `recomputeAll iterates all ids and returns success count`() = runTest {
        coEvery { noteDao.getAllIds() } returns listOf("n1", "n2", "n3")
        // setup() 已默认 local/wiki/entity 返回空行,recomputeForNote 安全 noop。

        val processed = linker.recomputeAll()

        assertEquals(3, processed)
        coVerify(exactly = 1) { noteDao.getAllIds() }
        coVerify(exactly = 1) { noteLinkDao.deleteBySrc("n1") }
        coVerify(exactly = 1) { noteLinkDao.deleteBySrc("n2") }
        coVerify(exactly = 1) { noteLinkDao.deleteBySrc("n3") }
    }

    /**
     * R3 fix M8 + H8 教训:单条 poisoned note 不应 abort 整批。
     * 期望:n2 抛异常 → n1 成功 + n2 跳过 + n3 仍继续,返回 2。
     */
    @Test
    fun `recomputeAll skips poisoned note and continues`() = runTest {
        coEvery { noteDao.getAllIds() } returns listOf("n1", "n2", "n3")
        coEvery { noteLinkDao.deleteBySrc("n2") } throws RuntimeException("simulated UNIQUE")

        val processed = linker.recomputeAll()

        assertEquals(2, processed, "n2 失败应被跳过,n1/n3 仍计入")
        coVerify(exactly = 1) { noteLinkDao.deleteBySrc("n1") }
        coVerify(exactly = 1) { noteLinkDao.deleteBySrc("n2") }
        coVerify(exactly = 1) { noteLinkDao.deleteBySrc("n3") }
    }
}
