package com.yy.writingwithai.core.ai.provider

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * provider-real-integration 单测用 · 内存版 [ProviderPrefsStore]。
 *
 * 默认 `"fake"`(与 [ProviderPrefsStoreImpl.DEFAULT_PROVIDER_ID] 一致),
 * 测试可通过 [seed] / [setSelectedProviderId] 改值。
 */
class FakeProviderPrefsStore(
    initial: String = ProviderPrefsStoreImpl.DEFAULT_PROVIDER_ID
) : ProviderPrefsStore {
    private val flow = MutableStateFlow(initial)

    override suspend fun getSelectedProviderId(): String = flow.value

    override suspend fun setSelectedProviderId(providerId: String) {
        flow.value = providerId
    }

    override fun observeSelectedProviderId() = flow.asStateFlow()

    /** 测试 hook:直接改 in-memory 值。 */
    fun seed(providerId: String) {
        flow.value = providerId
    }
}
