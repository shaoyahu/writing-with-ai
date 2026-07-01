package com.yy.writingwithai.core.prefs

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * M4-4 测试用 fake，跑 Robolectric 不需要真 EncryptedSharedPreferences / Tink / KeyStore。
 * 内存 map 存 apikey;reveal() 总是 emit Hidden(测试用不到真 reveal 流程)。
 *
 * spec: openspec/changes/onboarding-consent/tasks.md §3.4
 */
@Singleton
class FakeSecureApiKeyStore
@Inject
constructor() : SecureApiKeyStore {
    private val store = mutableMapOf<String, String>()
    private val states = mutableMapOf<String, MutableStateFlow<RevealState>>()
    private val configuredIds = MutableStateFlow<Set<String>>(emptySet())

    override suspend fun save(providerId: String, apikey: String) {
        store[providerId] = apikey
        configuredIds.value = configuredIds.value + providerId
    }

    override suspend fun get(providerId: String): String? = store[providerId]

    override suspend fun has(providerId: String): Boolean = store.containsKey(providerId)

    override suspend fun clear(providerId: String) {
        store.remove(providerId)
        configuredIds.value = configuredIds.value - providerId
        states[providerId]?.value = RevealState.Hidden
    }

    override suspend fun clearAll() {
        store.clear()
        configuredIds.value = emptySet()
        states.values.forEach { it.value = RevealState.Hidden }
    }

    override fun reveal(providerId: String): StateFlow<RevealState> {
        val flow = states.getOrPut(providerId) { MutableStateFlow(RevealState.Hidden) }
        return flow.asStateFlow()
    }

    override fun observeConfiguredProviders(): Flow<Set<String>> = configuredIds.asStateFlow()
}
