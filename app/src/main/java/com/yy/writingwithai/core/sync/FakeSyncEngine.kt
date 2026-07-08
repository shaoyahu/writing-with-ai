package com.yy.writingwithai.core.sync

import com.yy.writingwithai.core.data.model.Note
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * cloud-sync-foundation · FakeSyncEngine(纯本地循环验证接口)。
 * push 返回新 revision，pull 返回空列表(模拟无远程数据)。
 *
 * fix-full-review:revisionCounter 改为 AtomicLong，@Singleton 实例可能被多协程并发调用 push()。
 */
@Singleton
class FakeSyncEngine @Inject constructor() : SyncEngine {
    private val revisionCounter = AtomicLong(0L)

    override suspend fun push(note: Note, revision: String?): SyncResult {
        val newRev = revisionCounter.incrementAndGet()
        return SyncResult.PushSuccess(newRevision = "rev-$newRev")
    }

    override suspend fun pull(sinceRevision: String?): SyncResult {
        return SyncResult.PullSuccess(notes = emptyList(), latestRevision = sinceRevision)
    }

    override suspend fun getStatus(): SyncStatus = SyncStatus.Idle
}
