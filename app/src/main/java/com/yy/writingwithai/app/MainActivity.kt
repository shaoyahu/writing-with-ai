package com.yy.writingwithai.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import com.yy.writingwithai.BuildConfig
import com.yy.writingwithai.R
import com.yy.writingwithai.core.i18n.LocaleHelper
import com.yy.writingwithai.core.i18n.LocaleStore
import com.yy.writingwithai.core.prefs.ConsentStore
import com.yy.writingwithai.core.widget.WidgetLaunchRoute
import com.yy.writingwithai.core.widget.parseLaunchRoute
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * writing-with-ai · 单 Activity 入口。
 *
 * 整个应用的 Compose 树根是 [App];所有路由都由 [AppNav] 承载。
 *
 * M4-1 改动:从 widget `PendingIntent` 启动时，`Intent.extra[OpenNoteAction.EXTRA_ROUTE]`
 * 携带路由字符串(如 `"quicknote/edit?prefillFocus=true"`),`App(initialRoute=...)` 解析并跳。
 *
 * M4-4 改动(r1 H1 + M1 修):
 * - 解析 `intent.getStringExtra("route")` 前同步查 ConsentStore.isConsented();
 *   未同意 → 把 route 写到 `widgetPendingRoute` MutableState(由 `AppNav` 在同意后 navigate)
 * - `onNewIntent`(r1 M1 修)同样处理:未同意 → 改 widgetPendingRoute;已同意 → 立刻
 *   setContent 把 route 传给 `App`(走 initialRoute 路径，AppNav 启动 LaunchedEffect 解析)
 */
@EntryPoint
@InstallIn(ActivityComponent::class)
internal interface MainActivityEntryPoint {
    fun consentStore(): ConsentStore
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** M4-4:widget 启动但未同意时暂存 route，同意后回放。Compose state 由 App 持，跨 Activity 重建。
     *  hardening H-4:类型由 String? 升级为 sealed [WidgetLaunchRoute]，不再做 string prefix 解析。 */
    private val widgetPendingRoute = mutableStateOf<WidgetLaunchRoute?>(null)
    private var lastInitialRoute: WidgetLaunchRoute? = null

    // language-switcher fix:Activity.recreate() 不会重建 Application,
    // 所以 WritingApp.attachBaseContext 不会再次执行。必须在 Activity 层
    // 也 override attachBaseContext，确保每次 Activity 创建(含 recreate 后重建)
    // 都用 SharedPreferences 中保存的语言偏好 wrap context，否则切换语言不生效。
    override fun attachBaseContext(base: android.content.Context) {
        val selection = LocaleStore.readOnceBlocking(base)
        val systemLocale = base.resources.configuration.locales[0]
            ?: java.util.Locale.getDefault()
        val locale = LocaleHelper.resolveLocale(selection, systemLocale)
        super.attachBaseContext(LocaleHelper.wrap(base, locale))
    }

    // fix-global-back-nav-and-gesture: 主页防误触(2s 二次确认 Toast)。
    // C1 修:back callback 默认 enabled=false，等 navController ready 后在
    // onDestinationChanged 真正命中主页再 enable，避免 onboarding 屏死锁。
    private var lastBackPressAt: Long = 0L
    private val backCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                val now = SystemClock.elapsedRealtime()
                if (now - lastBackPressAt > 2000L) {
                    lastBackPressAt = now
                    Toast.makeText(
                        this@MainActivity,
                        R.string.back_press_exit_hint,
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 反馈 #6(2026-06-23):启用 edge-to-edge，让 Compose 接管 status bar insets。
        // 否则 M3 TopAppBar 的 windowInsets(默认含 status bar top)会与系统装饰 double-pad,
        // 表现为"随手记/我的"标题距屏幕顶部过远。
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, backCallback)
        // hardening H-4:用 parseLaunchRoute(intent) 替代 getStringExtra + string prefix 解析。
        val route = parseLaunchRoute(intent)
        handleRawRoute(route)
        setContent {
            App(
                initialRoute = lastInitialRoute,
                widgetPendingRoute = widgetPendingRoute,
                onNavControllerReady = { nc: NavController ->
                    // C1 修:用 typed Nav 2.8 hasRoute() 比对，而不是 FQN 字符串
                    // `dest.route`(Navigation Compose 对 data object emit 的是序列化 route,
                    // 不是 qualifiedName)。onNavControllerReady 是普通 lambda 不在 @Composable
                    // 上下文里，直接 register listener(只调一次)。
                    nc.addOnDestinationChangedListener { _, dest, _ ->
                        // review r2 修:QuicknoteList 已不再是根 NavHost destination(被 AppShell 取代),
                        // hasRoute(QuicknoteList::class) 永远返回 false，二次确认退出功能完全失效。
                        // 改为检查 AppShell(底部 tab 容器根路由)。
                        backCallback.isEnabled = dest.hasRoute(AppShell::class)
                    }
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val route = parseLaunchRoute(intent)
        handleRawRoute(route)
    }

    /**
     * M5:consent 检查移入 [Dispatchers.IO] 协程，主线程不 block。
     * 已同意 → 更新 [lastInitialRoute];未同意 → 写 [widgetPendingRoute] state。
     * fire-and-forget;AppNav LaunchedEffect(consentState) 异步处理 route 回放。
     *
     * hardening H-4:参数由 String? 升级为 [WidgetLaunchRoute],string prefix 解析已移至
     * [parseLaunchRoute](`WidgetIntentHelpers` 内)，此处只做 consent 分流。
     */
    private fun handleRawRoute(route: WidgetLaunchRoute?) {
        if (route == null) return
        val consentStore =
            EntryPointAccessors.fromActivity(this, MainActivityEntryPoint::class.java).consentStore()
        lifecycleScope.launch(Dispatchers.IO) {
            val consented = consentStore.isConsented(BuildConfig.CONSENT_VERSION)
            withContext(Dispatchers.Main) {
                if (consented) {
                    lastInitialRoute = route
                } else {
                    widgetPendingRoute.value = route
                }
            }
        }
    }
}
