package com.yy.writingwithai.core.feishu.sync

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** feishu-bidir-sync · FeishuRefDao 单测(tasks §10.3)。 */
class FeishuRefDaoTest {

    @Test
    fun `upsert and getByNoteId round-trip`() = runTest {
        val dao = FakeFeishuRefDao()
        val entity = FeishuRefEntity(
            noteId = "n1",
            docId = "d1",
            docUrl = "https://f.cn/d1",
            lastSyncedAt = 100L,
            syncDirection = SyncDirection.PUSH,
            localRevision = 200L,
            remoteRevision = "rev1",
            status = FeishuRefStatus.SYNCED
        )
        dao.upsert(entity)
        val loaded = dao.getByNoteId("n1")
        assertNotNull(loaded)
        assertEquals(entity.docId, loaded?.docId)
    }

    @Test
    fun `getByNoteId returns null when missing`() = runTest {
        val dao = FakeFeishuRefDao()
        assertNull(dao.getByNoteId("missing"))
    }

    @Test
    fun `deleteByNoteId removes row`() = runTest {
        val dao = FakeFeishuRefDao()
        dao.upsert(
            FeishuRefEntity(
                noteId = "n1",
                docId = "d1",
                docUrl = "u",
                lastSyncedAt = 0L,
                syncDirection = SyncDirection.PUSH,
                localRevision = 0L,
                remoteRevision = "",
                status = FeishuRefStatus.SYNCED
            )
        )
        dao.deleteByNoteId("n1")
        assertNull(dao.getByNoteId("n1"))
    }

    @Test
    fun `getByNoteIds returns map by noteId`() = runTest {
        val dao = FakeFeishuRefDao()
        dao.upsert(
            FeishuRefEntity(
                noteId = "n1",
                docId = "d1",
                docUrl = "u1",
                lastSyncedAt = 0L,
                syncDirection = SyncDirection.PUSH,
                localRevision = 0L,
                remoteRevision = "",
                status = FeishuRefStatus.SYNCED
            )
        )
        dao.upsert(
            FeishuRefEntity(
                noteId = "n2",
                docId = "d2",
                docUrl = "u2",
                lastSyncedAt = 0L,
                syncDirection = SyncDirection.PULL,
                localRevision = 0L,
                remoteRevision = "",
                status = FeishuRefStatus.DIRTY
            )
        )
        val refs = dao.getByNoteIds(listOf("n1", "n2", "n3"))
        assertEquals(2, refs.size)
        assertEquals("d1", refs[0].docId)
        assertEquals(FeishuRefStatus.DIRTY, refs[1].status)
    }
}
