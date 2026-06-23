package com.yy.writingwithai.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * cloud-sync-foundation · 同步 Worker 骨架。
 * 当前只记录日志，B5b 实现实际同步逻辑。
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // B5b: 实现实际同步逻辑
        return Result.success()
    }
}
