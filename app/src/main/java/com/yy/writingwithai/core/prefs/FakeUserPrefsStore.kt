package com.yy.writingwithai.core.prefs

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * onboarding-apikey-prompt · 测试用 fake(单元测试不需要真 DataStore)。
 *
 * spec: openspec/changes/onboarding-apikey-prompt/tasks.md §7.1
 */
@Singleton
class FakeUserPrefsStore
@Inject
constructor() : UserPrefsStore {
    private val state = MutableStateFlow(false)

    override val ackApikeyPromptFlow: StateFlow<Boolean> = state.asStateFlow()

    override suspend fun isApikeyPromptAcked(): Boolean = state.value

    override suspend fun setAckApikeyPrompt(ack: Boolean) {
        state.value = ack
    }

    /** 测试 hook:直接注入初值。 */
    fun seed(ack: Boolean) {
        state.value = ack
    }
}
