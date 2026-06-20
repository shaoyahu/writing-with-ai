package com.yy.writingwithai.core.ai.api

/** 给 UI(设置页 / 模型管理)展示的 provider 摘要。 */
data class ProviderDescriptor(
    val id: String,
    val displayName: String,
    val models: List<String>,
    val isConfigured: Boolean
)
