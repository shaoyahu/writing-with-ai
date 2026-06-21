package com.yy.writingwithai.core.ai.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * provider-real-integration 单测用 · 内存版 [ProviderPrefsStore]。
 *
 * 默认 `"fake"`(与 [ProviderPrefsStoreImpl.DEFAULT_PROVIDER_ID] 一致),
 * 测试可通过 [seed] / [setSelectedProviderId] 改值。
 *
 * model-management-detail-dropdown: 同步加 per-provider selectedModel in-memory 实现。
 */
class FakeProviderPrefsStore(
    initial: String = ProviderPrefsStoreImpl.DEFAULT_PROVIDER_ID
) : ProviderPrefsStore {
    private val flow = MutableStateFlow(initial)
    private val selectedModels = MutableStateFlow<Map<String, String>>(emptyMap())
    private val apiFormats = MutableStateFlow<Map<String, ApiFormat>>(emptyMap())

    override suspend fun getSelectedProviderId(): String = flow.value

    override suspend fun setSelectedProviderId(providerId: String) {
        flow.value = providerId
    }

    override fun observeSelectedProviderId() = flow.asStateFlow()

    override suspend fun getSelectedModel(providerId: String): String? {
        return selectedModels.value[providerId]
    }

    override suspend fun setSelectedModel(providerId: String, model: String) {
        selectedModels.value = selectedModels.value + (providerId to model)
    }

    override fun observeSelectedModel(providerId: String): Flow<String?> {
        return selectedModels.map { it[providerId] }
    }

    override suspend fun getApiFormat(providerId: String): ApiFormat? {
        return apiFormats.value[providerId]
    }

    override suspend fun setApiFormat(providerId: String, format: ApiFormat) {
        apiFormats.value = apiFormats.value + (providerId to format)
    }

    override fun observeApiFormat(providerId: String): Flow<ApiFormat?> {
        return apiFormats.map { it[providerId] }
    }

    /** 测试 hook:直接改 in-memory 值。 */
    fun seed(providerId: String) {
        flow.value = providerId
    }
}
