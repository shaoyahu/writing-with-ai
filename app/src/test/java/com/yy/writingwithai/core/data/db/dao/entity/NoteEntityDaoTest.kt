package com.yy.writingwithai.core.data.db.dao.entity

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yy.writingwithai.core.data.db.AppDatabase
import com.yy.writingwithai.core.data.db.entity.entity.NoteEntityRow
import com.yy.writingwithai.core.note.entity.EntityType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * entity-extraction-polish §6.1:NoteEntityDao 实跑 Robolectric in-memory Room 测试。
 *
 * 覆盖核心契约:
 * - upsertAll REPLACE(同 (noteId, entityKey) 二次插入覆盖)
 * - getByNoteId 仅返该 note 的 entity
 * - deleteByNoteId 仅删该 note 的 entity
 * - querySharedEntityHits 跨 note 共享 entity 命中
 * - queryAllEntityKeys DISTINCT + LIMIT/OFFSET
 *
 * 注意:用 JUnit4 + RobolectricTestRunner，跟仓库既有 [NoteRepositoryDeleteOrderTest] 一致
 * (JUnit5 RobolectricExtension 需额外依赖)。
 */
@RunWith(RobolectricTestRunner::class)
class NoteEntityDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: NoteEntityDao
    private lateinit var noteDao: com.yy.writingwithai.core.data.db.NoteDao

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.noteEntityDao()
        noteDao = db.noteDao()
        // note_entities.noteId FK → notes.id，先插父表
        runBlocking {
            listOf("n1", "n2", "n3").forEach { id ->
                noteDao.upsert(
                    com.yy.writingwithai.core.data.db.entity.NoteEntity(
                        id = id,
                        title = "title-$id",
                        content = "content-$id",
                        createdAt = 0L,
                        updatedAt = 0L
                    )
                )
            }
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(noteId: String, key: String, surface: String = key, type: EntityType = EntityType.PERSON) =
        NoteEntityRow(
            noteId = noteId,
            entityType = type,
            entityKey = key,
            surfaceForm = surface,
            spanStart = 0,
            spanEnd = surface.length,
            lastExtractedAt = 0L
        )

    @Test
    fun upsertAll_REPLACE_overwrites_same_noteId_entityKey_composite() = runBlocking {
        dao.upsertAll(listOf(row("n1", "person::alice", surface = "Alice v1")))
        dao.upsertAll(listOf(row("n1", "person::alice", surface = "Alice v2")))

        val rows = dao.getByNoteId("n1")
        assertEquals(1, rows.size)
        assertEquals("Alice v2", rows[0].surfaceForm)
    }

    @Test
    fun getByNoteId_returns_only_that_notes_entities() = runBlocking {
        dao.upsertAll(
            listOf(
                row("n1", "person::alice"),
                row("n2", "person::bob"),
                row("n1", "work::foo")
            )
        )

        val n1Rows = dao.getByNoteId("n1")
        assertEquals(2, n1Rows.size)
        assertTrue(n1Rows.all { it.noteId == "n1" })
    }

    @Test
    fun deleteByNoteId_removes_only_that_notes_entities() = runBlocking {
        dao.upsertAll(
            listOf(
                row("n1", "person::alice"),
                row("n2", "person::bob")
            )
        )

        dao.deleteByNoteId("n1")

        assertEquals(0, dao.getByNoteId("n1").size)
        assertEquals(1, dao.getByNoteId("n2").size)
    }

    @Test
    fun querySharedEntityHits_returns_notes_sharing_an_entityKey() = runBlocking {
        dao.upsertAll(
            listOf(
                row("n1", "work::sanguo", type = EntityType.WORK),
                row("n2", "work::sanguo", type = EntityType.WORK),
                row("n3", "work::honglou", type = EntityType.WORK)
            )
        )

        val hits = dao.querySharedEntityHits("n1", limit = 10)
        // n1 与 n2 共享 work::sanguo,n3 不共享
        assertEquals(1, hits.size)
        assertEquals("n2", hits[0].noteId)
        assertEquals("work::sanguo", hits[0].entityKey)
    }

    @Test
    fun queryAllEntityKeys_DISTINCT_with_LIMIT_and_OFFSET() = runBlocking {
        dao.upsertAll(
            listOf(
                row("n1", "person::alice"),
                // 重复
                row("n1", "person::alice"),
                row("n2", "person::alice"),
                row("n2", "person::bob"),
                row("n3", "work::foo")
            )
        )

        val page1 = dao.queryAllEntityKeys(limit = 2, offset = 0)
        // DISTINCT 后共 3 个 key
        assertEquals(2, page1.size)
        val page2 = dao.queryAllEntityKeys(limit = 2, offset = 2)
        assertEquals(1, page2.size)
        // 分页不重叠
        assertEquals(3, (page1 + page2).toSet().size)
    }
}
