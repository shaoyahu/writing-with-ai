package com.yy.writingwithai.core.ui.animation

import android.os.Build
import android.view.accessibility.AccessibilityManager

/**
 * fix-full-review M50:共享「系统 reduce-motion 状态读取」封装。
 *
 * 之前 [com.yy.writingwithai.feature.settings.animation.AnimationDetailViewModel]
 * 和 [com.yy.writingwithai.feature.settings.animation.AnimationStylePreviewViewModel]
 * 各自写了一份 reflection 调用 [AccessibilityManager.getMethod]，重复代码 + 漏改风险。
 * `app/ui/theme/Theme.kt` 还有一份 private extension `isReduceMotionEnabledCompat()`
 * 是 Compose 上下文专用(用于设置 LocalReduceMotion),不能复用。
 *
 * 反射是因为 `AccessibilityManager.isReduceMotionEnabled` 是 API 33 引入;
 * 低于 TIRAMISU 直接返回 `false`(API < 33 设备视为未启用 reduce-motion)。
 */
object ReduceMotionReader {
    fun read(manager: AccessibilityManager?): Boolean {
        if (manager == null) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return runCatching {
            val method = AccessibilityManager::class.java.getMethod("isReduceMotionEnabled")
            method.invoke(manager) as? Boolean ?: false
        }.getOrDefault(false)
    }
}
