package com.yy.writingwithai.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yy.writingwithai.core.data.db.AppDatabase
import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.entity.LinkType
import com.yy.writingwithai.core.data.db.entity.NoteEntity
import com.yy.writingwithai.core.data.db.entity.NoteLinkEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * entity-extraction-polish §6.3:NoteLinkDao 实跑 Robolectric in-memory Room 测试。
 *
 * 覆盖:
 * - getRelated threshold 0.10 默认 → 含低分
 * - getBacklinks threshold 0.10 → 同样阈值过滤
 * - threshold 0.70 排除低分
 * - ENTITY_HIT 权重 1.50 进 score
 */
@RunWith(RobolectricTestRunner::class)
class NoteLinkDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var noteLinkDao: NoteLinkDao
    private lateinit var noteDao: NoteDao

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        noteLinkDao = db.noteLinkDao()
        noteDao = db.noteDao()
        // 插入 3 个 note + 多种 linkType 的边
        runBlocking {
            noteDao.upsert(note("n1", "Hello三国", "content1"))
            noteDao.upsert(note("n2", "World三国", "content2"))
            noteDao.upsert(note("n3", "Foo", "content3"))
            // n1→n2 ENTITY_HIT weight 1.0 → score = 1.5
            noteLinkDao.upsert(
                NoteLinkEntity(
                    srcNoteId = "n1",
                    dstNoteId = "n2",
                    linkType = LinkType.ENTITY_HIT,
                    weight = 1f,
                    createdAt = 1L,
                    updatedAt = 1L,
                    evidence = null
                )
            )
            // n1→n3 TAG_OVERLAP jaccard=0.4 → score = 1.5*0.4 = 0.6
            noteLinkDao.upsert(
                NoteLinkEntity(
                    srcNoteId = "n1",
                    dstNoteId = "n3",
                    linkType = LinkType.TAG_OVERLAP,
                    weight = 0.4f,
                    createdAt = 1L,
                    updatedAt = 1L,
                    evidence = null
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun note(id: String, title: String, content: String) = NoteEntity(
        id = id,
        title = title,
        content = content,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun getRelated_threshold_0_10_includes_both_rows() = runBlocking {
        val rows = noteLinkDao.getRelated("n1", limit = 10, threshold = 0.10)
        assertEquals(2, rows.size)
        // ENTITY_HIT 排前(score 1.5)
        assertEquals("n2", rows[0].noteId)
        assertEquals("n3", rows[1].noteId)
        assertTrue(rows[0].score > rows[1].score)
    }

    @Test
    fun getBacklinks_threshold_0_10_includes_both_rows() = runBlocking {
        val rows = noteLinkDao.getBacklinks("n2", limit = 10, threshold = 0.10)
        // n1→n2 边,从 n2 反查 → 仅 n1
        assertEquals(1, rows.size)
        assertEquals("n1", rows[0].noteId)
    }

    @Test
    fun threshold_0_70_filters_out_low_score_TAG_OVERLAP_row() = runBlocking {
        val rows = noteLinkDao.getRelated("n1", limit = 10, threshold = 0.70)
        // n2 score=1.5 保留,n3 score=0.6 被过滤
        assertEquals(1, rows.size)
        assertEquals("n2", rows[0].noteId)
    }

    @Test
    fun ENTITY_HIT_contributes_1_50_to_aggregated_score() = runBlocking {
        val rows = noteLinkDao.getRelated("n1", limit = 10, threshold = 0.0)
        val entityHitRow = rows.first { it.noteId == "n2" }
        // ENTITY_HIT × 1.50 × max(weight=1.0) = 1.50
        assertEquals(1.5f, entityHitRow.score, 0.001f)
    }
}
