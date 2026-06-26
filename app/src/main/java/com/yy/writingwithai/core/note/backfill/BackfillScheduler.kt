package com.yy.writingwithai.core.note.backfill

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackfillScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun scheduleIfNeeded() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_BACKFILL_DONE, false)) return

        // R3 fix M7:把重复的 5s 初始延后 + 离线 constraints builder 收到 companion helper,
        // 避免两个方法漂移(已经看到 R1 H16/H17 改其中一个忘改另一个的迹象)。
        val request = OneTimeWorkRequestBuilder<BackfillWorker>()
            .setInitialDelay(INITIAL_DELAY_SECONDS, TimeUnit.SECONDS)
            .setConstraints(noNetworkConstraints())
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME_NOTE, ExistingWorkPolicy.KEEP, request)
        // fix-2026-06-24-review-r1-high H16:flag 移到 Worker.doWork 成功后写;此处不写
    }

    /** entity-extraction-association §7.2:AppDatabase 升级后 enqueue 实体抽取回填(5s 延后)。 */
    fun scheduleEntityBackfillIfNeeded() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_ENTITY_BACKFILL_DONE, false)) return

        // R3 fix M7:同上,复用 helper。
        // fix H17:加 .addTag(ENTITY_BACKFILL_TAG),cancel tag 真正生效
        val request = OneTimeWorkRequestBuilder<EntityBackfillWorker>()
            .setInitialDelay(INITIAL_DELAY_SECONDS, TimeUnit.SECONDS)
            .setConstraints(noNetworkConstraints())
            .addTag(ENTITY_BACKFILL_TAG)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME_ENTITY, ExistingWorkPolicy.KEEP, request)
        // fix H16:同上 flag 不在这里写
    }

    /** entity-extraction-association §7.6:取消实体回填(用户在设置页暂停)。 */
    fun cancelEntityBackfill() {
        WorkManager.getInstance(context).cancelAllWorkByTag(ENTITY_BACKFILL_TAG)
    }

    companion object {
        private const val PREFS_NAME = "backfill_note_association"
        private const val PREF_BACKFILL_DONE = "backfill_v1_done"
        private const val PREF_ENTITY_BACKFILL_DONE = "backfill_entity_v1_done"
        private const val WORK_NAME_NOTE = "note-association-backfill"
        private const val WORK_NAME_ENTITY = "entity-backfill-v4"
        const val ENTITY_BACKFILL_TAG = "entity_backfill"

        // R3 fix M7:回填延后共用常量,避免 scheduleIfNeeded / scheduleEntityBackfillIfNeeded
        // 各写一份 `5` / `Constraints.Builder()` 漂移。
        private const val INITIAL_DELAY_SECONDS = 5L

        private fun noNetworkConstraints(): Constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()
    }
}
