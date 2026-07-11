package com.yy.writingwithai.core.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yy.writingwithai.core.data.db.entity.AiHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * fix-review-r1 F6:AiHistoryDao 实跑 instrumented test。
 * spec:openspec/changes/fix-review-r1-2026-07-11/proposal.md "fix HIGH H2"
 */
@RunWith(AndroidJUnit4::class)
class AiHistoryDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: AiHistoryDao
    private val MS_PER_DAY = 86_400_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.aiHistoryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(
        id: String,
        noteId: String? = null,
        op: String = "EXPAND",
        inputTokens: Int = 100,
        outputTokens: Int = 50,
        createdAt: Long,
        error: String? = null
    ) = AiHistoryEntity(
        id = id,
        noteId = noteId,
        providerId = "deepseek",
        model = "deepseek-chat",
        op = op,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        totalTokens = inputTokens + outputTokens,
        durationMs = 100L,
        createdAt = createdAt,
        inputSnapshot = "",
        outputSnapshot = "",
        truncated = false,
        error = error
    )

    @Test
    fun aggregateByDay_bucketsCorrectly() = runBlocking {
        val day1 = 0L
        val day2 = MS_PER_DAY
        val day3 = 2 * MS_PER_DAY
        dao.insert(row("a1", createdAt = day1 + 1_000L, inputTokens = 10, outputTokens = 5))
        dao.insert(row("a2", createdAt = day1 + 2_000L, inputTokens = 20, outputTokens = 10))
        dao.insert(row("a3", createdAt = day2 + 500L, inputTokens = 30, outputTokens = 15))
        // 失败行不进聚合。
        dao.insert(row("a4", createdAt = day3 + 100L, inputTokens = 999, error = "boom"))

        val buckets = dao.aggregateByDay(day1, day3 + MS_PER_DAY).first()
        assertEquals(2, buckets.size)

        val b1 = buckets[0]
        assertEquals(day1 / MS_PER_DAY, b1.dayBucket)
        assertEquals(30, b1.sumInput)
        assertEquals(15, b1.sumOutput)
        assertEquals(45, b1.sumTotal)
        assertEquals(2, b1.count)

        val b2 = buckets[1]
        assertEquals(day2 / MS_PER_DAY, b2.dayBucket)
        assertEquals(30, b2.sumInput)
        assertEquals(15, b2.sumOutput)
        assertEquals(1, b2.count)

        for (b in buckets) assertTrue(b.sumTotal > 0)
    }

    @Test
    fun deleteByNoteId_removesOrphans() = runBlocking {
        val noteA = "note-A"
        val noteB = "note-B"
        dao.insert(row("a1", noteId = noteA, createdAt = 1L, inputTokens = 100))
        dao.insert(row("a2", noteId = noteA, createdAt = 2L, inputTokens = 200))
        dao.insert(row("b1", noteId = noteB, createdAt = 3L, inputTokens = 50))

        val before = dao.observeTotalTokens().first()
        assertEquals(350L, before)

        val deleted = dao.deleteByNoteId(noteA)
        assertEquals(2, deleted)

        val remaining = dao.observeAll().first()
        assertEquals(1, remaining.size)
        assertEquals("b1", remaining[0].id)
        assertEquals(noteB, remaining[0].noteId)

        val after = dao.observeTotalTokens().first()
        assertEquals(50L, after)

        // observeByNoteId 不再发射孤儿。
        val orphans = dao.observeByNoteId(noteA).first()
        assertTrue(orphans.isEmpty())
    }

    @Test
    fun aggregateByOp_groupsCorrectly() = runBlocking {
        val start = 0L
        val end = 10L * MS_PER_DAY
        dao.insert(row("a1", op = "EXPAND", createdAt = start + 1L, inputTokens = 10, outputTokens = 5))
        dao.insert(row("a2", op = "EXPAND", createdAt = start + 2L, inputTokens = 20, outputTokens = 10))
        dao.insert(row("a3", op = "POLISH", createdAt = start + 3L, inputTokens = 5, outputTokens = 15))
        // 区间外,不计入。
        dao.insert(row("a4", op = "EXPAND", createdAt = end + 1L, inputTokens = 999))
        // 半开区间 [start, end) 不含 end:这条 end 时刻不算。
        dao.insert(row("a5", op = "POLISH", createdAt = end, inputTokens = 999))

        val buckets = dao.aggregateByOp(start, end).first()
        assertEquals(2, buckets.size)

        val expand = buckets.first { it.op == "EXPAND" }
        assertEquals(30, expand.sumInput)
        assertEquals(15, expand.sumOutput)
        assertEquals(45, expand.sumTotal)
        assertEquals(2, expand.count)

        val polish = buckets.first { it.op == "POLISH" }
        assertEquals(5, polish.sumInput)
        assertEquals(15, polish.sumOutput)
        assertEquals(1, polish.count)
    }

    /**
     * 防回归:零行时 SQL aggregate 不能 NPE / 抛错。
     * `observeTotalTokens` 在空表返 null(SUM 语义),不是 0L。
     */
    @Test
    fun emptyTable_aggregatesReturnEmptyNotNull() = runBlocking {
        val buckets = dao.aggregateByDay(0L, MS_PER_DAY).first()
        assertTrue(buckets.isEmpty())
        val opBuckets = dao.aggregateByOp(0L, MS_PER_DAY).first()
        assertTrue(opBuckets.isEmpty())
        val providerBuckets = dao.aggregateByProvider(0L, MS_PER_DAY).first()
        assertTrue(providerBuckets.isEmpty())
        val total = dao.observeTotalTokens().first()
        assertNull("空表 SUM 返 null", total)
        // 兜底:observeAll 也要正常返回空列表,不抛。
        val all = dao.observeAll().first()
        assertNotNull(all)
        assertTrue(all.isEmpty())
    }
}
