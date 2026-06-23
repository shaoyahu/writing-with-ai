package com.yy.writingwithai.core.sync

import com.yy.writingwithai.core.data.model.Note
import javax.inject.Inject
import javax.inject.Singleton

/**
 * cloud-sync-foundation · FakeSyncEngine(纯本地循环验证接口)。
 * push 返回新 revision，pull 返回空列表(模拟无远程数据)。
 */
@Singleton
class FakeSyncEngine @Inject constructor() : SyncEngine {
    private var revisionCounter = 0L

    override suspend fun push(note: Note, revision: String?): SyncResult {
        revisionCounter++
        return SyncResult.PushSuccess(newRevision = "rev-$revisionCounter")
    }

    override suspend fun pull(sinceRevision: String?): SyncResult {
        return SyncResult.PullSuccess(notes = emptyList(), latestRevision = sinceRevision)
    }

    override suspend fun getStatus(): SyncStatus = SyncStatus.Idle
}
