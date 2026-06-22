package com.yy.writingwithai.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.prefs.UserPrefsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * - 不重置 `consent_accepted`(隐私条款是法律层,不能重置)
 */
@HiltViewModel
class ResetApikeyPromptViewModel
@Inject
constructor(
    private val userPrefsStore: UserPrefsStore
) : ViewModel() {
    private val _action = MutableStateFlow<Action?>(null)
    val action: StateFlow<Action?> = _action.asStateFlow()

    fun onResetConfirm() {
        viewModelScope.launch {
            userPrefsStore.setAckApikeyPrompt(false)
            _action.value = Action.ResetDone
        }
    }

    fun consumeAction() {
        _action.value = null
    }

    sealed interface Action {
        data object ResetDone : Action
    }
}
