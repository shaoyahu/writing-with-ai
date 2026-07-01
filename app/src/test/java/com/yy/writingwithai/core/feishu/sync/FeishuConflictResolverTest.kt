package com.yy.writingwithai.core.feishu.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** feishu-bidir-sync · ConflictResolver 单测(tasks §10.2)。
 *
 * review r2 修:detect 签名新增 lastSyncedAt 参数，用 localRev > lastSyncedAt
 * 判断本地是否改过(替代永远为 true 的 localRev > 0L)。
 */
class FeishuConflictResolverTest {
    private val resolver = FeishuConflictResolver()

    @Test
    fun `first sync (empty storedRemoteRev) returns NO_CONFLICT`() {
        assertEquals(
            ConflictResult.NO_CONFLICT,
            resolver.detect(localRev = 1L, lastSyncedAt = 0L, storedRemoteRev = "", newRemoteRev = "rev1")
        )
    }

    @Test
    fun `local changed (remote unchanged) returns NO_CONFLICT`() {
        // localRev > lastSyncedAt 但 remoteRev 没变 → 仅本地改，远端未动，直接 push 覆盖
        assertEquals(
            ConflictResult.NO_CONFLICT,
            resolver.detect(localRev = 200L, lastSyncedAt = 100L, storedRemoteRev = "rev1", newRemoteRev = "rev1")
        )
    }

    @Test
    fun `remote changed only returns REMOTE_WINS`() {
        // localRev == lastSyncedAt → 本地自上次同步后未改，远端变了 → REMOTE_WINS
        assertEquals(
            ConflictResult.REMOTE_WINS,
            resolver.detect(localRev = 100L, lastSyncedAt = 100L, storedRemoteRev = "rev1", newRemoteRev = "rev2")
        )
    }

    @Test
    fun `both changed returns BOTH_DIRTY`() {
        // localRev > lastSyncedAt → 本地改过，远端也变了 → BOTH_DIRTY
        assertEquals(
            ConflictResult.BOTH_DIRTY,
            resolver.detect(localRev = 200L, lastSyncedAt = 100L, storedRemoteRev = "rev1", newRemoteRev = "rev2")
        )
    }

    @Test
    fun `neither changed returns NO_CONFLICT`() {
        assertEquals(
            ConflictResult.NO_CONFLICT,
            resolver.detect(localRev = 100L, lastSyncedAt = 100L, storedRemoteRev = "rev1", newRemoteRev = "rev1")
        )
    }

    @Test
    fun `empty newRemoteRev with non-empty stored returns NO_CONFLICT`() {
        assertEquals(
            ConflictResult.NO_CONFLICT,
            resolver.detect(localRev = 100L, lastSyncedAt = 50L, storedRemoteRev = "rev1", newRemoteRev = "")
        )
    }
}
