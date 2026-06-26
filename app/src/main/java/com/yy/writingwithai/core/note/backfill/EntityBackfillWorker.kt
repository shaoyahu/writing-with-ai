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
        val result = runBackfillLoop(entityExtractor, noteIds) { processed, ok, failed ->
            setProgress(
                workDataOf(
                    "processed" to processed,
                    "total" to noteIds.size,
                    "succeeded" to ok,
                    "failed" to failed
                )
            )
        }
        // review r2 修:成功后落盘完成标志,避免 BackfillScheduler.scheduleEntityBackfillIfNeeded
        // 每次调用都发现标志为 false 而反复调度 Worker(与 BackfillWorker 的 C3 修对齐)。
        applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_ENTITY_BACKFILL_DONE, true)
            .apply()
        Result.success(workDataOf("processed" to result.processed, "succeeded" to result.ok, "failed" to result.failed))
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
        private const val TAG = "EntityBackfillWorker"

        /**
         * review r3 修 H6 测试点:逐条 note 包 try/catch + 计数 + log。
         * 抽成 companion fun 让单测能直接调,不依赖 WorkManager runtime。
         *
         * @param onProgress 每条 note 处理完后回调(progress / total / ok / failed),
         *   Worker 用来 setProgress,test 可以 noop。
         */
        internal suspend fun runBackfillLoop(
            entityExtractor: EntityExtractor,
            noteIds: List<String>,
            onProgress: suspend (processed: Int, ok: Int, failed: Int) -> Unit
        ): BackfillResult {
            var processed = 0
            var ok = 0
            var failed = 0
            noteIds.forEach { id ->
                try {
                    if (entityExtractor.extractAndPersist(id, bypassRateLimit = true) > 0) ok++
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                    android.util.Log.w(TAG, "extract failed for noteId=$id", e)
                }
                processed++
                onProgress(processed, ok, failed)
            }
            return BackfillResult(processed, ok, failed)
        }

        internal data class BackfillResult(val processed: Int, val ok: Int, val failed: Int)
    }
}
