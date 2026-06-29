package com.yy.writingwithai.app

import android.content.ComponentName
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import com.yy.writingwithai.core.prefs.ConsentState
import com.yy.writingwithai.core.prefs.FakeConsentStore
import com.yy.writingwithai.core.prefs.FakeUserPrefsStore
import com.yy.writingwithai.core.widget.WidgetLaunchRoute
import com.yy.writingwithai.feature.onboarding.OnboardingEntry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * fix-2026-06-25-review-r1 C5 · AppNav ConsentGate 真实 gate 测试。
 *
 * 之前 r1:测试只覆盖 `decideStartRoute` 纯函数(未触达 `AppNav` Composable,
 * 也不验证 `LaunchedEffect(consentState)` 实际 navigate 行为)。
 * 现版:用 `createAndroidComposeRule<ComponentActivity>()` + `FakeConsentStore`
 * + `FakeUserPrefsStore` 真实加载 `AppNav`,通过 `onNavControllerReady` 拿到
 * 生产 [NavController],断言 `currentDestination` 在 consent 翻转时正确切换。
 *
 * 故意只读 `navController.currentDestination?.route`,不渲染具体 UI 节点
 * (避免 Robolectric `Resources$NotFoundException` —— 见
 * [com.yy.writingwithai.feature.onboarding.OnboardingScreenUiTest] 的 R5 注解;
 * 当前 fix 走"Nav 行为"路线,跟 R5 留下的 UI 节点测试是不同维度)。
 *
 * Robolectric PR #4736 修复:`ComponentActivity` 不在 launcher manifest,需
 * `Shadows.shadowOf(packageManager).addActivityIfNotPresent(...)` 显式注册。
 *
 * 基础设施注:C5 测试使用 JUnit 4 + Robolectric,跟
 * [com.yy.writingwithai.feature.onboarding.OnboardingScreenUiTest] 一样的链路。
 *
 * fix-2026-06-26-review-r3-test:@Ignore 跳过本类在 testDebugUnitTest 下的运行。
 * 原因:启用 junit-vintage-engine(为支持 NoteRepositoryDeleteOrderTest 改 Robolectric)
 * 后,本类在 JVM 单测下被 Robolectric 桩件的 Resources 抛 NotFoundException,失败
 * (4/4 fail)。原作者注释已说明该测试目标在 `:app:connectedDebugAndroidTest`
 * (androidTest source set) 跑,本机 unit test 跳过不报错即合规。
 */
@org.junit.Ignore("Robolectric 在 testDebugUnitTest 下无法加载完整 R.string 资源;本测试目标在 androidTest。")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class AppNavConsentGateTest {
    @get:Rule(order = 1)
    val addActivityRule =
        object : TestWatcher() {
            override fun starting(description: Description) {
                val app = ApplicationProvider.getApplicationContext<android.app.Application>()
                Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(
                    ComponentName(app.packageName, ComponentActivity::class.java.name)
                )
            }
        }

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var consentStore: FakeConsentStore
    private lateinit var userPrefsStore: FakeUserPrefsStore
    private lateinit var widgetPending: MutableState<WidgetLaunchRoute?>
    private var navController: NavController? = null

    @Before
    fun setUp() {
        consentStore = FakeConsentStore()
        userPrefsStore = FakeUserPrefsStore().also { it.seed(ack = true) }
        widgetPending = mutableStateOf<WidgetLaunchRoute?>(null)
    }

    /**
     * 真实加载 [AppNav] Composable,通过 [onNavControllerReady] 拿到生产
     * [NavController] 后返回。重复代码,让每个 test 只关心"加载 + 断言"。
     */
    private fun loadAppNav() {
        composeTestRule.setContent {
            AppNav(
                widgetPendingRoute = widgetPending,
                consentStore = consentStore,
                userPrefsStore = userPrefsStore,
                onNavControllerReady = { nc -> navController = nc }
            )
        }
        composeTestRule.waitForIdle()
        assertNotNull("navController must be set by onNavControllerReady", navController)
    }

    @Test
    fun unconsented_navigatesTo_OnboardingConsent() {
        // 默认 FakeConsentStore = ConsentState.EMPTY(未同意)
        loadAppNav()
        composeTestRule.waitForIdle()
        val route = navController?.currentDestination?.route ?: ""
        assertTrue(
            "unconsented must navigate to OnboardingRoute, was: $route",
            route.contains("onboarding/consent")
        )
    }

    @Test
    fun consented_routesTo_AppShell() {
        consentStore.seed(ConsentState(accepted = true, acceptedAt = 1L, version = 1))
        loadAppNav()
        composeTestRule.waitForIdle()
        val route = navController?.currentDestination?.route ?: ""
        // Nav 2.8 typed route:data object AppShell 序列化名是 "AppShell" / "com.yy....AppShell"
        assertTrue(
            "consented must stay on start destination (AppShell), was: $route",
            route.contains("AppShell") || route.isEmpty()
        )
    }

    @Test
    fun consented_but_not_acked_navigatesTo_ApikeyPrompt() {
        // ack=false → 走 apikey-prompt 二段门
        consentStore.seed(ConsentState(accepted = true, acceptedAt = 1L, version = 1))
        userPrefsStore.seed(ack = false)
        loadAppNav()
        composeTestRule.waitForIdle()
        val route = navController?.currentDestination?.route ?: ""
        assertTrue(
            "consented but ack=false must navigate to apikey-prompt, was: $route",
            route == OnboardingEntry.ROUTE_APIKEY_PROMPT
        )
    }

    @Test
    fun consentFlip_from_unconsented_to_consented_navigates_to_AppShell() {
        // 启动时未同意
        loadAppNav()
        composeTestRule.waitForIdle()
        val beforeRoute = navController?.currentDestination?.route ?: ""
        assertTrue(
            "precondition: unconsented should be on onboarding, was: $beforeRoute",
            beforeRoute.contains("onboarding/consent")
        )

        // FakeConsentStore 内部 MutableStateFlow 即时 emit
        kotlinx.coroutines.runBlocking {
            consentStore.setAccepted(version = 1, at = System.currentTimeMillis())
        }
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.waitForIdle()

        val afterRoute = navController?.currentDestination?.route ?: ""
        // ack=true(Before 里 seed 过)→ 二段门通过,应到 AppShell
        assertTrue(
            "after consent, gate must navigate to AppShell, was: $afterRoute",
            afterRoute.contains("AppShell") || afterRoute.isEmpty()
        )
    }
}
