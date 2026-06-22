package com.yy.writingwithai.core.prefs

/**
 * onboarding-apikey-prompt · AI 能力 + token 成本参考常量。
 *
 * spec: openspec/changes/onboarding-apikey-prompt/specs/onboarding-consent/spec.md
 * "Token cost reference displayed"
 *
 * 设计 D2:常量写死,UI 只渲染,不参与任何逻辑。
 * 风险:provider 调价 → 留 v2 接 provider 价格 API。
 *
 * 价格区间按 deepseek / MiniMax-M2.7 / mimo 当前公开价折算,**仅供参考**,
 * 实际费用以 provider 账单为准。
 */
data class AiAbilityCost(
    /** 能力名(zh),UI 直接渲染。 */
    val name: String,
    /** 单次调用 input token 区间。 */
    val inputTokens: String,
    /** 单次调用 output token 区间。 */
    val outputTokens: String,
    /** 单次调用折算人民币区间。 */
    val rmbRange: String
)

object AiCostReference {
    /** 4 类 AI 能力 + 成本参考。顺序 = UI 渲染顺序。 */
    val abilities: List<AiAbilityCost> = listOf(
        AiAbilityCost(
            name = "扩写 / 润色 / 整理",
            inputTokens = "500-1500",
            outputTokens = "1000-3000",
            rmbRange = "¥0.005-0.02"
        ),
        AiAbilityCost(
            name = "实体抽取",
            inputTokens = "300-600",
            outputTokens = "100-300",
            rmbRange = "¥0.001-0.005"
        ),
        AiAbilityCost(
            name = "语义兜底关联",
            inputTokens = "2000-4000",
            outputTokens = "200-500",
            rmbRange = "¥0.01-0.05"
        )
    )

    /** 免责声明 — 每行末 / 整表末尾都会展示。 */
    const val DISCLAIMER_ZH: String = "以上为参考值,实际以 provider 账单为准。本应用不经手费用。"
    const val DISCLAIMER_EN: String = "Values are reference only; actual cost depends on your provider's billing."
}
