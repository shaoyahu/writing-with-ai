package com.yy.writingwithai.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.BuildConfig
import com.yy.writingwithai.core.prefs.ConsentStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * M4-4 Onboarding ViewModel。
 *
 * spec: openspec/changes/onboarding-consent/specs/onboarding-consent/spec.md
 * "Privacy policy rendered as Markdown" + "Reject exits the app cleanly"。
 *
 * 公共 API:
 * - `accept()` — 写 ConsentStore;AppNav 监听到 consentFlow 变 true → 跳主路由
 * - `reject()` — 调 Activity.finishAffinity() 退出 App
 *
 * r1 H3 修:删 `ProceedWithoutConsent` 死路径(原意是 `BuildConfig.CONSENT_GATE_ENABLED=false`
 * 回滚逃生口,但依赖 `feature/settings/` 的 consent UI 启用,死锁)。新方案:
 * `WritingApp.onCreate` 在 `CONSENT_GATE_ENABLED=false` 时同步写默认 consent,本 VM 行为不变。
 */
@HiltViewModel
class OnboardingViewModel
@Inject
constructor(
    private val consentStore: ConsentStore
) : ViewModel() {
    private val _scrolledToBottom = MutableStateFlow(false)
    val scrolledToBottom: StateFlow<Boolean> = _scrolledToBottom.asStateFlow()

    private val _action = MutableStateFlow<Action?>(null)
    val action: StateFlow<Action?> = _action.asStateFlow()

    fun setScrolledToBottom(value: Boolean) {
        _scrolledToBottom.update { value }
    }

    fun accept() {
        viewModelScope.launch {
            consentStore.setAccepted(
                version = BuildConfig.CONSENT_VERSION,
                at = System.currentTimeMillis()
            )
        }
    }

    fun reject() {
        _action.value = Action.ExitApp
    }

    fun consumeAction() {
        _action.value = null
    }

    sealed interface Action {
        data object ExitApp : Action
    }
}
