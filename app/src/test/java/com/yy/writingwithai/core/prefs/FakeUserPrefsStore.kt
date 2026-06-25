package com.yy.writingwithai.core.prefs

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * fix-2026-06-25-review-r1 C5 · 测试用 `UserPrefsStore` fake。
 *
 * 与 [FakeConsentStore] 同思路:`MutableStateFlow` 持 ack 状态,set 改 value。
 * 仅在 unit test 编译,不入 main。
 *
 * spec: openspec/changes/onboarding-apikey-prompt/specs/onboarding-consent/spec.md
 */
@Singleton
class FakeUserPrefsStore
@Inject
constructor() : UserPrefsStore {
    private val state = MutableStateFlow(false)

    override val ackApikeyPromptFlow
        get() = state

    override suspend fun isApikeyPromptAcked(): Boolean = state.value

    override suspend fun setAckApikeyPrompt(ack: Boolean) {
        state.value = ack
    }

    /** 测试 hook:直接注入初值。 */
    fun seed(ack: Boolean) {
        state.value = ack
    }
}
