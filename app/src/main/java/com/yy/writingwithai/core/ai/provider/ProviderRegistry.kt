package com.yy.writingwithai.core.ai.provider

/**
 * ai-usage-statistics §4:`AiUsageScreen` 需要把 `ai_history.providerId`(string)翻译成展示名。
 *
 * 现有 [ProviderConfig] 定义在 `core/ai/provider/{deepseek,minimax,mimo}/...` 包,**internal object**
 * (`DeepseekConfig.config` 等) —— 不能跨包 import。这里汇总 3 家静态展示名,纯字符串字典,
 * 不暴露 URL / apikey / 模型清单。
 *
 * 之后新增 provider:在本文件加一行映射即可。custom provider 的 displayName 来自
 * [CustomProviderStore] 持久化的 user-defined name(后续 proposal 再接)。
 */
object ProviderRegistry {
    /** id → 展示名。找不到的 provider id 走 `id`(让 UI 至少显示原始字符串)。 */
    val displayNames: Map<String, String> = mapOf(
        "deepseek" to "DeepSeek",
        "minimax" to "MiniMax",
        "mimo" to "Mimo"
    )

    fun displayName(providerId: String): String = displayNames[providerId] ?: providerId
}
