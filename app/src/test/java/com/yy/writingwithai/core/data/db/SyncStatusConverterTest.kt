package com.yy.writingwithai.core.data.db

import com.yy.writingwithai.core.data.db.entity.SyncStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * F3 fix L1:review r1 syncStatus enum converter 测试。
 *
 * 关键不变量:
 * - 枚举 → String 走 `name.lowercase()`,落库形式与 v9 schema `'local'` 对齐。
 * - String → 枚举 接受全部 5 个合法值(PARTIAL_IMPORT_FAIL 由 feishu-import-from-folder 引入),
 *   未知字符串 fail open(回退到 LOCAL + log warning) —— fix-full-review:之前 fail-closed
 *   (valueOf 抛 IllegalArgumentException)会导致旧版 DB 新增 enum 值后 App 崩溃；
 *   回退到 LOCAL 更安全，新值丢失总比整库不可读好。
 * - review 2026-07-07 Finding #14:补 PARTIAL_IMPORT_FAIL 显式 round-trip 测试。
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

    // fix-full-review:未知字符串不再抛异常，改为 fail-open 回退到 LOCAL。
    // 之前 fail-closed(valueOf 抛 IllegalArgumentException)会导致旧版 DB 新增 enum 值后 App 崩溃。
    @Test
    fun `toSyncStatus falls back to LOCAL for unknown strings`() {
        // 大小写错、新增未迁移的值 —— 回退到 LOCAL 而非崩溃
        assertEquals(SyncStatus.LOCAL, converter.toSyncStatus("LOCALLY"))
    }

    @Test
    fun `toSyncStatus falls back to LOCAL for empty and blank and unknown strings`() {
        for (bad in listOf("", " ", "unknown")) {
            assertEquals(
                SyncStatus.LOCAL,
                converter.toSyncStatus(bad),
                "expected LOCAL fallback for '$bad'"
            )
        }
    }
}
