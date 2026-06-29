package com.yy.writingwithai.core.ai.api

/**
 * 给 UI(设置页 / 模型管理)展示的 provider 摘要。
 *
 * fix-2026-06-28-ai-model-selection-actually-used:加 [defaultModel] 字段,UI 据此显
 * 示「实际将调用」行(消除「选 pro 实际调 flash」的歧义)。`models` 列表仍保留以供
 * 「N 个模型」展示 + 下拉选择;`defaultModel` 是无用户偏好时的 fallback。
 */
data class ProviderDescriptor(
    val id: String,
    val displayName: String,
    val models: List<String>,
    val isConfigured: Boolean,
    // review-M3:默认 "" 对齐 resolveActualModel 的 blank 兜底 — 新 caller 忘记传
    // 也会编译过(走空白 fallback),不会因「required String」字面抛头。
    val defaultModel: String = ""
)
