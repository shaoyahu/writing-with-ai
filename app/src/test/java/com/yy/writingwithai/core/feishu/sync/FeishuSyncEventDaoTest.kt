package com.yy.writingwithai.core.feishu.sync

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** feishu-bidir-sync · FeishuSyncEventDao 单测(tasks §10.4)。 */
class FeishuSyncEventDaoTest {

    @Test
    fun `insert and listLast returns events in descending createdAt order`() = runTest {
        val dao = FakeFeishuSyncEventDao()
        dao.insert(
            FeishuSyncEventEntity(
                id = "e1",
                noteId = "n1",
                direction = SyncDirection.PUSH,
                status = "OK",
                errorMessage = null,
                createdAt = 100L
            )
        )
        dao.insert(
            FeishuSyncEventEntity(
                id = "e2",
                noteId = "n2",
                direction = SyncDirection.PULL,
                status = "ERROR",
                errorMessage = "boom",
                createdAt = 300L
            )
        )
        dao.insert(
            FeishuSyncEventEntity(
                id = "e3",
                noteId = "n3",
                direction = SyncDirection.PUSH,
                status = "OK",
                errorMessage = null,
                createdAt = 200L
            )
        )
        val events = dao.listLast(10)
        assertEquals(3, events.size)
        assertEquals("e2", events[0].id)
        assertEquals("e3", events[1].id)
        assertEquals("e1", events[2].id)
    }

    @Test
    fun `listLast respects limit parameter`() = runTest {
        val dao = FakeFeishuSyncEventDao()
        repeat(5) { i ->
            dao.insert(
                FeishuSyncEventEntity(
                    id = "e$i",
                    noteId = "n",
                    direction = SyncDirection.PUSH,
                    status = "OK",
                    errorMessage = null,
                    createdAt = i.toLong()
                )
            )
        }
        assertEquals(3, dao.listLast(3).size)
    }

    @Test
    fun `trimTo keeps cap latest events`() = runTest {
        val dao = FakeFeishuSyncEventDao()
        repeat(10) { i ->
            dao.insert(
                FeishuSyncEventEntity(
                    id = "e$i",
                    noteId = "n",
                    direction = SyncDirection.PUSH,
                    status = "OK",
                    errorMessage = null,
                    createdAt = i.toLong()
                )
            )
        }
        dao.trimTo(cap = 4)
        val remaining = dao.listLast(100)
        assertEquals(4, remaining.size)
        assertEquals("e9", remaining[0].id)
        assertEquals("e6", remaining[3].id)
    }
}
