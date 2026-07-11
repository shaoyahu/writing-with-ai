package com.yy.writingwithai.core.data.repo

import androidx.compose.runtime.Immutable
import com.yy.writingwithai.core.data.db.DailyUsageBucket
import com.yy.writingwithai.core.data.db.OpUsageBucket
import com.yy.writingwithai.core.data.db.ProviderUsageBucket

/**
 * ai-usage-statistics §2:`AiUsageRepository.observeUsage` 输出物。
 *
 * - `byDay` 填满 `[periodStart.dayBucket, periodEnd.dayBucket)` 连续区间,
 *   缺失日填 `DailyUsageBucket(_, 0, 0, 0, 0)`,便于 Compose Canvas 画连续 X 轴;
 * - `byOp` / `byProvider` 按 `sumTotal` 降序;
 * - `totalTokens` = `byProvider.sumOf { it.sumTotal }`(也等于 byDay.sumOf / byOp.sumOf,数学等价)。
 */
@Immutable
data class UsageSnapshot(
    val byDay: List<DailyUsageBucket>,
    val byOp: List<OpUsageBucket>,
    val byProvider: List<ProviderUsageBucket>,
    val totalTokens: Long
)
