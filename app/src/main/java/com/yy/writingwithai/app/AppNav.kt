@file:Suppress("FunctionNaming")

package com.yy.writingwithai.app

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.yy.writingwithai.BuildConfig
import com.yy.writingwithai.core.prefs.ConsentState
import com.yy.writingwithai.core.prefs.ConsentStore
import com.yy.writingwithai.core.prefs.UserPrefsStore
import com.yy.writingwithai.feature.onboarding.ApikeyPromptRoute
import com.yy.writingwithai.feature.onboarding.OnboardingEntry
import com.yy.writingwithai.feature.onboarding.OnboardingRoute
import com.yy.writingwithai.feature.quicknote.detail.QuickNoteDetailScreen
import com.yy.writingwithai.feature.quicknote.edit.QuickNoteEditorScreen
import com.yy.writingwithai.feature.settings.SettingsEntry
import com.yy.writingwithai.feature.settings.alias.AliasManagementScreen
import com.yy.writingwithai.feature.settings.data.SettingsDataScreen
import com.yy.writingwithai.feature.settings.feishu.FeishuAuthScreen
import com.yy.writingwithai.feature.settings.model.ModelManagementEntry
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

/**
 * writing-with-ai · 应用 NavHost(M1 接入 quick-note-feature,M4-1 加 widget 启动参数,
 * M4-4 加 consent gate,app-bottom-tab-bar 改为底部 3 槽 tab shell)。
 *
 * 路由结构(review r1 L6 修后):
 * - [AppShell] 底部 tab 容器根路由(应用默认目的地;承载 Notes / Me 两个 tab 根屏)
 * - [QuicknoteDetail] 详情(`id` 为 Note.id)
 * - [QuicknoteEdit] 编辑(`id` 缺省或 "NEW" 视为新建;`prefillFocus` 用于 widget "新建"启动)
 * - [Notes] / [Me] tab 根屏(由 AppShell 内部子 NavHost 持有,不在根 NavHost 注册 composable)
 * - `onboarding/consent` 同意门(M4-4 新增)
 *
 * M4-4 改动(r1 H1 修):
 * - `widgetPendingRoute: MutableState<String?>` 由 `MainActivity` 写入,本函数在同意后
 *   navigate 该 route + 清栈(防 back 回 onboarding)+ 清 widgetPendingRoute
 * - 启动时 `LaunchedEffect(Unit) { consentStore.consentFlow.first() }` → 未同意或版本过期
 *   → `navController.navigate("onboarding/consent") { popUpTo(0) }` 强制走同意页
 * - 同意后 `LaunchedEffect(consentState)` 监听到 `accepted && version >= CURRENT` →
 *   `navigate(AppShell) { popUpTo(0) }` 单向门 + widgetPendingRoute 回放
 *   (review r1 修:原 `QuicknoteList` 改 `AppShell`)
 *
 * app-bottom-tab-bar 改动:
 * - `startDestination = AppShell`(原 `QuicknoteList`)
 * - widget pending route 回放的 popUpTo 锚点由 `QuicknoteList` 切到 `AppShell`
 * - `composable<AppShell>` block 渲染 `AppShell(...)`(同 package `com.yy.writingwithai.app`)
 *
 * @deprecated `QuicknoteList` route 仍保留作 archive sync 对齐,不推荐新代码引用。
 */
@EntryPoint
@InstallIn(ActivityComponent::class)
internal interface AppNavEntryPoint {
    fun consentStore(): ConsentStore
    fun userPrefsStore(): UserPrefsStore
}

@Composable
fun AppNav(
    initialRoute: String? = null,
    widgetPendingRoute: MutableState<String?> = remember { mutableStateOf<String?>(null) },
    onNavControllerReady: (androidx.navigation.NavController) -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // fix-global-back-nav-and-gesture: 把 navController 传给 Activity 层(用于 OnBackPressedCallback)
    androidx.compose.runtime.SideEffect { onNavControllerReady(navController) }

    // M4-4 · 从 Activity 拿 ConsentStore(避免在 Composable 显式传 hiltViewModel)
    val consentStore =
        remember(context) {
            EntryPointAccessors.fromActivity(
                context as Activity,
                AppNavEntryPoint::class.java
            ).consentStore()
        }

    // onboarding-apikey-prompt · 同样从 Activity 拿 UserPrefsStore(走 EntryPoint)
    val userPrefsStore =
        remember(context) {
            EntryPointAccessors.fromActivity(
                context as Activity,
                AppNavEntryPoint::class.java
            ).userPrefsStore()
        }

    // M4-4 · 启动时强制 gate:未同意或版本过期 → navigate onboarding + 清栈
    LaunchedEffect(Unit) {
        val state = consentStore.consentFlow.first()
        val needsOnboarding = !state.accepted || state.version < BuildConfig.CONSENT_VERSION
        if (needsOnboarding) {
            navController.navigate(OnboardingEntry.ROUTE_CONSENT) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // M4-4 · 同意后单向门 + widget route 回放(r1 H1 修)
    val consentState by consentStore.consentFlow.collectAsState(initial = ConsentState.EMPTY)
    // onboarding-apikey-prompt · ack 状态镜像(用于二段门)
    val ackApikeyPrompt by userPrefsStore.ackApikeyPromptFlow
        .collectAsState(initial = false)
    LaunchedEffect(consentState.accepted, consentState.version, ackApikeyPrompt) {
        if (consentState.accepted && consentState.version >= BuildConfig.CONSENT_VERSION) {
            val pending = widgetPendingRoute.value
            val currentRoute = navController.currentDestination?.route
            if (currentRoute?.contains("onboarding") == true) {
                // onboarding-apikey-prompt · 同意通过后,若 ack=false → 串到 apikey-prompt
                if (!ackApikeyPrompt) {
                    navController.navigate(OnboardingEntry.ROUTE_APIKEY_PROMPT) {
                        popUpTo(0) { inclusive = true }
                    }
                    return@LaunchedEffect
                }
                if (pending != null) {
                    // widget 入口未同意时暂存的 route — 同意后回放
                    widgetPendingRoute.value = null
                    when {
                        pending.startsWith("quicknote/edit") -> {
                            val prefill = pending.contains("prefillFocus=true")
                            navController.navigate(QuicknoteEdit(id = "NEW", prefillFocus = prefill)) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                        pending.startsWith("quicknote/detail/") -> {
                            val id = pending.removePrefix("quicknote/detail/")
                            if (id.isNotBlank()) {
                                navController.navigate(QuicknoteDetail(id)) {
                                    popUpTo(0) { inclusive = true }
                                }
                            } else {
                                navController.navigate(AppShell) { popUpTo(0) { inclusive = true } }
                            }
                        }
                        else -> {
                            navController.navigate(AppShell) { popUpTo(0) { inclusive = true } }
                        }
                    }
                } else {
                    navController.navigate(AppShell) { popUpTo(0) { inclusive = true } }
                }
            }
        } else if (!consentState.accepted) {
            // 撤回同意 → 强制回 onboarding
            if (navController.currentDestination?.route?.contains("onboarding") != true) {
                navController.navigate(OnboardingEntry.ROUTE_CONSENT) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppShell
    ) {
        composable<AppShell> {
            AppShell(
                rootNavController = navController,
                onCreateClick = { navController.navigate(QuicknoteEdit()) }
            )
        }
        composable<QuicknoteDetail> {
            QuickNoteDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(QuicknoteEdit(id)) },
                onDeleted = { navController.popBackStack() },
                navController = navController
            )
        }
        composable<QuicknoteEdit> { backStackEntry ->
            val args = backStackEntry.toRoute<QuicknoteEdit>()
            QuickNoteEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { _ ->
                    navController.popBackStack()
                },
                prefillFocus = args.prefillFocus
            )
        }
        composable<SettingsData> {
            SettingsDataScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable<Settings> {
            SettingsEntry.SettingsRoute(
                onBack = { navController.popBackStack() }
            )
        }
        composable<SettingsPromptTemplate> {
            SettingsEntry.PromptTemplateRoute(
                onBack = { navController.popBackStack() }
            )
        }
        composable<SettingsAliasManagement> {
            AliasManagementScreen(onBack = { navController.popBackStack() })
        }
        composable<SettingsModelManagement> {
            ModelManagementEntry.ModelManagementRoute(
                onProviderClick = { id -> navController.navigate(SettingsModelProviderDetail(id)) },
                onCreateCustomClick = { navController.navigate(SettingsCustomProviderEdit(null)) },
                onEditCustomClick = { id -> navController.navigate(SettingsCustomProviderEdit(id)) },
                onBack = { navController.popBackStack() }
            )
        }
        composable<SettingsModelProviderDetail> { backStackEntry ->
            val args = backStackEntry.toRoute<SettingsModelProviderDetail>()
            ModelManagementEntry.ModelProviderDetailRoute(
                providerId = args.providerId,
                onBack = { navController.popBackStack() }
            )
        }
        composable<SettingsCustomProviderEdit> { backStackEntry ->
            val args = backStackEntry.toRoute<SettingsCustomProviderEdit>()
            ModelManagementEntry.CustomProviderEditRoute(
                providerId = args.providerId,
                onBack = { navController.popBackStack() }
            )
        }
        composable<FeishuAuth> {
            // 飞书 OAuth 授权(app_id / app_secret)。从 MyScreen "飞书同步" 入口进。
            // FeishuAuthScreen 自带 TopAppBar + ArrowBack,与 Settings 其他二级页一致。
            FeishuAuthScreen(onBack = { navController.popBackStack() })
        }
        composable(OnboardingEntry.ROUTE_CONSENT) {
            OnboardingRoute(
                onExitApp = { /* OnboardingRoute 内部已 finishAffinity() */ }
            )
        }
        composable(OnboardingEntry.ROUTE_APIKEY_PROMPT) {
            ApikeyPromptRoute(
                onFinished = {
                    // ack=true 后(VM 已写 DataStore),此处手动 navigate 到主路由。
                    // 上面 LaunchedEffect(ackApikeyPrompt) 在 collect 触发新值后
                    // 也会再次 navigate,这里 popUpTo(0) 防止双跳。
                    val pending = widgetPendingRoute.value
                    if (pending != null) {
                        widgetPendingRoute.value = null
                        when {
                            pending.startsWith("quicknote/edit") -> {
                                val prefill = pending.contains("prefillFocus=true")
                                navController.navigate(QuicknoteEdit(id = "NEW", prefillFocus = prefill)) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                            pending.startsWith("quicknote/detail/") -> {
                                val id = pending.removePrefix("quicknote/detail/")
                                if (id.isNotBlank()) {
                                    navController.navigate(QuicknoteDetail(id)) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                } else {
                                    navController.navigate(AppShell) { popUpTo(0) { inclusive = true } }
                                }
                            }
                            else -> {
                                navController.navigate(AppShell) { popUpTo(0) { inclusive = true } }
                            }
                        }
                    } else {
                        navController.navigate(AppShell) { popUpTo(0) { inclusive = true } }
                    }
                }
            )
        }
    }

    // M4-1 · widget 启动路由解析(已同意情况:走 initialRoute 路径;
    // 未同意情况:由 MainActivity 暂存到 widgetPendingRoute,本 LaunchedEffect
    // 在 consent 变 true 后统一处理。见上方"同意后单向门"块)。
    LaunchedEffect(initialRoute) {
        if (initialRoute == null) return@LaunchedEffect
        // 已同意且 initialRoute 给出 → 直接 navigate
        val state = consentStore.consentFlow.value
        if (state.accepted && state.version >= BuildConfig.CONSENT_VERSION) {
            when {
                initialRoute.startsWith("quicknote/edit") -> {
                    val prefill = initialRoute.contains("prefillFocus=true")
                    navController.navigate(QuicknoteEdit(id = "NEW", prefillFocus = prefill)) {
                        popUpTo(AppShell) { inclusive = true }
                    }
                }
                initialRoute.startsWith("quicknote/detail/") -> {
                    val id = initialRoute.removePrefix("quicknote/detail/")
                    if (id.isNotBlank()) {
                        navController.navigate(QuicknoteDetail(id)) {
                            popUpTo(AppShell) { inclusive = true }
                        }
                    }
                }
            }
        }
    }
}

@Serializable
data object QuicknoteList

/**
 * app-bottom-tab-bar · 底部 tab 容器根路由(类型安全)。
 * - `AppShell` 是根 NavHost 的 startDestination;`composable<AppShell>` 渲染 `AppShell(...)`。
 * - `Notes` / `Me` 是 AppShell 内部子 NavHost 的 tab 根路由(不由根 NavHost 注册 composable)。
 *   它们在这里声明,供 `AppShell.kt` 的 typed navigate / popUpTo 引用。
 */
@Serializable
data object AppShell

@Serializable
data object Notes

@Serializable
data object Me

@Serializable
data class QuicknoteDetail(val id: String)

@Serializable
data class QuicknoteEdit(val id: String? = "NEW", val prefillFocus: Boolean = false)

@Serializable
data object SettingsData

@Serializable
data object Settings

@Serializable
data object SettingsPromptTemplate

@Serializable
data object SettingsAliasManagement

@Serializable
data object SettingsModelManagement

@Serializable
data class SettingsModelProviderDetail(val providerId: String)

@Serializable
data class SettingsCustomProviderEdit(val providerId: String? = null)

/**
 * app-bottom-tab-bar 增量 · 飞书 OAuth 授权屏路由。
 * MyScreen 的"飞书同步"入口指向此路由,渲染 FeishuAuthScreen(app_id / app_secret 输入 + 连接/断开)。
 */
@Serializable
data object FeishuAuth
