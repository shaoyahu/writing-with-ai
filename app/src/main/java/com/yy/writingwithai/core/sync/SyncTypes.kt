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

    /**
     * fix-2026-06-24-review-r1-critical:功能未启用(B5b 后端未实现)vs 真正失败(网络 / 凭据)的区分。
     * UI 看到 `Unsupported` 应显示"功能未启用",而非误报"同步失败"。
     */
    data class Unsupported(val reason: String) : SyncResult()
}
