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
        for (id in ids) {
            if (isStopped) return@withContext Result.success()
            extractor.extractAndPersist(id, bypassRateLimit = false)
        }
        Result.success()
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
    }
}
