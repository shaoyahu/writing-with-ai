package com.yy.writingwithai.feature.settings.freewrite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.notification.MorningFreewriteScheduler
import com.yy.writingwithai.core.prefs.UserPrefsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * morning-freewrite §2.3 + §3.2:Settings 屏内 VM。
 *
 * 状态合并:
 * - `enabled: Boolean`(`morningFreewriteEnabledFlow`)
 * - `time: LocalTime`(`morningFreewriteTimeFlow`)
 *
 * 动作:
 * - `setEnabled(b)`:写 prefs + 同步调度(开 → schedule,关 → cancel)
 * - `setTime(t)`:写 prefs + 若已 enabled 重新 schedule(防 AlarmManager 仍指向旧时间)
 *
 * 设计要点:
 * - **不**直接 `android.app.AlarmManager.setExactAndAllowWhileIdle` —— 走 [MorningFreewriteScheduler] 统一封装,
 *   避免散落多份 fallback 逻辑(canScheduleExactAlarms → setAndAllowWhileIdle)。
 * - 状态初始值用 `WhileSubscribed(5000)`,屏离开 5s 后停订阅,回屏再取最新。
 */
@HiltViewModel
class SettingsFreewriteViewModel
@Inject
constructor(
    private val prefs: UserPrefsStore,
    private val scheduler: MorningFreewriteScheduler
) : ViewModel() {

    val uiState: StateFlow<SettingsFreewriteUiState> = combine(
        prefs.morningFreewriteEnabledFlow,
        prefs.morningFreewriteTimeFlow
    ) { enabled, time ->
        SettingsFreewriteUiState(enabled = enabled, time = time)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsFreewriteUiState.DEFAULT
    )

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setMorningFreewriteEnabled(enabled)
            val time = prefs.morningFreewriteTimeFlow.first()
            if (enabled) {
                scheduler.schedule(time.hour, time.minute, LocalDate.now())
            } else {
                scheduler.cancel()
            }
        }
    }

    fun setTime(time: LocalTime) {
        viewModelScope.launch {
            prefs.setMorningFreewriteTime(time)
            val enabled = prefs.morningFreewriteEnabledFlow.first()
            if (enabled) {
                // 已开启 → 取消旧闹钟 + 按新时间重排;否则不动 AlarmManager
                scheduler.schedule(time.hour, time.minute, LocalDate.now())
            }
        }
    }
}

/** morning-freewrite §2.3 UI 状态。 */
data class SettingsFreewriteUiState(
    val enabled: Boolean,
    val time: LocalTime
) {
    companion object {
        /** 默认值:未启用 + 08:00(design §3.2 / spec 默认)。 */
        val DEFAULT = SettingsFreewriteUiState(
            enabled = false,
            time = LocalTime.of(8, 0)
        )
    }
}
