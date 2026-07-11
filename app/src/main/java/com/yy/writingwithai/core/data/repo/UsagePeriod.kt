package com.yy.writingwithai.core.data.repo

/**
 * ai-usage-statistics §2:`UsagePeriod` 时间窗 sealed class。
 *
 * fix-review-r1 F4:对齐 SQL `GROUP BY (createdAt / 86400000)` 走 UTC epoch day bucket。
 * 原实现用本地时区零点(Calendar.getInstance(TimeZone.getDefault())),结果 7-day 窗口
 * 起点在不同用户机器 / DST 切换日偏移不一致;SQL 用 UTC epoch day 算 bucket,本地零点会
 * 跟 SQL 错位 → 图表坐标轴对不上日期。改为 (ms / MS_PER_DAY) * MS_PER_DAY 后两端一致。
 */
sealed class UsagePeriod(
    val startMs: Long,
    val endMs: Long,
    val days: Int
) {
    data class Last7Days(
        val nowMs: Long = System.currentTimeMillis()
    ) : UsagePeriod(
        startMs = startOfDayMillis(nowMs),
        endMs = startOfDayMillis(nowMs) + 7L * MS_PER_DAY,
        days = 7
    )

    data class Last30Days(
        val nowMs: Long = System.currentTimeMillis()
    ) : UsagePeriod(
        startMs = startOfDayMillis(nowMs),
        endMs = startOfDayMillis(nowMs) + 30L * MS_PER_DAY,
        days = 30
    )

    companion object {
        const val MS_PER_DAY = 86_400_000L

        /**
         * 把 nowMs 切到 UTC epoch day 起点;与 SQL createdAt / 86400000 桶对齐。
         * 移动用户跨时区 / DST 切换 / 系统时区改动,桶依然稳定。
         */
        internal fun startOfDayMillis(nowMs: Long): Long {
            return (nowMs / MS_PER_DAY) * MS_PER_DAY
        }
    }
}
