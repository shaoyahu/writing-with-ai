package com.yy.writingwithai.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.prefs.UserPrefsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * onboarding-apikey-prompt · 设置页「重置 apikey 教育提示」ViewModel。
 *
 * spec: openspec/changes/onboarding-apikey-prompt/specs/onboarding-consent/spec.md
 * "Reset apikey prompt in settings"
 *
 * 行为:
 * - `onResetConfirm()` 清 `ack_apikey_prompt_v1 = false`
 * - 发 `Action.ResetDone` → SettingsScreen 显示一次性 toast/提示
 * - 不重置 `consent_accepted`(隐私条款是法律层，不能重置)
 * fix-2026-06-30-full-review-r1 LOW L3:一次性 action 改 SharedFlow,
 * replay=0 + buffer=1 + DROP_OLDEST，避免 StateFlow 状态合并/重组重发问题。
 */
@HiltViewModel
class ResetApikeyPromptViewModel
@Inject
constructor(
    private val userPrefsStore: UserPrefsStore
) : ViewModel() {
    private val _action = MutableSharedFlow<Action>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val action: SharedFlow<Action> = _action.asSharedFlow()

    fun onResetConfirm() {
        viewModelScope.launch {
            userPrefsStore.setAckApikeyPrompt(false)
            _action.tryEmit(Action.ResetDone)
        }
    }

    /** no-op:SharedFlow 无状态可消费，保留为旧 caller 兼容。 */
    fun consumeAction() {
        // no-op
    }

    sealed interface Action {
        data object ResetDone : Action
    }
}
