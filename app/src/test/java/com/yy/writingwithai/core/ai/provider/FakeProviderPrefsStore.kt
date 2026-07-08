package com.yy.writingwithai.core.ai.provider

import com.yy.writingwithai.core.ai.api.ApiFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * provider-real-integration 单测用 · 内存版 [ProviderPrefsStore]。
 *
 * fix-2026-06-24-review-r1-critical:`initial` 改为 `String?` 默认 `null`,
 * 与 [ProviderPrefsStoreImpl.DEFAULT_PROVIDER_ID] 一致(首次安装未配置)。
 * 测试可通过 [seed] / [setSelectedProviderId] 改值。
 */
class FakeProviderPrefsStore(
    initial: String? = ProviderPrefsStoreImpl.DEFAULT_PROVIDER_ID
) : ProviderPrefsStore {
    private val flow = MutableStateFlow(initial)
    private val selectedModels = MutableStateFlow<Map<String, String>>(emptyMap())
    private val apiFormats = MutableStateFlow<Map<String, ApiFormat>>(emptyMap())

    // fix-2026-06-28-ai-model-selection-actually-used §5.3 测试 hook:让
    // setSelectedModel 注入异常。null = 不注入，正常写 map。
    var setSelectedModelError: Throwable? = null

    override suspend fun getSelectedProviderId(): String? = flow.value

    override suspend fun setSelectedProviderId(providerId: String?) {
        flow.value = providerId
    }

    override fun observeSelectedProviderId() = flow.asStateFlow()

    override suspend fun getSelectedModel(providerId: String): String? {
        return selectedModels.value[providerId]
    }

    override suspend fun setSelectedModel(providerId: String, model: String) {
        setSelectedModelError?.let { throw it }
        // fix-review-r3-medium M6:用 update{} 原子读-改-写，避免 read-modify-write
        // 与观察者并发时丢更新(原版先 get 后 set，中间窗口竞态)。
        selectedModels.update { it + (providerId to model) }
    }

    // fix-2026-06-28-ai-model-selection-actually-used:仅 key 不存在时落，模拟
    // ProviderPrefsStoreImpl 行为(原子存在性检查 + put)。
    override suspend fun setSelectedModelIfMissing(providerId: String, defaultModel: String) {
        selectedModels.update { current ->
            if (current[providerId].isNullOrBlank()) current + (providerId to defaultModel) else current
        }
    }

    override fun observeSelectedModel(providerId: String): Flow<String?> {
        return selectedModels.map { it[providerId] }
    }

    override suspend fun getApiFormat(providerId: String): ApiFormat? {
        return apiFormats.value[providerId]
    }

    override suspend fun setApiFormat(providerId: String, format: ApiFormat) {
        // fix-review-r3-medium M6:同上，update{} 原子。
        apiFormats.update { it + (providerId to format) }
    }

    override fun observeApiFormat(providerId: String): Flow<ApiFormat?> {
        return apiFormats.map { it[providerId] }
    }

    /** 测试 hook:直接改 in-memory 值。 */
    fun seed(providerId: String) {
        flow.value = providerId
    }
}
