package com.yy.writingwithai.core.ui.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.IntSize

/**
 * animation-system · 项目级动画 token 集合。
 *
 * 一份 token 同时覆盖 nav / Switch / Tab / 展开折叠 / ListItem / Dialog,4 套
 * 风格(MINIMAL/FLUID/IMMERSIVE/NONE)的差异通过 [toTokens] 扩展函数呈现。
 * 业务 Composable 通过 [LocalAnimationTokens] 读取。
 *
 * 设计取舍(spec D1):用 `compositionLocalOf` 而非 `staticCompositionLocalOf`，因为用户切换
 * 风格时值会变，需要细粒度重组读取者。
 *
 * 类型说明:
 * - [switchSpec]/[listItemSpec]: `FiniteAnimationSpec<Float>` — AnimatedSwitch thumb 位移等 Float 动画
 * - [tabContentSpec]: `FiniteAnimationSpec<Float>` — fadeIn/fadeOut animationSpec 参数需 Finite 子类型
 * - [expandSpec]/[collapseSpec]: `FiniteAnimationSpec<IntSize>` — expandVertically/shrinkVertically 需此类型
 * - nav/dialog: EnterTransition / ExitTransition — NavHost composable 过渡
 */
data class AnimationTokens(
    /** NavHost forward enter。 */
    val navEnter: EnterTransition,

    /** NavHost forward exit。 */
    val navExit: ExitTransition,

    /** NavHost pop enter(返回目标页)。 */
    val navPopEnter: EnterTransition,

    /** NavHost pop exit(从栈顶返回)。 */
    val navPopExit: ExitTransition,

    /** Switch thumb 位移 spec。 */
    val switchSpec: FiniteAnimationSpec<Float>,

    /** Tab 内容切换 spec(`AnimatedContent` transitionSpec fadeIn/fadeOut)。 */
    val tabContentSpec: FiniteAnimationSpec<Float>,

    /** 展开(`AnimatedVisibility` enter)spec — expandVertically 用。 */
    val expandSpec: FiniteAnimationSpec<IntSize>,

    /** 折叠(`AnimatedVisibility` exit)spec — shrinkVertically 用。 */
    val collapseSpec: FiniteAnimationSpec<IntSize>,

    /** 列表项插入 spec。 */
    val listItemSpec: FiniteAnimationSpec<Float>,

    /** Dialog 进入。 */
    val dialogEnter: EnterTransition,

    /** Dialog 退出。 */
    val dialogExit: ExitTransition
)

/**
 * 当前生效的 [AnimationTokens]。
 *
 * - 由 `app/ui/theme/WritingAppTheme` 在根 Composable 注入(`CompositionLocalProvider`)。
 * - reduce-motion 启用时 `WritingAppTheme` 强制覆盖为 `AnimationStyle.NONE.toTokens()`。
 * - 业务 Composable 仅读 **current**，绝不写。
 */
val LocalAnimationTokens = compositionLocalOf<AnimationTokens> {
    error(
        "LocalAnimationTokens used outside WritingAppTheme. " +
            "Wrap your composable in WritingAppTheme { ... } to provide AnimationTokens."
    )
}
