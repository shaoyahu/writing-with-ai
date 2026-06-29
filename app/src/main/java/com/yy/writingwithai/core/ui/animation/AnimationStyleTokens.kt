package com.yy.writingwithai.core.ui.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
    AnimationStyle.NONE -> noneTokens
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
