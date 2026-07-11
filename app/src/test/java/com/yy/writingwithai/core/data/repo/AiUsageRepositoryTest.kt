package com.yy.writingwithai.core.data.repo

import app.cash.turbine.test
import com.yy.writingwithai.core.data.db.AiHistoryDao
import com.yy.writingwithai.core.data.db.DailyUsageBucket
import com.yy.writingwithai.core.data.db.OpUsageBucket
import com.yy.writingwithai.core.data.db.ProviderUsageBucket
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ai-usage-statistics §7.2:JVM 单测覆盖 [AiUsageRepository]:
 * - 3 路 flow `combine` 后输出 `UsageSnapshot`;
 * - `byDay` 自动 fillDays(永远返回 `period.days` 条);
 * - `byOp` / `byProvider` 按 `sumTotal` 降序;
 * - `totalTokens` = `byProvider.sumOf { it.sumTotal }.toLong()`(等于上面两条 sum)。
 *
 * 不依赖 Robolectric / Android Context —— `AiUsageRepository` 是纯 JVM 协程流,
 * 用 `mockk` 打桩 `AiHistoryDao` 即可。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiUsageRepositoryTest {
    @Test
    fun `fills missing days and sorts providers by total desc`() = runTest {
        val dao = mockk<AiHistoryDao>()
        every { dao.aggregateByDay(any<Long>(), any<Long>()) } returns flowOf(
            listOf(
                DailyUsageBucket(dayBucket = 0, sumInput = 0, sumOutput = 0, sumTotal = 0, count = 0),
                DailyUsageBucket(dayBucket = 2, sumInput = 5, sumOutput = 7, sumTotal = 12, count = 1),
                DailyUsageBucket(dayBucket = 5, sumInput = 0, sumOutput = 0, sumTotal = 10, count = 1)
            )
        )
        every { dao.aggregateByOp(any<Long>(), any<Long>()) } returns flowOf(
            listOf(
                OpUsageBucket(op = "EXPAND", sumInput = 0, sumOutput = 0, sumTotal = 10, count = 1),
                OpUsageBucket(op = "POLISH", sumInput = 0, sumOutput = 0, sumTotal = 12, count = 1)
            )
        )
        every { dao.aggregateByProvider(any<Long>(), any<Long>()) } returns flowOf(
            listOf(
                ProviderUsageBucket(providerId = "deepseek", sumInput = 0, sumOutput = 0, sumTotal = 12, count = 1),
                ProviderUsageBucket(providerId = "minimax", sumInput = 0, sumOutput = 0, sumTotal = 10, count = 1)
            )
        )
        val repo = AiUsageRepository(dao)
        val period = UsagePeriod.Last7Days()

        repo.observeUsage(period).test {
            val snap = awaitItem()
            cancelAndIgnoreRemainingEvents()
            // byDay fillDays 后 = period.days = 7
            assertEquals(7, snap.byDay.size)
            // totalTokens = byProvider 求和(12+10=22)
            assertEquals(22L, snap.totalTokens)
            // byProvider descending
            assertEquals("deepseek", snap.byProvider.first().providerId)
            // byOp descending
            assertEquals("POLISH", snap.byOp.first().op)
        }
    }
}
