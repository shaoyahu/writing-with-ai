package com.yy.writingwithai.core.note.backfill

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yy.writingwithai.core.data.db.AppDatabase
import com.yy.writingwithai.core.note.entity.EntityExtractor
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** entity-extraction-association · 实体抽取回填 worker(tasks §7.2)。 */
class EntityBackfillWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val entryPoint = EntryPoints.get(applicationContext, EntityBackfillEntryPoint::class.java)
        val entityExtractor = entryPoint.entityExtractor()
        val noteIds = entryPoint.db().noteDao().observeAll().first().map { it.id }
        val total = noteIds.size
        var processed = 0
        var ok = 0
        noteIds.forEach { id ->
            try {
                if (entityExtractor.extractAndPersist(id, bypassRateLimit = true) > 0) ok++
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
            processed++
            setProgress(workDataOf("processed" to processed, "total" to total))
        }
        // review r2 修:成功后落盘完成标志,避免 BackfillScheduler.scheduleEntityBackfillIfNeeded
        // 每次调用都发现标志为 false 而反复调度 Worker(与 BackfillWorker 的 C3 修对齐)。
        applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_ENTITY_BACKFILL_DONE, true)
            .apply()
        Result.success(workDataOf("processed" to processed, "succeeded" to ok))
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface EntityBackfillEntryPoint {
        fun entityExtractor(): EntityExtractor
        fun db(): AppDatabase
    }

    companion object {
        private const val PREFS_NAME = "backfill_note_association"
        private const val PREF_ENTITY_BACKFILL_DONE = "backfill_entity_v1_done"
    }
}
