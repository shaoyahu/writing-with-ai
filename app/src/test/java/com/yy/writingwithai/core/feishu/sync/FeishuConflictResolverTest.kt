package com.yy.writingwithai.core.feishu.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** feishu-bidir-sync · ConflictResolver 单测(tasks §10.2)。 */
class FeishuConflictResolverTest {
    private val resolver = FeishuConflictResolver()

    @Test
    fun `first sync (empty storedRemoteRev) returns NO_CONFLICT`() {
        assertEquals(
            ConflictResult.NO_CONFLICT,
            resolver.detect(localRev = 1L, storedRemoteRev = "", newRemoteRev = "rev1")
        )
    }

    @Test
    fun `local changed (remote unchanged) returns NO_CONFLICT`() {
        // localRev > 0 但 remoteRev 没变 → 仅本地改,远端未动,直接 push 覆盖(spec "Local-only change")
        assertEquals(
            ConflictResult.NO_CONFLICT,
            resolver.detect(localRev = 100L, storedRemoteRev = "rev1", newRemoteRev = "rev1")
        )
    }

    @Test
    fun `remote changed only returns REMOTE_WINS`() {
        // localRev=0 表示本地自上次同步后未改
        assertEquals(
            ConflictResult.REMOTE_WINS,
            resolver.detect(localRev = 0L, storedRemoteRev = "rev1", newRemoteRev = "rev2")
        )
    }

    @Test
    fun `both changed returns BOTH_DIRTY`() {
        assertEquals(
            ConflictResult.BOTH_DIRTY,
            resolver.detect(localRev = 200L, storedRemoteRev = "rev1", newRemoteRev = "rev2")
        )
    }

    @Test
    fun `neither changed returns NO_CONFLICT`() {
        assertEquals(
            ConflictResult.NO_CONFLICT,
            resolver.detect(localRev = 100L, storedRemoteRev = "rev1", newRemoteRev = "rev1")
        )
    }

    @Test
    fun `empty newRemoteRev with non-empty stored returns NO_CONFLICT`() {
        assertEquals(
            ConflictResult.NO_CONFLICT,
            resolver.detect(localRev = 100L, storedRemoteRev = "rev1", newRemoteRev = "")
        )
    }
}
