package com.yy.writingwithai.core.ui.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntSize

/**
 * animation-system · [AnimationStyle] 到 [AnimationTokens] 的映射工厂。
 *
 * 4 套风格的具体 spec 在此定义(spec §REQ 2 MINIMAL/FLUID/IMMERSIVE/NONE 表格)。
 * 业务代码不直接调此函数(除 `app/ui/theme/WritingAppTheme` 注入 CompositionLocalProvider 时)。
 */
fun AnimationStyle.toTokens(): AnimationTokens = when (this) {
    AnimationStyle.MINIMAL -> minimalTokens
    AnimationStyle.FLUID -> fluidTokens
    AnimationStyle.IMMERSIVE -> immersiveTokens
    AnimationStyle.CROSSFADE -> crossfadeTokens
    AnimationStyle.SCALE -> scaleTokens
    AnimationStyle.SLIDE_UP -> slideUpTokens
    AnimationStyle.NONE -> noneTokens
}

/**
 * animation-system · 带 override 的 token 工厂(animation-switch-redesign)。
 *
 * 在 [toTokens] 基线上,根据 `navEnabled` / `tabEnabled` 覆盖 5 个 nav/tab 字段:
 * - `navEnabled == false` → 4 个 nav field 全部退化为 `EnterTransition.None / ExitTransition.None`
 * - `tabEnabled == false` → `tabContentSpec` 退化为 `snap()`(即时切换)
 *
 * 其它字段(`switchSpec` / `expandSpec` / `collapseSpec` / `listItemSpec` / `dialogEnter/Exit`)
 * 透传基线风格,不受开关影响。
 *
 * 调用方:`app/ui/theme/WritingAppTheme` 收集 3 路 Flow 后用此函数算最终 token;
 * `AnimationStyle.toTokens()` 仍是历史 API,实现委托本函数并传 `navEnabled=true, tabEnabled=true`。
 */
fun tokensFor(style: AnimationStyle, navEnabled: Boolean, tabEnabled: Boolean): AnimationTokens {
    val base = style.toTokens()
    if (navEnabled && tabEnabled) return base
    return base.copy(
        navEnter = if (navEnabled) base.navEnter else EnterTransition.None,
        navExit = if (navEnabled) base.navExit else ExitTransition.None,
        navPopEnter = if (navEnabled) base.navPopEnter else EnterTransition.None,
        navPopExit = if (navEnabled) base.navPopExit else ExitTransition.None,
        tabContentSpec = if (tabEnabled) base.tabContentSpec else snap()
    )
}

// === MINIMAL ===
private val minimalTokens = AnimationTokens(
    navEnter = fadeIn(tween(200)),
    navExit = fadeOut(tween(150)),
    navPopEnter = fadeIn(tween(200)),
    navPopExit = fadeOut(tween(150)),
    switchSpec = spring(stiffness = 800f),
    tabContentSpec = tween(200),
    expandSpec = tween<IntSize>(200),
    collapseSpec = tween<IntSize>(150),
    listItemSpec = spring(stiffness = 900f),
    dialogEnter = fadeIn(tween(200)),
    dialogExit = fadeOut(tween(150))
)

// === FLUID ===
private val fluidTokens = AnimationTokens(
    navEnter = slideInHorizontally(
        animationSpec = spring(stiffness = 500f),
        initialOffsetX = { it / 3 }
    ) + fadeIn(tween(220)),
    navExit = fadeOut(tween(180)),
    navPopEnter = fadeIn(tween(220)),
    navPopExit = slideOutHorizontally(
        animationSpec = spring(stiffness = 500f),
        targetOffsetX = { it / 3 }
    ) + fadeOut(tween(180)),
    switchSpec = spring(stiffness = 500f),
    tabContentSpec = spring(stiffness = 700f),
    expandSpec = spring(stiffness = 700f),
    collapseSpec = spring(stiffness = 800f),
    listItemSpec = spring(stiffness = 800f),
    dialogEnter = fadeIn(tween(220)) + expandVertically(animationSpec = tween(250)),
    dialogExit = fadeOut(tween(180)) + shrinkVertically(animationSpec = tween(200))
)

// === IMMERSIVE ===
private val immersiveTokens = AnimationTokens(
    navEnter = slideInHorizontally(
        animationSpec = tween(350),
        initialOffsetX = { it }
    ),
    navExit = slideOutHorizontally(
        animationSpec = tween(300),
        targetOffsetX = { -it / 4 }
    ) + fadeOut(tween(200)),
    navPopEnter = slideInHorizontally(
        animationSpec = tween(300),
        initialOffsetX = { -it / 4 }
    ) + fadeIn(tween(200)),
    navPopExit = slideOutHorizontally(
        animationSpec = tween(350),
        targetOffsetX = { it }
    ),
    switchSpec = tween(250),
    tabContentSpec = tween(300),
    expandSpec = tween<IntSize>(300),
    collapseSpec = tween<IntSize>(250),
    listItemSpec = tween(250),
    dialogEnter = expandVertically(animationSpec = tween(250)) + fadeIn(tween(250)),
    dialogExit = fadeOut(tween(200)) + shrinkVertically(animationSpec = tween(200))
)

// === CROSSFADE ===
// 交叉淡入淡出：新旧页面同时渐变，类幻灯片过渡。
// enter/exit 同时进行(不等待对方完成)，视觉上两页面短暂叠加后切换。
private val crossfadeTokens = AnimationTokens(
    navEnter = fadeIn(tween(300)),
    navExit = fadeOut(tween(300)),
    navPopEnter = fadeIn(tween(300)),
    navPopExit = fadeOut(tween(300)),
    switchSpec = tween(250),
    tabContentSpec = tween(300),
    expandSpec = tween<IntSize>(250),
    collapseSpec = tween<IntSize>(200),
    listItemSpec = tween(200),
    dialogEnter = fadeIn(tween(250)) + expandVertically(animationSpec = tween(250)),
    dialogExit = fadeOut(tween(200)) + shrinkVertically(animationSpec = tween(200))
)

// === SCALE ===
// 缩放进出：新页面从中心放大进入，旧页面缩小退出，类打开卡片。
// enter: scaleIn(0.85→1) + fadeIn; exit: scaleOut(1→0.85) + fadeOut。
private val scaleTokens = AnimationTokens(
    navEnter = scaleIn(
        animationSpec = tween(300),
        initialScale = 0.85f
    ) + fadeIn(tween(250)),
    navExit = scaleOut(
        animationSpec = tween(250),
        targetScale = 0.85f
    ) + fadeOut(tween(200)),
    navPopEnter = scaleIn(
        animationSpec = tween(300),
        initialScale = 0.85f
    ) + fadeIn(tween(250)),
    navPopExit = scaleOut(
        animationSpec = tween(250),
        targetScale = 0.85f
    ) + fadeOut(tween(200)),
    switchSpec = tween(250),
    tabContentSpec = tween(300),
    expandSpec = tween<IntSize>(250),
    collapseSpec = tween<IntSize>(200),
    listItemSpec = tween(200),
    dialogEnter = scaleIn(
        animationSpec = tween(250),
        initialScale = 0.9f
    ) + fadeIn(tween(200)),
    dialogExit = scaleOut(
        animationSpec = tween(200),
        targetScale = 0.9f
    ) + fadeOut(tween(150))
)

// === SLIDE_UP ===
// 上滑进入：新页面从底部上滑进入，类底部弹出面板。
// enter: slideInVertically(全高→0); exit: slideOutVertically(0→全高)。
private val slideUpTokens = AnimationTokens(
    navEnter = slideInVertically(
        animationSpec = tween(300),
        initialOffsetY = { it }
    ) + fadeIn(tween(200)),
    navExit = fadeOut(tween(150)),
    navPopEnter = fadeIn(tween(200)),
    navPopExit = slideOutVertically(
        animationSpec = tween(300),
        targetOffsetY = { it }
    ) + fadeOut(tween(200)),
    switchSpec = tween(250),
    tabContentSpec = tween(300),
    expandSpec = tween<IntSize>(250),
    collapseSpec = tween<IntSize>(200),
    listItemSpec = tween(200),
    dialogEnter = slideInVertically(
        animationSpec = tween(250),
        initialOffsetY = { it }
    ) + fadeIn(tween(200)),
    dialogExit = slideOutVertically(
        animationSpec = tween(200),
        targetOffsetY = { it }
    ) + fadeOut(tween(150))
)

// === NONE ===
private val noneTokens = AnimationTokens(
    navEnter = EnterTransition.None,
    navExit = ExitTransition.None,
    navPopEnter = EnterTransition.None,
    navPopExit = ExitTransition.None,
    switchSpec = snap(),
    tabContentSpec = snap(),
    expandSpec = snap(),
    collapseSpec = snap(),
    listItemSpec = snap(),
    dialogEnter = EnterTransition.None,
    dialogExit = ExitTransition.None
)
