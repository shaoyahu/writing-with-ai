package com.yy.writingwithai.core.prefs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FakeConsentStore — 纯内存 Fake，测试用。
 *
 * 内部用 `MutableStateFlow<ConsentState>` 持状态，
 * `setAccepted` / `seed` 直接改 value。
 */
class FakeConsentStore : ConsentStore {

    private val _consentFlow = MutableStateFlow(ConsentState.EMPTY)
    override val consentFlow: StateFlow<ConsentState> = _consentFlow.asStateFlow()

    override suspend fun setAccepted(version: Int, at: Long) {
        _consentFlow.value = ConsentState(accepted = true, acceptedAt = at, version = version)
    }

    override suspend fun isConsented(currentVersion: Int): Boolean {
        val state = _consentFlow.value
        return state.accepted && state.version >= currentVersion
    }

    /** 测试辅助：直接注入初始状态。 */
    fun seed(state: ConsentState) {
        _consentFlow.value = state
    }
}
