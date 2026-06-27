package com.yy.writingwithai.core.note.backfill

import com.yy.writingwithai.core.note.impl.SemanticNoteLinker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * review r3 修 H8:LlmBackfillWorker 之前主循环无 per-note try/catch,
 * 单条 poisoned note abort 整个 worker。修后:逐条包 try/catch + skip + log,
 * 抽出 companion `runLlmBackfillLoop` 让单测能直接覆盖。
 *
 * 验证:
 * - 单条抛 Exception 不影响后续 note
 * - 计数 processed / failed 准确
 * - CancellationException 正确重抛
 * - isStopped 提前终止循环
 */
class LlmBackfillWorkerTest {

    @Test
    fun `single poisoned note should be skipped without aborting the rest`() = runTest {
        val extractor = mockk<SemanticNoteLinker>()
        coEvery { extractor.extractAndPersist("ok", bypassRateLimit = false) } returns 1
        coEvery { extractor.extractAndPersist("broken", bypassRateLimit = false) } throws
            java.io.IOException("network down")
        coEvery { extractor.extractAndPersist("ok2", bypassRateLimit = false) } returns 0

        val result = LlmBackfillWorker.runLlmBackfillLoop(
            extractor = extractor,
            ids = listOf("ok", "broken", "ok2"),
            isStopped = { false }
        )

        assertEquals(2, result.processed)
        assertEquals(1, result.failed)
        coVerify(exactly = 1) { extractor.extractAndPersist("ok", bypassRateLimit = false) }
        coVerify(exactly = 1) { extractor.extractAndPersist("broken", bypassRateLimit = false) }
        coVerify(exactly = 1) { extractor.extractAndPersist("ok2", bypassRateLimit = false) }
    }

    @Test
    fun `CancellationException should propagate and stop the loop`() = runTest {
        val extractor = mockk<SemanticNoteLinker>()
        coEvery { extractor.extractAndPersist("c1", bypassRateLimit = false) } throws
            kotlinx.coroutines.CancellationException("cancelled")

        var caught: Throwable? = null
        try {
            LlmBackfillWorker.runLlmBackfillLoop(
                extractor = extractor,
                ids = listOf("c1"),
                isStopped = { false }
            )
        } catch (t: Throwable) {
            caught = t
        }
        assertTrue(
            caught is kotlinx.coroutines.CancellationException,
            "CancellationException must propagate so coroutine cancellation works"
        )
    }

    @Test
    fun `isStopped true should early-return without invoking extractor`() = runTest {
        val extractor = mockk<SemanticNoteLinker>()
        val result = LlmBackfillWorker.runLlmBackfillLoop(
            extractor = extractor,
            ids = listOf("a", "b", "c"),
            isStopped = { true }
        )

        assertEquals(0, result.processed)
        assertEquals(0, result.failed)
        coVerify(exactly = 0) { extractor.extractAndPersist(any(), any()) }
    }
}
