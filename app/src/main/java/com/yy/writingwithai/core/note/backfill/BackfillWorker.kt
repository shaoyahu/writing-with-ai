package com.yy.writingwithai.core.note.backfill

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yy.writingwithai.core.data.db.AppDatabase
import com.yy.writingwithai.core.note.NoteLinker
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class BackfillWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val entryPoint = EntryPoints.get(applicationContext, BackfillEntryPoint::class.java)
        val db = entryPoint.db()
        val noteLinker = entryPoint.noteLinker()

        val ids = db.noteDao().observeAll()
            .first()
            .map { it.id }
        val total = ids.size
        var processed = 0
        ids.chunked(BATCH_SIZE).forEach { batch ->
            batch.forEach { id ->
                noteLinker.recomputeForNote(id)
                processed++
            }
            setProgress(workDataOf("processed" to processed, "total" to total))
        }
        // fix-2026-06-25-review-r1 C3:success 才落盘，retry/failure 不写，
        // 避免失败重试时把"未完成"误标"已 done"导致后续 BackfillScheduler 不再调度。
        applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_BACKFILL_DONE_NOTE, true)
            .apply()
        Result.success(workDataOf("processed" to processed))
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BackfillEntryPoint {
        fun db(): AppDatabase
        fun noteLinker(): NoteLinker
    }

    companion object {
        private const val BATCH_SIZE = 50
        private const val PREFS_NAME = "backfill_note_association"
        private const val PREF_BACKFILL_DONE_NOTE = "backfill_v1_done"
    }
}
