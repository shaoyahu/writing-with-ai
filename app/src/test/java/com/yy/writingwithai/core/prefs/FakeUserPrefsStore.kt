package com.yy.writingwithai.core.prefs

import com.yy.writingwithai.core.ui.animation.AnimationStyle
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * fix-2026-06-25-review-r1 C5 · 测试用 `UserPrefsStore` fake。
 *
 * 与 [FakeConsentStore] 同思路:`MutableStateFlow` 持状态,set 改 value。
 * 仅在 unit test 编译,不入 main。
 *
 * animation-system-and-consent-redesign §2.3:新增 `animationStyleFlow` + `setAnimationStyle` + `seedAnimationStyle`。
 */
@Singleton
class FakeUserPrefsStore
@Inject
constructor() : UserPrefsStore {
    private val ackState = MutableStateFlow(false)
    private val animStyleState = MutableStateFlow(AnimationStyle.MINIMAL)

    override val ackApikeyPromptFlow
        get() = ackState

    override val animationStyleFlow
        get() = animStyleState

    override suspend fun isApikeyPromptAcked(): Boolean = ackState.value

    override suspend fun setAckApikeyPrompt(ack: Boolean) {
        ackState.value = ack
    }

    override suspend fun setAnimationStyle(style: AnimationStyle) {
        animStyleState.value = style
    }

    /** 测试 hook:直接注入 ack 初值。 */
    fun seed(ack: Boolean) {
        ackState.value = ack
    }

    /**
     * 测试 hook:用 String 种入 animationStyle(模拟 DataStore 读取未知值场景)。
     * null → MINIMAL;未知 String → MINIMAL;合法 enum 名 → 对应风格。
     */
    fun seedAnimationStyle(raw: String?) {
        animStyleState.value = if (raw == null) {
            AnimationStyle.MINIMAL
        } else {
            runCatching { AnimationStyle.valueOf(raw) }.getOrDefault(AnimationStyle.MINIMAL)
        }
    }
}
