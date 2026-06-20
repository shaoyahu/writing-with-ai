package com.yy.writingwithai.core.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * M4-1 · widget 兜底周期 worker。
 *
 * `Application.onCreate`(`WritingApp`)里 `WorkManager.enqueueUniquePeriodicWork(15min, KEEP)`
 * 调度;每次触发调 [QuickNoteWidget.updateAll] 拉一次 Room 数据。
 *
 * Glance `updateAll` 内部走 Glance worker,不需要再 enqueue 子任务。
 */
class QuickNoteWidgetWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        QuickNoteWidget().updateAll(applicationContext)
        // M3 修:不 retry。Glance `updateAll` 内部已自带 WorkManager 子任务调度,
        // retry 会与 Glance 内部任务双跑。
        Result.success()
    } catch (e: Exception) {
        // 兜底周期失败也别 retry,避免堆积;下次 WorkManager 周期自然会再触发。
        Result.success()
    }
}
