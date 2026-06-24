package com.yy.writingwithai.core.sync

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebDavSyncEngineUnsupportedTest {

    private val engine = WebDavSyncEngine()

    @Test
    fun `push returns Unsupported not Failure`() = runTest {
        val result = engine.push(note = note(), revision = null)
        assertTrue(result is SyncResult.Unsupported, "expected Unsupported, got: $result")
        assertEquals(
            "WebDAV sync not implemented yet (B5b)",
            (result as SyncResult.Unsupported).reason
        )
    }

    @Test
    fun `pull returns Unsupported not Failure`() = runTest {
        val result = engine.pull(sinceRevision = null)
        assertTrue(result is SyncResult.Unsupported, "expected Unsupported, got: $result")
    }

    @Test
    fun `SyncResult sealed adds Unsupported case`() {
        val values: List<SyncResult> = listOf(
            SyncResult.PushSuccess("rev1"),
            SyncResult.PullSuccess(emptyList(), null),
            SyncResult.Conflict("a", "b"),
            SyncResult.Failure("err"),
            SyncResult.Unsupported("not yet")
        )
        assertEquals(5, values.size)
        assertTrue(values.last() is SyncResult.Unsupported)
    }

    private fun note() = com.yy.writingwithai.core.data.model.Note(
        id = "n1",
        title = "test",
        content = "x",
        createdAt = 0L,
        updatedAt = 0L,
        isPinned = false,
        lastAiOp = null,
        lastAiAt = null
    )
}
