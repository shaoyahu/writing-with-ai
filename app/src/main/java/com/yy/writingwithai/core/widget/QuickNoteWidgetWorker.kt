package com.yy.writingwithai.core.widget

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * M4-1 · widget 兜底周期 worker。
 *
 * `Application.onCreate`(`WritingApp`)里 `WorkManager.enqueueUniquePeriodicWork(15min, KEEP)`
 * 调度;每次触发调 [QuickNoteWidget.updateAll] 拉一次 Room 数据。
 *
 * Glance `updateAll` 内部走 Glance worker,不需要再 enqueue 子任务。
 *
 * hardening-sse-and-widget-init H-1:错误分级 —— 瞬时错误(IO/DB 锁)走
 * `Result.retry()` 让 WorkManager 在 30s/5min/15min backoff 后重试,致命错误
 * 走 `Result.failure()` 写 failure log。`CancellationException` 必须 re-throw。
 * 原 `catch (e: Exception) { Result.success() }` 静默吞掉所有异常,违反 fail-fast
 * 原则 —— 删。
 */
class QuickNoteWidgetWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runWithErrorGrading {
        QuickNoteWidget().updateAll(applicationContext)
    }

    companion object {
        private const val TAG = "QuickNoteWidgetWorker"

        /**
         * hardening H-1:Widget update 错误分级 —— 抽出可测函数,Worker 类只做
         * CoroutineWorker 包装。便于单元测试覆盖 IO / SQLite / Throwable 三条路径。
         *
         * @param action 实际的 widget 更新动作(通常是 `QuickNoteWidget().updateAll(context)`)
         * @return WorkManager Result,`retry` / `failure` / `success` 三态
         */
        suspend fun runWithErrorGrading(action: suspend () -> Unit): Result {
            return try {
                action()
                Result.success()
            } catch (e: CancellationException) {
                // 协程取消必须 re-throw,让 WorkManager 记录为 cancelled。
                throw e
            } catch (e: IOException) {
                // 瞬时错误,等下次周期重试。
                Log.w(TAG, "transient IO failure during widget update", e)
                Result.retry()
            } catch (e: SQLiteException) {
                // 瞬时错误。
                Log.w(TAG, "transient DB failure during widget update", e)
                Result.retry()
            } catch (e: Throwable) {
                // 致命错误,标记失败,WorkManager 写 failure log 供诊断。
                Log.e(TAG, "fatal widget update failure", e)
                Result.failure()
            }
        }
    }
}
