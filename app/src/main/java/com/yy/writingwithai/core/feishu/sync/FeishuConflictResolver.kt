package com.yy.writingwithai.core.feishu.sync

import javax.inject.Inject
import javax.inject.Singleton

/** feishu-bidir-sync · 冲突判定结果(design D2/D3)。 */
enum class ConflictResult { NO_CONFLICT, LOCAL_WINS, REMOTE_WINS, BOTH_DIRTY }

/**
 * feishu-bidir-sync · 冲突判定器(design D2/D3)。
 *
 * 规则:local 变 ∧ remote 变 → BOTH_DIRTY;各自独变 → 对应 WINS;都没变 → NO_CONFLICT。
 *
 * review r2 修:`localRev > 0L` 永远为 true(localRev 是 updatedAt 毫秒时间戳),
 * 导致远端变更时总是判定 BOTH_DIRTY。改为比较 localRev 与 lastSyncedAt:
 * localRev > lastSyncedAt → 本地在上次同步后改过 → BOTH_DIRTY;
 * 否则 → REMOTE_WINS。
 */
@Singleton
class FeishuConflictResolver @Inject constructor() {
    fun detect(localRev: Long, lastSyncedAt: Long, storedRemoteRev: String, newRemoteRev: String): ConflictResult {
        if (storedRemoteRev.isEmpty()) return ConflictResult.NO_CONFLICT
        val remoteChanged = newRemoteRev.isNotEmpty() && newRemoteRev != storedRemoteRev
        if (!remoteChanged) return ConflictResult.NO_CONFLICT
        // 远端变了，本地在上次同步后也有变更(localRev > lastSyncedAt)即冲突
        return if (localRev > lastSyncedAt) ConflictResult.BOTH_DIRTY else ConflictResult.REMOTE_WINS
    }
}
