package com.yy.writingwithai.core.note.backfill

import com.yy.writingwithai.core.note.entity.EntityExtractor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * review r3 修 H6:EntityBackfillWorker 之前 `catch (Exception)` 静默吞 IO/Json 异常，
 * `ok` 不增，失败不可见。修后:计数 `failed` + `Log.w`，并通过 companion `runBackfillLoop`
 * 把循环逻辑抽成可测函数，避免依赖 WorkManager runtime。
 *
 * 验证:
 * - 单条 poisoned note 不 abort 整个 worker，后续 note 仍继续
 * - failed 计数与 succeeded 计数都准确
 * - CancellationException 正确重抛(不被算入 failed)
 */
class EntityBackfillWorkerTest {

    @Test
    fun `single poisoned note should increment failed and continue with remaining notes`() = runTest {
        val extractor = mockk<EntityExtractor>()
        coEvery { extractor.extractAndPersist("ok1", bypassRateLimit = true) } returns 2
        coEvery { extractor.extractAndPersist("broken", bypassRateLimit = true) } throws
            java.io.IOException("simulated IO failure")
        coEvery { extractor.extractAndPersist("ok2", bypassRateLimit = true) } returns 1

        val result = EntityBackfillWorker.runBackfillLoop(
            entityExtractor = extractor,
            noteIds = listOf("ok1", "broken", "ok2"),
            onProgress = { _, _, _ -> }
        )

        assertEquals(3, result.processed)
        // ok1 + ok2 都返回 >0
        assertEquals(2, result.ok)
        // broken 一条计入 failed
        assertEquals(1, result.failed)
        // 三条都被调用
        coVerify(exactly = 1) { extractor.extractAndPersist("ok1", bypassRateLimit = true) }
        coVerify(exactly = 1) { extractor.extractAndPersist("broken", bypassRateLimit = true) }
        coVerify(exactly = 1) { extractor.extractAndPersist("ok2", bypassRateLimit = true) }
    }

    @Test
    fun `CancellationException should propagate and not be counted as failed`() = runTest {
        val extractor = mockk<EntityExtractor>()
        coEvery { extractor.extractAndPersist("c1", bypassRateLimit = true) } throws
            kotlinx.coroutines.CancellationException("cancelled")

        var caught: Throwable? = null
        try {
            EntityBackfillWorker.runBackfillLoop(
                entityExtractor = extractor,
                noteIds = listOf("c1"),
                onProgress = { _, _, _ -> }
            )
        } catch (t: Throwable) {
            caught = t
        }
        assertTrue(
            caught is kotlinx.coroutines.CancellationException,
            "CancellationException must propagate so Worker can be cancelled cleanly"
        )
    }

    @Test
    fun `progress callback should be invoked once per note with cumulative counters`() = runTest {
        val extractor = mockk<EntityExtractor>()
        coEvery { extractor.extractAndPersist(any(), bypassRateLimit = true) } returns 1

        val progress = mutableListOf<Triple<Int, Int, Int>>()
        EntityBackfillWorker.runBackfillLoop(
            entityExtractor = extractor,
            noteIds = listOf("a", "b", "c"),
            onProgress = { p, o, f -> progress += Triple(p, o, f) }
        )

        assertEquals(3, progress.size)
        assertEquals(Triple(1, 1, 0), progress[0])
        assertEquals(Triple(2, 2, 0), progress[1])
        assertEquals(Triple(3, 3, 0), progress[2])
    }

    // entity-extraction-polish §6.4:Worker 扩 — emptyNoteList + pause guard 测试

    @Test
    fun `empty note list should succeed immediately without calling extractor`() = runTest {
        val extractor = mockk<EntityExtractor>()
        val result = EntityBackfillWorker.runBackfillLoop(
            entityExtractor = extractor,
            noteIds = emptyList(),
            onProgress = { _, _, _ -> }
        )
        assertEquals(0, result.processed)
        assertEquals(0, result.ok)
        assertEquals(0, result.failed)
        coVerify(exactly = 0) { extractor.extractAndPersist(any(), any()) }
    }

    @Test
    fun `shouldRun guard returns false when pauseBackfill is true`() {
        // entity-extraction-polish §3.1:Worker 自身 pause guard 抽成 companion fun 让单测可调，
        // 不依赖 WorkManager runtime 跑整 doWork。
        val store = io.mockk.mockk<com.yy.writingwithai.core.prefs.NoteAssociationSettingsStore>()
        io.mockk.every { store.pauseBackfill() } returns true
        assertEquals(false, EntityBackfillWorker.shouldRun(store))
    }

    @Test
    fun `shouldRun guard returns true when pauseBackfill is false`() {
        val store = io.mockk.mockk<com.yy.writingwithai.core.prefs.NoteAssociationSettingsStore>()
        io.mockk.every { store.pauseBackfill() } returns false
        assertEquals(true, EntityBackfillWorker.shouldRun(store))
    }
}
