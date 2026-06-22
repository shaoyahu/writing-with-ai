package com.yy.writingwithai.core.feishu.sync

/** feishu-bidir-sync · 冲突判定结果(design D2/D3)。 */
enum class ConflictResult { NO_CONFLICT, LOCAL_WINS, REMOTE_WINS, BOTH_DIRTY }

/**
 * feishu-bidir-sync · 冲突判定器(design D2/D3)。
 *
 * 规则:local 变 ∧ remote 变 → BOTH_DIRTY;各自独变 → 对应 WINS;都没变 → NO_CONFLICT。
 */
class FeishuConflictResolver {
    fun detect(localRev: Long, storedRemoteRev: String, newRemoteRev: String): ConflictResult {
        if (storedRemoteRev.isEmpty()) return ConflictResult.NO_CONFLICT
        val localChanged = localRev > 0L
        val remoteChanged = newRemoteRev.isNotEmpty() && newRemoteRev != storedRemoteRev
        if (localChanged && remoteChanged) return ConflictResult.BOTH_DIRTY
        if (localChanged) return ConflictResult.LOCAL_WINS
        if (remoteChanged) return ConflictResult.REMOTE_WINS
        return ConflictResult.NO_CONFLICT
    }
}
