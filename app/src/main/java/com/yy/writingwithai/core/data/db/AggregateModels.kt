package com.yy.writingwithai.core.data.db

import androidx.compose.runtime.Immutable

/**
 * ai-usage-statistics §1:SQL `GROUP BY (createdAt / 86400000)` 结果行。
 *
 * `dayBucket` = `createdAt / 86400000` 计算出来的 epochDay(本地时区零点对齐)。
 * 索引 `idx_ai_history_createdAt` 已建,GROUP BY 走索引。
 */
@Immutable
data class DailyUsageBucket(
    val dayBucket: Long,
    val sumInput: Int,
    val sumOutput: Int,
    val sumTotal: Int,
    val count: Int
)

/**
 * ai-usage-statistics §1:SQL `GROUP BY op` 结果行。`op` 是 raw string(WritingOp enum.name)。
 * UI 层做 enum 翻译 / 找不到 enum 走原 string。
 */
@Immutable
data class OpUsageBucket(
    val op: String,
    val sumInput: Int,
    val sumOutput: Int,
    val sumTotal: Int,
    val count: Int
)

/**
 * ai-usage-statistics §1:SQL `GROUP BY providerId` 结果行。
 * `providerId` 是 SecureApiKeyStore 的 apikey key 前缀去掉后的 id(如 `deepseek`)。
 * UI 层需要把 id 翻译成 displayName(走 ProviderDescriptor)。
 */
@Immutable
data class ProviderUsageBucket(
    val providerId: String,
    val sumInput: Int,
    val sumOutput: Int,
    val sumTotal: Int,
    val count: Int
)
