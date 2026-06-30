package com.yy.writingwithai.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.prefs.UserPrefsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _action = MutableSharedFlow<Action>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val action: SharedFlow<Action> = _action.asSharedFlow()

    init {
        viewModelScope.launch {
            userPrefsStore.ackApikeyPromptFlow.collect { _acked.value = it }
        }
    }

    /** 「我知道了」 — 设置页 → 用户填 apikey 后才走 AI,本按钮只是 ack。 */
    fun onAck() {
        viewModelScope.launch {
            userPrefsStore.setAckApikeyPrompt(true)
            _action.tryEmit(Action.Finished)
        }
    }

    /** 「稍后设置」 — spec 场景:同样 ack=true,避免后续 AI 入口反复拦截。 */
    fun onSkip() {
        viewModelScope.launch {
            userPrefsStore.setAckApikeyPrompt(true)
            _action.tryEmit(Action.Finished)
        }
    }

    /** 设置页「重新显示 API Key 说明」 — 重置 ack=false。 */
    fun onReset() {
        viewModelScope.launch {
            userPrefsStore.setAckApikeyPrompt(false)
            _action.tryEmit(Action.Reset)
        }
    }

    /** no-op:SharedFlow 无状态可消费,保留为旧 caller 兼容。 */
    fun consumeAction() {
        // no-op
    }

    sealed interface Action {
        /** ApikeyPrompt 屏结束 → AppNav navigate 出此路由。 */
        data object Finished : Action

        /** 设置页触发 reset → 不需要 navigate,只是 UI 提示用。 */
        data object Reset : Action
    }
}
