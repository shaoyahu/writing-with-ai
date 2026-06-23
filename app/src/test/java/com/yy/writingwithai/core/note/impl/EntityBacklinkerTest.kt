package com.yy.writingwithai.core.note.impl

import com.yy.writingwithai.core.data.db.dao.NoteLinkDao
import com.yy.writingwithai.core.data.db.dao.entity.EntityAliasDao
import com.yy.writingwithai.core.data.db.dao.entity.NoteEntityDao
import com.yy.writingwithai.core.data.db.entity.entity.EntityAliasRow
import com.yy.writingwithai.core.data.db.entity.entity.NoteEntityRow
import com.yy.writingwithai.core.note.entity.EntityType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * entity-extraction-association · EntityBacklinker 单测。
 *
 * 覆盖:
 * - 共享实体命中 + 聚合 dstNoteId
 * - alias canonical 展开
 * - 非 expandedKey 过滤
 * - 上限 MAX_HITS=66
 */
class EntityBacklinkerTest {
    private lateinit var entityDao: NoteEntityDao
    private lateinit var aliasDao: EntityAliasDao
    private lateinit var linkDao: NoteLinkDao
    private lateinit var linker: EntityBacklinker

    @BeforeEach
    fun setup() {
        entityDao = mockk()
        aliasDao = mockk()
        linkDao = mockk(relaxed = true)
        linker = EntityBacklinker(entityDao, aliasDao, linkDao)
    }

    @Test
    fun `compute returns empty when self has no entities`() = runTest {
        coEvery { entityDao.getByNoteId("src") } returns emptyList()

        val result = linker.compute("src")

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { aliasDao.findByAliasKeys(any()) }
    }

    @Test
    fun `compute returns ENTITY_HIT rows aggregated by dst with alias expansion`() = runTest {
        coEvery { entityDao.getByNoteId("src") } returns listOf(
            row("src", EntityType.PERSON, "person::xiaoming"),
            row("src", EntityType.PERSON, "person::xiaom")
        )
        coEvery { aliasDao.findByAliasKeys(any()) } returns listOf(
            EntityAliasRow(EntityType.PERSON, "person::xiaom", "person::xiaoming")
        )
        coEvery { entityDao.querySharedEntityHits("src", any()) } returns listOf(
            row("n2", EntityType.PERSON, "person::xiaoming"),
            row("n3", EntityType.PERSON, "person::xiaom"),
            row("n4", EntityType.PERSON, "person::other")
        )

        val result = linker.compute("src")

        assertEquals(2, result.size)
        assertTrue(result.all { it.linkType.name == "ENTITY_HIT" })
        val dsts = result.map { it.dstNoteId }.toSet()
        assertTrue(dsts.contains("n2"))
        assertTrue(dsts.contains("n3"))
        assertTrue(!dsts.contains("n4"))
    }

    @Test
    fun `compute evidence JSON contains sharedEntities array`() = runTest {
        coEvery { entityDao.getByNoteId("src") } returns listOf(row("src", EntityType.WORK, "work::sanguo"))
        coEvery { aliasDao.findByAliasKeys(any()) } returns emptyList()
        coEvery { entityDao.querySharedEntityHits("src", any()) } returns listOf(
            row("n2", EntityType.WORK, "work::sanguo")
        )

        val result = linker.compute("src")

        assertEquals(1, result.size)
        val ev = result[0].evidence ?: ""
        assertTrue(ev.contains("\"sharedEntities\""))
        assertTrue(ev.contains("work::sanguo"))
    }

    @Test
    fun `compute caps at MAX_HITS 66`() = runTest {
        coEvery { entityDao.getByNoteId("src") } returns listOf(row("src", EntityType.PERSON, "person::a"))
        coEvery { aliasDao.findByAliasKeys(any()) } returns emptyList()
        val hits = (0 until 100).map { row("n$it", EntityType.PERSON, "person::a") }
        coEvery { entityDao.querySharedEntityHits("src", any()) } returns hits

        val result = linker.compute("src")

        assertEquals(EntityBacklinker.MAX_HITS, result.size)
    }

    private fun row(noteId: String, type: EntityType, key: String) = NoteEntityRow(
        noteId = noteId,
        entityType = type,
        entityKey = key,
        surfaceForm = key.substringAfter("::"),
        spanStart = 0,
        spanEnd = 0,
        lastExtractedAt = 0L
    )
}
