package com.yy.writingwithai.feature.settings.animation

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.prefs.UserPrefsStore
import com.yy.writingwithai.core.ui.animation.AnimationStyle
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
 * animation-system-and-consent-redesign §10.1:动画风格设置页 VM。
 *
 * - 当前风格:`UserPrefsStore.animationStyleFlow`(enum,unknown → MINIMAL fallback,见 UserPrefsStore)
 * - 系统 reduce-motion:`AccessibilityManager.isReduceMotionEnabled`,仅展示提示,**不**自动写入;
 *   真实覆盖由 [com.yy.writingwithai.app.ui.theme.WritingAppTheme] 在根 Composable 处强切 NONE。
 * - 写盘:`onStyleSelected(style)` → `setAnimationStyle` 持久化;UI 通过 collect 重绘。
 *
 * spec 关联:openspec/changes/animation-system-and-consent-redesign/specs/animation-system/spec.md
 */
@HiltViewModel
class AnimationStylePreviewViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    private val userPrefsStore: UserPrefsStore
) : ViewModel() {

    private val accessibilityManager: AccessibilityManager? =
        runCatching {
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        }.getOrNull()

    /** 当前生效的动画风格(spec D2:DataStore 存 enum name String)。 */
    val animationStyle: StateFlow<AnimationStyle> = userPrefsStore.animationStyleFlow
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            // 初值给 MINIMAL;真实值在 collect 后立刻 emit 覆盖,UI 不会出现抖动。
            AnimationStyle.MINIMAL
        )

    /**
     * 系统 reduce-motion 状态。读一次 [AccessibilityManager.isReduceMotionEnabled](API 33+) —
     * 低于 33 时返回 `false`(API < 33 设备视为未启用)。reduce-motion 是用户在系统设置里切的,
     * 在设置页停留期间基本不变,无 hot flow 订阅需求(spec §REQ 3)。
     */
    private val _reduceMotionEnabled = MutableStateFlow(isReduceMotionEnabled())
    val reduceMotionEnabled: StateFlow<Boolean> = _reduceMotionEnabled.asStateFlow()

    private fun isReduceMotionEnabled(): Boolean {
        val manager = accessibilityManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 反射调用避免编译期对 API 33 属性的引用
            runCatching {
                val method = AccessibilityManager::class.java.getMethod("isReduceMotionEnabled")
                method.invoke(manager) as? Boolean ?: false
            }.getOrDefault(false)
        } else {
            false
        }
    }

    /**
     * 写盘;若 reduce-motion 已启用,调用此方法仍会写入(用户明确选择),但实际生效由
     * [com.yy.writingwithai.app.ui.theme.WritingAppTheme] 强切 NONE(spec §REQ 3)。
     */
    fun onStyleSelected(style: AnimationStyle) {
        viewModelScope.launch {
            userPrefsStore.setAnimationStyle(style)
        }
    }
}
