package com.yy.writingwithai.core.sync

import com.yy.writingwithai.core.data.model.Note

/** 同步状态。 */
sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

/** 同步操作结果。 */
sealed class SyncResult {
    data class PushSuccess(val newRevision: String) : SyncResult()
    data class PullSuccess(val notes: List<Note>, val latestRevision: String?) : SyncResult()
    data class Conflict(val localRevision: String, val remoteRevision: String) : SyncResult()
    data class Failure(val error: String) : SyncResult()
}
