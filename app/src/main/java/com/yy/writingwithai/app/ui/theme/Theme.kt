@file:Suppress("FunctionNaming")

package com.yy.writingwithai.app.ui.theme

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yy.writingwithai.core.prefs.UserPrefsStore
import com.yy.writingwithai.core.ui.animation.AnimationStyle
import com.yy.writingwithai.core.ui.animation.LocalAnimationTokens
import com.yy.writingwithai.core.ui.animation.toTokens
import com.yy.writingwithai.core.ui.animation.tokensFor

/**
 * ui-redesign-v2 · 语义色 token 集合，业务侧通过 MaterialTheme.customColors.success / warning 引用。
 */
data class CustomColors(
    val success: Color,
    val successDark: Color,
    val warning: Color,
    val warningDark: Color
)

// 默认 token 实例:顶层 val 保证 App 生命周期内单例，避免 Theme.kt 在重组里反复 new。
private val DefaultSpacing = Spacing()
private val DefaultCornerRadius = CornerRadius()
private val DefaultLightCustomColors = CustomColors(
    success = SuccessGreenLight,
    successDark = SuccessGreenDarkLight,
    warning = WarningAmberLight,
    warningDark = WarningAmberDarkLight
)
private val DefaultDarkCustomColors = CustomColors(
    success = SuccessGreenDarkTheme,
    successDark = SuccessGreenDarkDarkTheme,
    warning = WarningAmberDarkTheme,
    warningDark = WarningAmberDarkDarkTheme
)

// M7 fix: 改为 staticCompositionLocalOf，值仅在主题切换时变化(全量重组)，跳过读追踪
private val LocalCustomColors = staticCompositionLocalOf { DefaultLightCustomColors }

/** 业务 Composable 通过 MaterialTheme.customColors 读取语义色 token。 */
val MaterialTheme.customColors: CustomColors
    @Composable
    @ReadOnlyComposable
    get() = LocalCustomColors.current

/**
 * writing-with-ai · Material 3 主题入口。
 *
 * - 默认跟随系统 dark/light(`isSystemInDarkTheme()`)。
 * - 提供 `LocalSpacing` / `LocalCornerRadius` 两个自定义 token，业务 Composable 通过它们读取。
 * - ui-redesign-v2: 种子色从蓝 #3B82F6 改为墨绿 #1B6B4A。
 * - animation-system:提供 `LocalAnimationTokens`(spec D1 `compositionLocalOf`)。
 *   - 数据源优先级:`userPrefsStore.animationStyleFlow` > `animationStyle` 入参。
 *   - reduce-motion(`AccessibilityManager.isReduceMotionEnabled`)开启时强制覆盖为 NONE,
 *     用户的 `animation_style_v1` 持久化值不被破坏(spec REQ 3)。
 *   - reduce-motion 通过 `LifecycleEventObserver` 在 `ON_RESUME` 重新读取，运行时切系统设置有效。
 */
@Composable
fun WritingAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /**
     * 动画风格兜底值;当 [userPrefsStore] 为 null 时生效(Preview / 测试场景)。
     * 运行时由 [userPrefsStore] 的 flow 接管。
     */
    animationStyle: AnimationStyle = AnimationStyle.MINIMAL,
    /**
     * 可选注入(避免 Theme 反向依赖 core/prefs 构造参数);
     * - 运行时由 `MainActivity` 通过 Hilt EntryPoint 注入，本 Composable 内 collect flow。
     * - Preview / 测试场景传 null 时，使用 [animationStyle] 兜底值。
     */
    userPrefsStore: UserPrefsStore? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val customColors = if (darkTheme) DefaultDarkCustomColors else DefaultLightCustomColors

    // animation-system · 风格源:有 store 走 store(持久化值)，无 store 用兜底入参。
    val persistedStyle: AnimationStyle = if (userPrefsStore != null) {
        val state by userPrefsStore.animationStyleFlow.collectAsStateWithLifecycle(initialValue = animationStyle)
        state
    } else {
        animationStyle
    }

    // animation-switch-redesign · 2 个动画总开关:nav / tab 独立控制。
    // 未设置时 spec ADDED REQ 1/2 默认 `true`,与 Flow 的 `.map { ?: true }` 兜底保持一致。
    val navEnabled: Boolean = if (userPrefsStore != null) {
        val state by userPrefsStore.navAnimationsEnabledFlow.collectAsStateWithLifecycle(initialValue = true)
        state
    } else {
        true
    }
    val tabEnabled: Boolean = if (userPrefsStore != null) {
        val state by userPrefsStore.tabAnimationsEnabledFlow.collectAsStateWithLifecycle(initialValue = true)
        state
    } else {
        true
    }

    // animation-system · reduce-motion 检测(spec D3):Android 9+ API 28 `isReduceMotionEnabled`。
    // 用 LifecycleEventObserver 在 ON_RESUME 重读，捕获运行时切换系统设置。
    val reduceMotion = rememberReduceMotion()

    val effectiveStyle: AnimationStyle = if (reduceMotion) AnimationStyle.NONE else persistedStyle
    // animation-switch-redesign · reduce-motion 强制走 NONE(纯 token 覆盖,不写盘 2 个 Boolean);
    // 其它情况走 `tokensFor` 让 navEnabled/tabEnabled 覆盖 5 个 nav/tab 字段。
    val tokens = if (reduceMotion) {
        effectiveStyle.toTokens()
    } else {
        tokensFor(effectiveStyle, navEnabled = navEnabled, tabEnabled = tabEnabled)
    }

    CompositionLocalProvider(
        LocalSpacing provides DefaultSpacing,
        LocalCornerRadius provides DefaultCornerRadius,
        LocalCustomColors provides customColors,
        LocalAnimationTokens provides tokens
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}

/**
 * 返回当前系统是否启用 reduce-motion。
 *
 * 设计要点:
 * - 初始值在 [remember] 块里同步读，首次 Composition 立刻有正确值，无需等 ON_RESUME。
 * - 后续运行时切换通过 `LifecycleEventObserver` 在 `ON_RESUME` 重读，触发 state 变更重组 Theme。
 */
@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    val am = remember(context) {
        context.getSystemServiceCompat(AccessibilityManager::class.java)
    }
    var reduceMotion by remember(am) {
        mutableStateOf(am?.isReduceMotionEnabledCompat() ?: false)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, am) {
        if (am == null) {
            return@DisposableEffect onDispose { }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reduceMotion = am.isReduceMotionEnabledCompat()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return reduceMotion
}

/** AndroidX 26+ 兼容 getSystemService(Class)，不强制 minSdk 27 才能编译。 */
private inline fun <reified T> Context.getSystemServiceCompat(serviceClass: Class<T>): T? =
    getSystemService(serviceClass)

/** API 33+ 兼容读取 [AccessibilityManager.isReduceMotionEnabled];低于 33 返回 false。 */
private fun AccessibilityManager.isReduceMotionEnabledCompat(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // 反射调用避免编译期对 API 33 属性的引用(minSdk < 33 时编译不过)
        runCatching {
            val method = AccessibilityManager::class.java.getMethod("isReduceMotionEnabled")
            method.invoke(this) as? Boolean ?: false
        }.getOrDefault(false)
    } else {
        false
    }
}
