package com.yy.writingwithai.core.widget

import android.database.sqlite.SQLiteException
import androidx.work.ListenableWorker
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * hardening-sse-and-widget-init H-1:验证 [QuickNoteWidgetWorker.runWithErrorGrading]
 * 错误分级契约 —— 瞬时错误 retry / 致命错误 failure / CancellationException rethrow。
 *
 * 不依赖 WorkManager runtime，只测静态函数 + 抛错 lambda。
 */
class QuickNoteWidgetWorkerTest {

    @Test
    fun `successful action returns Result_success`() = runTest {
        val result = QuickNoteWidgetWorker.runWithErrorGrading { /* no-op */ }
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `IOException returns Result_retry`() = runTest {
        val result = QuickNoteWidgetWorker.runWithErrorGrading {
            throw IOException("simulated disk full")
        }
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `SQLiteException returns Result_retry`() = runTest {
        val result = QuickNoteWidgetWorker.runWithErrorGrading {
            throw SQLiteException("simulated database locked")
        }
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `unknown Throwable returns Result_failure`() = runTest {
        val result = QuickNoteWidgetWorker.runWithErrorGrading {
            throw IllegalStateException("simulated unexpected state")
        }
        assertTrue(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun `CancellationException is rethrown not swallowed`() = runTest {
        val ex = assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                QuickNoteWidgetWorker.runWithErrorGrading {
                    throw CancellationException("simulated cancel")
                }
            }
        }
        // CancellationException rethrow 路径不返回 Result，异常原样传出。
        assertEquals("simulated cancel", ex.message)
    }

    @Test
    fun `RuntimeException returns Result_failure not retry`() = runTest {
        // H-1 边界:RuntimeException 落 Throwable 分支 → failure，不被吞掉。
        val result = QuickNoteWidgetWorker.runWithErrorGrading {
            throw RuntimeException("not IO, not SQLite")
        }
        assertTrue(result is ListenableWorker.Result.Failure, "RuntimeException should produce Failure, got $result")
    }
}
