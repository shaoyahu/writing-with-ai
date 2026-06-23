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

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val request = OneTimeWorkRequestBuilder<BackfillWorker>()
            .setInitialDelay(5, TimeUnit.SECONDS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME_NOTE, ExistingWorkPolicy.KEEP, request)

        prefs.edit().putBoolean(PREF_BACKFILL_DONE, true).apply()
    }

    /** entity-extraction-association §7.2:AppDatabase 升级后 enqueue 实体抽取回填(5s 延后)。 */
    fun scheduleEntityBackfillIfNeeded() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_ENTITY_BACKFILL_DONE, false)) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val request = OneTimeWorkRequestBuilder<EntityBackfillWorker>()
            .setInitialDelay(5, TimeUnit.SECONDS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME_ENTITY, ExistingWorkPolicy.KEEP, request)

        prefs.edit().putBoolean(PREF_ENTITY_BACKFILL_DONE, true).apply()
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
    }
}
