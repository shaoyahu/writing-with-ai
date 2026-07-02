package com.yy.writingwithai.feature.settings.animation

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.prefs.UserPrefsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * animation-switch-redesign-followup §3.2:动画详细页 VM(原 `animation-switch-redesign` 的
 * nav/tab 细分开关已从 [AnimationStylePreviewViewModel] 迁出,转交本 VM)。
 *
 * 承载 2 个独立 Boolean:
 * - `navAnimationsEnabled`:导航动画(页面 push/pop 过渡)。
 * - `tabAnimationsEnabled`:Tab 切换时的内容过渡。
 *
 * reduce-motion:仅展示提示,**不**自动写入;真实覆盖由
 * [com.yy.writingwithai.app.ui.theme.WritingAppTheme] 在根 Composable 处强切 NONE。
 *
 * spec 关联:openspec/changes/animation-switch-redesign-followup/specs/animation-system/spec.md
 * REQ ADDED AnimationDetailScreen exposes nav/tab animation toggles。
 */
@HiltViewModel
class AnimationDetailViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    private val userPrefsStore: UserPrefsStore
) : ViewModel() {

    private val accessibilityManager: AccessibilityManager? =
        runCatching {
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        }.getOrNull()

    /** 「导航动画」开关 —— 直接从 PrefsStore 拿 Boolean flow,初值 `true` 与 `?: true` 兜底一致。 */
    val navAnimationsEnabled: StateFlow<Boolean> = userPrefsStore.navAnimationsEnabledFlow
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            true
        )

    /** 「标签动画」开关。 */
    val tabAnimationsEnabled: StateFlow<Boolean> = userPrefsStore.tabAnimationsEnabledFlow
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            true
        )

    /**
     * 系统 reduce-motion 状态。读一次 [AccessibilityManager.isReduceMotionEnabled](API 33+) —
     * 低于 33 时返回 `false`(API < 33 设备视为未启用)。reduce-motion 是用户在系统设置里切的,
     * 在设置页停留期间基本不变,无 hot flow 订阅需求。
     */
    private val _reduceMotionEnabled = MutableStateFlow(isReduceMotionEnabled())
    val reduceMotionEnabled: StateFlow<Boolean> = _reduceMotionEnabled.asStateFlow()

    private fun isReduceMotionEnabled(): Boolean {
        val manager = accessibilityManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val method = AccessibilityManager::class.java.getMethod("isReduceMotionEnabled")
                method.invoke(manager) as? Boolean ?: false
            }.getOrDefault(false)
        } else {
            false
        }
    }

    /**
     * 「导航动画」toggle 写盘(spec ADDED REQ AnimationDetailScreen)。
     * reduce-motion 开启时,UI 层会传 `enabled = false` 但本方法**不**额外屏蔽写入 —
     * 持久化值保留,关掉 reduce-motion 后立即恢复。
     */
    fun onNavAnimationsToggled(enabled: Boolean) {
        viewModelScope.launch {
            userPrefsStore.setNavAnimationsEnabled(enabled)
        }
    }

    /**
     * 「标签动画」toggle 写盘。
     */
    fun onTabAnimationsToggled(enabled: Boolean) {
        viewModelScope.launch {
            userPrefsStore.setTabAnimationsEnabled(enabled)
        }
    }
}
