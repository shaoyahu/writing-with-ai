package com.yy.writingwithai.core.note.graph

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yy.writingwithai.core.data.db.AppDatabase
import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.data.db.dao.NoteLinkDao
import com.yy.writingwithai.core.data.db.dao.entity.NoteEntityDao
import com.yy.writingwithai.core.data.db.entity.LinkType
import com.yy.writingwithai.core.data.db.entity.NoteEntity
import com.yy.writingwithai.core.data.db.entity.NoteLinkEntity
import com.yy.writingwithai.core.data.db.entity.entity.NoteEntityRow
import com.yy.writingwithai.core.note.NoteLinker
import com.yy.writingwithai.core.note.RelatedNote
import com.yy.writingwithai.core.prefs.NoteAssociationSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * note-graph-view · GraphDataLoader 单测(tasks §1.6)。
 *
 * 覆盖:
 * - 空关联 0 节点
 * - 仅 1-hop 5 节点 (5 个 score 不同,排序正确)
 * - 1+2-hop 超 cap → truncated=true
 * - entity chip 上限 8
 */
@RunWith(RobolectricTestRunner::class)
class GraphDataLoaderTest {

    private lateinit var db: AppDatabase
    private lateinit var noteDao: NoteDao
    private lateinit var noteLinkDao: NoteLinkDao
    private lateinit var noteEntityDao: NoteEntityDao
    private lateinit var fakeLinker: FakeNoteLinker
    private lateinit var settings: TestSettings

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        noteDao = db.noteDao()
        noteLinkDao = db.noteLinkDao()
        noteEntityDao = db.noteEntityDao()
        fakeLinker = FakeNoteLinker()
        settings = TestSettings(ctx.getSharedPreferences("graph_test_settings", android.content.Context.MODE_PRIVATE))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun emptyRelations_yields_centerOnly_snapshot() = runBlocking {
        noteDao.upsert(note("c", "Center", "body"))
        val loader = loader()
        val snap = loader.load("c")

        // 1 center only, no edges, no chips
        assertEquals(1, snap.nodes.size)
        assertEquals(0, snap.hopLevelCount(1))
        assertEquals(0, snap.hopLevelCount(2))
        assertTrue(snap.edges.isEmpty())
        assertTrue(snap.entityChips.isEmpty())
        // no candidates → not truncated
        assertFalse(snap.truncated)
    }

    @Test
    fun onlyOneHop_fiveNodes_ordersByScoreDesc() = runBlocking {
        noteDao.upsert(note("c", "Center", "body"))
        // 5 个 1-hop,score 不同
        val hops = listOf(
            Triple("h1", "Hop-1", 0.3f),
            Triple("h2", "Hop-2", 0.8f),
            Triple("h3", "Hop-3", 0.5f),
            Triple("h4", "Hop-4", 0.1f),
            Triple("h5", "Hop-5", 0.95f)
        )
        for ((id, title, s) in hops) {
            noteDao.upsert(note(id, title, "body"))
            fakeLinker.addRelated("c", RelatedNote(id, title, "prev", s, setOf(LinkType.WIKILINK)))
        }
        val loader = loader()
        val snap = loader.load("c")

        // 1 center + 5 hop1 = 6
        assertEquals(6, snap.nodes.size)
        assertEquals(0, snap.hopLevelCount(2))
        // 5 条中心↔hop1 边
        assertEquals(5, snap.edges.size)
        // score 降序
        val hop1Scores = snap.nodes.filter { it.hopLevel == 1 }.map { it.score }
        assertEquals(hop1Scores.sortedDescending(), hop1Scores)
        assertFalse(snap.truncated)
    }

    @Test
    fun oneAndTwoHop_beyondCap_marksTruncated() = runBlocking {
        noteDao.upsert(note("c", "Center", "body"))
        // 10 个 1-hop(超 9=hop1Limit=maxNodes-1-hop2Limit=50-1-20=29,但 10 个以内;超 cap 需要 2-hop 多)
        for (i in 1..10) {
            val id = "h$i"
            noteDao.upsert(note(id, "Hop-$i", "body"))
            fakeLinker.addRelated("c", RelatedNote(id, "Hop-$i", "p", 0.5f, setOf(LinkType.WIKILINK)))
            // 给 hop1 注入 4 个 2-hop 邻居(都是独立 noteId)
            for (j in 1..4) {
                val dh = "${id}_$j"
                noteDao.upsert(note(dh, "Hop-$i-$j", "body"))
                // 这里只走 dao path —— 为简化 2-hop 走 noteLinkDao.getRelated 而非 linker
            }
        }
        // 直接往 note_links 表里塞 2-hop 边,确保 dao.getRelated 能返回
        for (i in 1..10) {
            for (j in 1..4) {
                val hop1Id = "h$i"
                val hop2Id = "${hop1Id}_$j"
                runBlocking {
                    noteLinkDao.upsert(
                        NoteLinkEntity(
                            srcNoteId = hop1Id,
                            dstNoteId = hop2Id,
                            linkType = LinkType.ENTITY_HIT,
                            weight = 1f,
                            createdAt = 0L,
                            updatedAt = 0L
                        )
                    )
                }
            }
        }
        val loader = loader()
        val snap = loader.load("c", config = LoaderConfig(hop1Limit = 30, hop2Limit = 20))

        // 1 center (hopLevel=0) + 10 hop1 + 至少 1 hop2 (40 candidate 但 cap = 1+10+20=31)
        assertEquals(0, snap.nodes.first().hopLevel)
        assertTrue(snap.nodes.size <= 51) // maxNodes + 1 中心预留缓冲
        // 由于 2-hop 总候选 (10 × 4 = 40) > hop2Limit 20 → truncated = true
        assertTrue(snap.truncated)
    }

    @Test
    fun entityChipLimit_cappedTo8() = runBlocking {
        noteDao.upsert(note("c", "Center", "body"))
        // 塞 12 个 entity,只应保留前 8
        val rows = (1..12).map {
            NoteEntityRow(
                noteId = "c",
                entityKey = "key$it",
                entityType = com.yy.writingwithai.core.note.entity.EntityType.WORK,
                surfaceForm = "Entity-$it",
                spanStart = 0,
                spanEnd = 0,
                lastExtractedAt = 0L,
                source = "TEST"
            )
        }
        noteEntityDao.upsertAll(rows)

        val loader = loader()
        val snap = loader.load("c")

        assertEquals(8, snap.entityChips.size)
        assertTrue(snap.entityChips.all { it.startsWith("Entity-") })
    }

    // ---- helpers ----

    private fun loader(): GraphDataLoader = GraphDataLoader(
        noteLinker = fakeLinker,
        noteLinkDao = noteLinkDao,
        noteDao = noteDao,
        noteEntityDao = noteEntityDao,
        assocSettings = settings
    )

    private fun GraphSnapshot.hopLevelCount(level: Int): Int = nodes.count { it.hopLevel == level }

    private fun note(id: String, title: String, content: String) = NoteEntity(
        id = id,
        title = title,
        content = content,
        createdAt = 0L,
        updatedAt = 0L
    )

    /** 测试专用:返回固定 RelatedNote 列表,不依赖 DAO。 */
    private class FakeNoteLinker : NoteLinker {
        private val related: MutableMap<String, MutableList<RelatedNote>> = mutableMapOf()
        private val backlinks: MutableMap<String, MutableList<RelatedNote>> = mutableMapOf()

        fun addRelated(src: String, n: RelatedNote) {
            related.getOrPut(src) { mutableListOf() }.add(n)
        }

        override suspend fun recomputeForNote(noteId: String) {}
        override suspend fun recomputeAll(): Int = 0
        override suspend fun getRelated(noteId: String, limit: Int): List<RelatedNote> =
            related[noteId].orEmpty().sortedByDescending { it.score }.take(limit)

        override suspend fun getBacklinks(noteId: String, limit: Int): List<RelatedNote> =
            backlinks[noteId].orEmpty().sortedByDescending { it.score }.take(limit)
    }

    /** 测试专用:settings store 仅实现 threshold() 字段(其它走 default false / 0.10f)。 */
    private class TestSettings(
        private val prefs: android.content.SharedPreferences
    ) : NoteAssociationSettingsStore {
        override fun isEnabled(): Boolean = prefs.getBoolean("enabled", false)
        override fun setEnabled(value: Boolean) {
            prefs.edit().putBoolean("enabled", value).apply()
        }
        override fun observeEnabled(): Flow<Boolean> = flowOf(isEnabled())
        override fun threshold(): Float = prefs.getFloat("threshold", 0.10f)
        override fun setThreshold(value: Float) {
            prefs.edit().putFloat("threshold", value).apply()
        }
        override fun observeThreshold(): Flow<Float> = flowOf(threshold())
        override fun pauseBackfill(): Boolean = prefs.getBoolean("pauseBackfill", false)
        override fun setPauseBackfill(value: Boolean) {
            prefs.edit().putBoolean("pauseBackfill", value).apply()
        }
        override fun observePauseBackfill(): Flow<Boolean> = flowOf(pauseBackfill())
    }
}
