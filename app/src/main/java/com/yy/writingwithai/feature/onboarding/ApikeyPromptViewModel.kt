package com.yy.writingwithai.feature.onboarding

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
 * onboarding-apikey-prompt · ApikeyPrompt ViewModel。
 *
 * spec: openspec/changes/onboarding-apikey-prompt/specs/onboarding-consent/spec.md
 * "Apikey prompt screen shown after consent" + "Reset apikey prompt in settings"
 *
 * 行为:
 * - 读 UserPrefsStore.ackApikeyPromptFlow 镜像为本地 StateFlow
 * - `onAck()` / `onSkip()` 都写 `true`(跳过也视为已确认,避免每次 AI 调用都弹)
 * - `onReset()` 写 `false`(设置页「重新显示」按钮用)
 * - 写完发 `Action.Finished` 信号 → AppNav 监听到后 navigate 出 apikey-prompt 路由
 *
 * fix-2026-06-30-full-review-r1 LOW L3:action 改 SharedFlow,replay=0 + buffer=1 + DROP_OLDEST。
 */
@HiltViewModel
class ApikeyPromptViewModel
@Inject
constructor(
    private val userPrefsStore: UserPrefsStore
) : ViewModel() {
    private val _acked = MutableStateFlow(false)
    val acked: StateFlow<Boolean> = _acked.asStateFlow()

    // fix-2026-06-30-full-review-r1 test build:SharedFlow 没有 .value,测试读不到最近 emit;
    // 改 StateFlow<Action?>(默认 null)既兼容 .value 读取,consumeAction 也能正确清回 null。
    private val _action = MutableStateFlow<Action?>(null)
    val action: StateFlow<Action?> = _action.asStateFlow()

    init {
        viewModelScope.launch {
            userPrefsStore.ackApikeyPromptFlow.collect { _acked.value = it }
        }
    }

    /** 「我知道了」 — 设置页 → 用户填 apikey 后才走 AI,本按钮只是 ack。 */
    fun onAck() {
        viewModelScope.launch {
            userPrefsStore.setAckApikeyPrompt(true)
            _action.value = Action.Finished
        }
    }

    /** 「稍后设置」 — spec 场景:同样 ack=true,避免后续 AI 入口反复拦截。 */
    fun onSkip() {
        viewModelScope.launch {
            userPrefsStore.setAckApikeyPrompt(true)
            _action.value = Action.Finished
        }
    }

    /** 设置页「重新显示 API Key 说明」 — 重置 ack=false。 */
    fun onReset() {
        viewModelScope.launch {
            userPrefsStore.setAckApikeyPrompt(false)
            _action.value = Action.Reset
        }
    }

    /** StateFlow.Action -> null,UI 已 navigate 后调,避免重复触发。 */
    fun consumeAction() {
        _action.value = null
    }

    sealed interface Action {
        /** ApikeyPrompt 屏结束 → AppNav navigate 出此路由。 */
        data object Finished : Action

        /** 设置页触发 reset → 不需要 navigate,只是 UI 提示用。 */
        data object Reset : Action
    }
}
