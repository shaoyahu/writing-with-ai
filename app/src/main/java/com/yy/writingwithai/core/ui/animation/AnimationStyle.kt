package com.yy.writingwithai.core.ui.animation

/**
 * animation-system · 用户可选动画风格(纯枚举，无 Compose 依赖)。
 *
 * 4 套(MINIMAL/FLUID/IMMERSIVE/NONE)，通过 [toTokens] 扩展函数映射到 [AnimationTokens]。
 * reduce-motion 由 `app/ui/theme/WritingAppTheme` 检测 `AccessibilityManager.isReduceMotionEnabled`
 * 强制 NONE;此处只暴露用户意图。
 *
 * **位置**:放 `core/ui/animation/` 而非 `app/ui/theme/`，因为 `core/prefs/UserPrefsStore` 需要
 * 在持久层引用此枚举(用户偏好值，非 UI 关注点);`app` 层依赖 `core` 而非反向。
 */
enum class AnimationStyle {
    /** 几乎感觉不到但顺畅(fade 200ms + 默认 spring)。 */
    MINIMAL,

    /** Spring 物理动画，有弹性(slide + spring)。 */
    FLUID,

    /** 大幅滑动，戏剧感(全屏 slide tween 350ms)。 */
    IMMERSIVE,

    /** 即时切换，无动画(尊重无障碍;reduce-motion 默认走此风格)。 */
    NONE
}
