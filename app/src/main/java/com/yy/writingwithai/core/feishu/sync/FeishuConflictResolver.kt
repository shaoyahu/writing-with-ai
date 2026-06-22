package com.yy.writingwithai.core.feishu.sync

import javax.inject.Inject
import javax.inject.Singleton

/** feishu-bidir-sync · 冲突判定结果(design D2/D3)。 */
enum class ConflictResult { NO_CONFLICT, LOCAL_WINS, REMOTE_WINS, BOTH_DIRTY }

/**
 * feishu-bidir-sync · 冲突判定器(design D2/D3)。
 *
 * 规则:local 变 ∧ remote 变 → BOTH_DIRTY;各自独变 → 对应 WINS;都没变 → NO_CONFLICT。
 */
@Singleton
class FeishuConflictResolver @Inject constructor() {
    fun detect(localRev: Long, storedRemoteRev: String, newRemoteRev: String): ConflictResult {
        if (storedRemoteRev.isEmpty()) return ConflictResult.NO_CONFLICT
        val remoteChanged = newRemoteRev.isNotEmpty() && newRemoteRev != storedRemoteRev
        if (!remoteChanged) return ConflictResult.NO_CONFLICT
        // 远端变了,本地也有过变更(localRev > 0)即冲突
        return if (localRev > 0L) ConflictResult.BOTH_DIRTY else ConflictResult.REMOTE_WINS
    }
}
