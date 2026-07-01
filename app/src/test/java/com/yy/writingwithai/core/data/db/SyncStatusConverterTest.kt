package com.yy.writingwithai.core.data.db

import com.yy.writingwithai.core.data.db.entity.SyncStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * F3 fix L1:review r1 syncStatus enum converter 测试。
 *
 * 关键不变量:
 * - 枚举 → String 走 `name.lowercase()`，落库形式与 v9 schema `'local'` 对齐。
 * - String → 枚举 接受全部 4 个合法值，未知字符串 fail closed(抛 IllegalArgumentException)
 *   —— 比静默回退到 LOCAL 更安全。
 */
class SyncStatusConverterTest {

    private val converter = SyncStatusConverter()

    @Test
    fun `fromSyncStatus writes lowercase stable form`() {
        // 4 个合法值各打一次，保证落库字符串与 schema DEFAULT 'local' 一致。
        assertEquals("local", converter.fromSyncStatus(SyncStatus.LOCAL))
        assertEquals("synced", converter.fromSyncStatus(SyncStatus.SYNCED))
        assertEquals("dirty", converter.fromSyncStatus(SyncStatus.DIRTY))
        assertEquals("conflict", converter.fromSyncStatus(SyncStatus.CONFLICT))
    }

    @Test
    fun `toSyncStatus roundtrips all enum values`() {
        SyncStatus.values().forEach { status ->
            val str = converter.fromSyncStatus(status)
            assertEquals(status, converter.toSyncStatus(str))
        }
    }

    @Test
    fun `toSyncStatus rejects unknown strings fail closed`() {
        // 大小写错、新增未迁移的值 —— 都要显式崩，而不是静默回退 LOCAL。
        assertThrows(IllegalArgumentException::class.java) {
            converter.toSyncStatus("LOCALLY")
        }
    }

    @Test
    fun `toSyncStatus rejects empty and blank and unknown strings`() {
        for (bad in listOf("", " ", "unknown")) {
            assertThrows(
                IllegalArgumentException::class.java,
                { converter.toSyncStatus(bad) },
                "expected IllegalArgumentException for '$bad'"
            )
        }
    }
}
