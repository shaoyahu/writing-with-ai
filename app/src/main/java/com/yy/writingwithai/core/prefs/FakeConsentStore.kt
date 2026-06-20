package com.yy.writingwithai.core.prefs

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * M4-4 测试用 fake,跑单元测试不需要真 DataStore。
 *
 * spec: openspec/changes/onboarding-consent/tasks.md §3.4
 */
@Singleton
class FakeConsentStore
@Inject
constructor() : ConsentStore {
    private val state = MutableStateFlow(ConsentState.EMPTY)

    override val consentFlow: StateFlow<ConsentState> = state.asStateFlow()

    override suspend fun setAccepted(version: Int, at: Long) {
        state.value = ConsentState(accepted = true, acceptedAt = at, version = version)
    }

    override suspend fun isConsented(currentVersion: Int): Boolean {
        val s = state.value
        return s.accepted && s.version >= currentVersion
    }

    /** 测试 hook:直接注入初值。 */
    fun seed(value: ConsentState) {
        state.value = value
    }
}
