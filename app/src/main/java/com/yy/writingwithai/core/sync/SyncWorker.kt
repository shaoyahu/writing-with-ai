package com.yy.writingwithai.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * cloud-sync-foundation · 同步 Worker 骨架。
 * fix-2026-06-24-review-r1-high H14:`runAttemptCount > 3` 守卫，
 * 真实 sync 由 enqueue 端挂 Constraints(NetworkType.CONNECTED) + BackoffPolicy(EXPONENTIAL 30s)。
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (runAttemptCount > MAX_ATTEMPTS) {
            return Result.failure()
        }
        val entryPoint = EntryPoints.get(applicationContext, SyncEntryPoint::class.java)
        val engine = entryPoint.syncEngine()
        return when (val r = engine.pull(sinceRevision = null)) {
            is SyncResult.PullSuccess -> Result.success()
            is SyncResult.PushSuccess -> Result.success()
            is SyncResult.Conflict -> Result.retry()
            is SyncResult.Failure -> Result.retry()
            is SyncResult.Unsupported -> Result.failure() // B5b 未实现，不再 silent-success
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncEntryPoint {
        fun syncEngine(): SyncEngine
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
    }
}
