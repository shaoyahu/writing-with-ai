package com.yy.writingwithai.core.note.backfill

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import com.yy.writingwithai.core.data.db.NoteDao
import com.yy.writingwithai.core.note.impl.LlmNoteLinkExtractor
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class LlmBackfillWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val entry = EntryPoints.get(applicationContext, LlmBackfillEntryPoint::class.java)
        val noteDao = entry.noteDao()
        val extractor = entry.llmExtractor()
        val ids = noteDao.observeAll().first().map { it.id }
        // review r3 修 H8:逐条 note 包 try/catch + skip + log,单条 poisoned note
        // (LLM 返回异常 / provider 401 / 网络断) 不再 abort 整个 worker。
        // CancellationException 必须重新抛出,Worker 才能正常 cancel。
        val result = runLlmBackfillLoop(extractor, ids, isStopped = { isStopped })
        Result.success(
            androidx.work.workDataOf("processed" to result.processed, "failed" to result.failed)
        )
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LlmBackfillEntryPoint {
        fun noteDao(): NoteDao
        fun llmExtractor(): LlmNoteLinkExtractor
    }

    companion object {
        fun constraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresCharging(true)
            .build()

        private const val TAG = "LlmBackfillWorker"

        /**
         * review r3 修 H8 测试点:逐条 note 包 try/catch + skip + log。
         * 抽成 companion fun 让单测能直接调,不依赖 WorkManager runtime。
         */
        internal suspend fun runLlmBackfillLoop(
            extractor: LlmNoteLinkExtractor,
            ids: List<String>,
            isStopped: () -> Boolean
        ): LlmBackfillResult {
            var processed = 0
            var failed = 0
            for (id in ids) {
                if (isStopped()) return LlmBackfillResult(processed, failed)
                try {
                    extractor.extractAndPersist(id, bypassRateLimit = false)
                    processed++
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                    android.util.Log.w(TAG, "llm extract failed for noteId=$id", e)
                }
            }
            return LlmBackfillResult(processed, failed)
        }

        internal data class LlmBackfillResult(val processed: Int, val failed: Int)
    }
}
