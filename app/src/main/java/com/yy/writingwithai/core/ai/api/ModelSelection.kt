package com.yy.writingwithai.core.ai.api

/**
 * fix-2026-06-28-ai-model-selection-actually-used:工具函数,统一「实际将调用的 model」
 * 算法。`selectedModel` 为 null / 空 / 纯空白时 fallback 到 [defaultModel];否则用
 * `selectedModel`。
 *
 * 业务侧必须走这个函数(不直接 `selectedModel ?: defaultModel`),保证:
 * - 空白字符串("" / "   ")也 fallback,避免把无效字面量发到 provider
 * - 同一算法在 UI 卡片 + AI 透传 + gateway fallback 三处复用,无歧义
 */
internal fun resolveActualModel(selectedModel: String?, defaultModel: String): String =
    selectedModel?.takeIf { it.isNotBlank() } ?: defaultModel
