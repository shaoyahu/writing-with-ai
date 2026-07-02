package com.yy.writingwithai.core.prefs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FakeSecureApiKeyStore — 纯内存 Fake，测试用。
 *
 * 内部用 `MutableMap<String, String>` 持 apikey，
 * reveal 默认 `Hidden`，clear/clearAll 同步改 map + 重置 reveal state。
 */
class FakeSecureApiKeyStore : SecureApiKeyStore {

    private val keys = mutableMapOf<String, String>()
    private val revealStates = mutableMapOf<String, MutableStateFlow<RevealState>>()

    private val _keystoreHealth = MutableStateFlow(true)
    override val keystoreHealth: StateFlow<Boolean> = _keystoreHealth.asStateFlow()

    override suspend fun save(providerId: String, apikey: String) {
        keys[providerId] = apikey
    }

    override suspend fun get(providerId: String): String? = keys[providerId]

    override suspend fun has(providerId: String): Boolean = providerId in keys

    override suspend fun clear(providerId: String) {
        keys.remove(providerId)
        revealStates[providerId]?.value = RevealState.Hidden
    }

    override suspend fun clearAll() {
        keys.clear()
        revealStates.values.forEach { it.value = RevealState.Hidden }
    }

    override fun reveal(providerId: String): StateFlow<RevealState> {
        val flow = revealStates.getOrPut(providerId) {
            // Fake 默认 Hidden，与真实实现一致：reveal 需显式触发
            MutableStateFlow(RevealState.Hidden)
        }
        return flow.asStateFlow()
    }

    override fun observeConfiguredProviders(): Flow<Set<String>> {
        val flow = MutableStateFlow(keys.keys.toSet())
        return flow.asStateFlow()
    }
}
