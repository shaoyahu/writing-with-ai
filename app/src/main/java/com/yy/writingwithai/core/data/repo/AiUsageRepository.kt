package com.yy.writingwithai.core.data.repo

import com.yy.writingwithai.core.data.db.AiHistoryDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * ai-usage-statistics §2:把 3 个 GROUP BY flow 合成 `UsageSnapshot`,做 fillDays / 排序。
 *
 * 设计备忘:
 * - 用 `combine` 而非三次串行 map,因为 3 路流同时 refresh;
 * - `byDay` 排序后 fillDays,基于 `period.startMs / MS_PER_DAY` 计算 dayBucket 起点;
 * - `byOp` / `byProvider` 在内存里按 `sumTotal` desc 排;
 * - `totalTokens` 用 byProvider 求和。
 */
@Singleton
class AiUsageRepository
@Inject
constructor(
    private val dao: AiHistoryDao
) {
    fun observeUsage(period: UsagePeriod): Flow<UsageSnapshot> {
        val startDay = period.startMs / UsagePeriod.MS_PER_DAY
        val endDay = period.endMs / UsagePeriod.MS_PER_DAY
        val byDayFlow = dao.aggregateByDay(period.startMs, period.endMs)
        val byOpFlow = dao.aggregateByOp(period.startMs, period.endMs)
        val byProviderFlow = dao.aggregateByProvider(period.startMs, period.endMs)
        return combine(byDayFlow, byOpFlow, byProviderFlow) { byDay, byOp, byProvider ->
            UsageSnapshot(
                byDay = fillDays(byDay, startDay, endDay),
                byOp = byOp.sortedByDescending { it.sumTotal },
                byProvider = byProvider.sortedByDescending { it.sumTotal },
                totalTokens = byProvider.sumOf { it.sumTotal }.toLong()
            )
        }
    }

    /**
     * 把 `byDay` 填满 `[startDay, endDay)` 区间,缺失日补 0 bucket,
     * 保证 UI 7d/30d 永远画连续 N 根柱。
     */
    private fun fillDays(
        byDay: List<com.yy.writingwithai.core.data.db.DailyUsageBucket>,
        startDay: Long,
        endDay: Long
    ): List<com.yy.writingwithai.core.data.db.DailyUsageBucket> {
        val map = byDay.associateBy { it.dayBucket }
        return (startDay until endDay).map { d ->
            map[d] ?: com.yy.writingwithai.core.data.db.DailyUsageBucket(
                dayBucket = d,
                sumInput = 0,
                sumOutput = 0,
                sumTotal = 0,
                count = 0
            )
        }
    }
}
