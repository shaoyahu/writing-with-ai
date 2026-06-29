package com.yy.writingwithai.core.prefs

import com.yy.writingwithai.core.ui.animation.AnimationStyle
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * onboarding-apikey-prompt · 测试用 fake(单元测试不需要真 DataStore)。
 *
 * spec: openspec/changes/onboarding-apikey-prompt/tasks.md §7.1
 *
 * animation-system 扩展:同步 + `animationStyleFlow` / `setAnimationStyle`,`seedAnimationStyle`
 * 测试 hook 支持注入初值(包括未知 String 走解析失败路径,与 [UserPrefsStoreImpl.parseAnimationStyleOrNull] 行为一致)。
 */
@Singleton
class FakeUserPrefsStore
@Inject
constructor() : UserPrefsStore {
    private val ackState = MutableStateFlow(false)
    private val animState = MutableStateFlow(AnimationStyle.MINIMAL)

    override val ackApikeyPromptFlow: StateFlow<Boolean> = ackState.asStateFlow()

    override suspend fun isApikeyPromptAcked(): Boolean = ackState.value

    override suspend fun setAckApikeyPrompt(ack: Boolean) {
        ackState.value = ack
    }

    override val animationStyleFlow: StateFlow<AnimationStyle> = animState.asStateFlow()

    override suspend fun setAnimationStyle(style: AnimationStyle) {
        animState.value = style
    }

    /** 测试 hook:直接注入 ack 初值。 */
    fun seed(ack: Boolean) {
        ackState.value = ack
    }

    /**
     * 测试 hook:注入动画风格 raw String(模拟 DataStore 中存的值);
     * - 合法 enum name → flow emit 对应 [AnimationStyle]
     * - 未知 String / null → 经 [UserPrefsStoreImpl.parseAnimationStyleOrNull] 解析失败回退 [AnimationStyle.MINIMAL]
     */
    fun seedAnimationStyle(raw: String?) {
        animState.value =
            UserPrefsStoreImpl.parseAnimationStyleOrNull(raw) ?: AnimationStyle.MINIMAL
    }
}
