package com.yy.writingwithai.core.data.db

import com.yy.writingwithai.core.data.db.entity.SyncStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * F3 fix L1:review r1 syncStatus enum converter 测试。
 *
 * 关键不变量:
 * - 枚举 → String 走 `name.lowercase()`,落库形式与 v9 schema `'local'` 对齐。
 * - String → 枚举 接受全部 5 个合法值(PARTIAL_IMPORT_FAIL 由 feishu-import-from-folder 引入),
 *   未知字符串 fail closed(抛 IllegalArgumentException) —— 比静默回退到 LOCAL 更安全。
 * - review 2026-07-07 Finding #14:补 PARTIAL_IMPORT_FAIL 显式 round-trip 测试,
 *   防止后续 refactor 把 throw 改成静默回退 LOCAL 时无 guard。
 */
class SyncStatusConverterTest {

    private val converter = SyncStatusConverter()

    @Test
    fun `fromSyncStatus writes lowercase stable form`() {
        // 5 个合法值各打一次,保证落库字符串与 schema DEFAULT 'local' 一致。
        assertEquals("local", converter.fromSyncStatus(SyncStatus.LOCAL))
        assertEquals("synced", converter.fromSyncStatus(SyncStatus.SYNCED))
        assertEquals("dirty", converter.fromSyncStatus(SyncStatus.DIRTY))
        assertEquals("conflict", converter.fromSyncStatus(SyncStatus.CONFLICT))
        assertEquals("partial_import_fail", converter.fromSyncStatus(SyncStatus.PARTIAL_IMPORT_FAIL))
    }

    @Test
    fun `toSyncStatus roundtrips all enum values`() {
        SyncStatus.values().forEach { status ->
            val str = converter.fromSyncStatus(status)
            assertEquals(status, converter.toSyncStatus(str))
        }
    }

    @Test
    fun `toSyncStatus accepts partial_import_fail stable form`() {
        // review 2026-07-07 Finding #14:显式验证新增的 PARTIAL_IMPORT_FAIL 分支。
        // 如果有人重构 converter 把新分支漏掉,这条 case 会 fail。
        assertEquals(SyncStatus.PARTIAL_IMPORT_FAIL, converter.toSyncStatus("partial_import_fail"))
    }

    @Test
    fun `toSyncStatus rejects unknown strings fail closed`() {
        // 大小写错、新增未迁移的值 —— 都要显式崩,而不是静默回退 LOCAL。
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
