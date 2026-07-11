@file:Suppress("FunctionNaming")

package com.yy.writingwithai.app

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.yy.writingwithai.BuildConfig
import com.yy.writingwithai.core.prefs.ConsentState
import com.yy.writingwithai.core.prefs.ConsentStore
import com.yy.writingwithai.core.prefs.UserPrefsStore
import com.yy.writingwithai.core.ui.animation.LocalAnimationTokens
import com.yy.writingwithai.core.widget.WidgetLaunchRoute
import com.yy.writingwithai.feature.aiwriting.AiwritingEntry
import com.yy.writingwithai.feature.aiwriting.usage.AiUsageScreen
import com.yy.writingwithai.feature.my.devmode.DeveloperModeScreen
import com.yy.writingwithai.feature.my.devmode.PromptEditorScreen
import com.yy.writingwithai.feature.my.entity.EntityDetailScreen
import com.yy.writingwithai.feature.my.entity.EntityManagementScreen
import com.yy.writingwithai.feature.onboarding.ApikeyPromptRoute
import com.yy.writingwithai.feature.onboarding.OnboardingEntry
import com.yy.writingwithai.feature.onboarding.OnboardingRoute
import com.yy.writingwithai.feature.quicknote.detail.QuickNoteDetailScreen
import com.yy.writingwithai.feature.quicknote.edit.QuickNoteEditorScreen
import com.yy.writingwithai.feature.quicknote.graph.NoteGraphScreen
import com.yy.writingwithai.feature.quicknote.lightbox.AttachmentLightboxScreen
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

/**
 * writing-with-ai · 应用 NavHost(M1 接入 quick-note-feature,M4-1 加 widget 启动参数，
 * M4-4 加 consent gate,app-bottom-tab-bar 改为底部 3 槽 tab shell)。
 *
 * 路由结构(review r1 L6 修后):
 * - [AppShell] 底部 tab 容器根路由(应用默认目的地;承载 Notes / Me 两个 tab 根屏)
 * - [QuicknoteDetail] 详情(`id` 为 Note.id)
 * - [QuicknoteEdit] 编辑(`id` 缺省或 "NEW" 视为新建;`prefillFocus` 用于 widget "新建"启动)
 * - [Notes] / [Me] tab 根屏(由 AppShell 内部子 NavHost 持有，不在根 NavHost 注册 composable)
 * - `onboarding/consent` 同意门(M4-4 新增)
 *
 * M4-4 改动(r1 H1 修):
 * - `widgetPendingRoute: MutableState<String?>` 由 `MainActivity` 写入，本函数在同意后
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
 * @deprecated `QuicknoteList` route 仍保留作 archive sync 对齐，不推荐新代码引用。
 */
@EntryPoint
@InstallIn(ActivityComponent::class)
internal interface AppNavEntryPoint {
    fun consentStore(): ConsentStore
    fun userPrefsStore(): UserPrefsStore
}

@Composable
fun AppNav(
    initialRoute: WidgetLaunchRoute? = null,
    widgetPendingRoute: MutableState<WidgetLaunchRoute?> = remember { mutableStateOf<WidgetLaunchRoute?>(null) },
    onNavControllerReady: (androidx.navigation.NavController) -> Unit = {},
    // fix-2026-06-25-review-r1 C5:让测试能注入 fake(默认走 Hilt EntryPoint)。
    // 不传时仍从 Activity EntryPoint 拿，MainActivity 走默认路径不变。
    consentStore: ConsentStore? = null,
    userPrefsStore: UserPrefsStore? = null
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // fix-global-back-nav-and-gesture: 把 navController 传给 Activity 层(用于 OnBackPressedCallback)
    // fix-full-review:SideEffect 每次重组都调用 onNavControllerReady，而回调内
    // addOnDestinationChangedListener 会累积 listener。改用 remember + 一次性赋值，
    // 确保 listener 只注册一次。
    val navControllerRef = remember { mutableStateOf<NavController?>(null) }
    if (navControllerRef.value == null) {
        navControllerRef.value = navController
        onNavControllerReady(navController)
    }

    // M4-4 · ConsentStore:测试优先用入参(避免 Robolectric 跑 Hilt EntryPoint);
    // 运行时 / MainActivity 走 Hilt ActivityComponent EntryPoint。
    val resolvedConsentStore: ConsentStore =
        consentStore
            ?: remember(context) {
                // review r2 修:安全转换 context as? Activity，非 Activity context(如 Preview)
                // 返回 null 时抛明确异常，而非 ClassCastException。
                val activity = context as? Activity
                    ?: error("AppNav requires Activity context, got ${context.javaClass.simpleName}")
                EntryPointAccessors.fromActivity(
                    activity,
                    AppNavEntryPoint::class.java
                ).consentStore()
            }

    // onboarding-apikey-prompt · 同上，UserPrefsStore 支持测试入参。
    val resolvedUserPrefsStore: UserPrefsStore =
        userPrefsStore
            ?: remember(context) {
                val activity = context as? Activity
                    ?: error("AppNav requires Activity context, got ${context.javaClass.simpleName}")
                EntryPointAccessors.fromActivity(
                    activity,
                    AppNavEntryPoint::class.java
                ).userPrefsStore()
            }

    // M4-4 · 启动时强制 gate:未同意或版本过期 → navigate onboarding + 清栈
    LaunchedEffect(Unit) {
        val state = resolvedConsentStore.consentFlow.first()
        val needsOnboarding = !state.accepted || state.version < BuildConfig.CONSENT_VERSION
        if (needsOnboarding) {
            navController.navigate(OnboardingEntry.ROUTE_CONSENT) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // M12 fix:widget pending route 解析提取 helper，避免 3 处重复 when 分支。
    // hardening H-4:用 sealed [WidgetLaunchRoute] 替代 string prefix 拼装，when 穷尽由编译器保证。
    // popUpToInclusive:consent 回放 / apikey-prompt 走 popUpTo(0)(清栈),
    // initialRoute 走 popUpTo(AppShell)(不清 consent 栈)。
    fun navigatePendingRoute(route: WidgetLaunchRoute?, popUpToInclusive: Boolean = true) {
        when (route) {
            null -> {
                navController.navigate(AppShell) { popUpTo(0) { inclusive = true } }
            }
            is WidgetLaunchRoute.NewNote -> {
                navController.navigate(QuicknoteEdit(id = "NEW", prefillFocus = true)) {
                    if (popUpToInclusive) popUpTo(0) { inclusive = true } else popUpTo(AppShell) { inclusive = true }
                }
            }
            is WidgetLaunchRoute.OpenNote -> {
                navController.navigate(QuicknoteDetail(route.noteId.toString())) {
                    if (popUpToInclusive) {
                        popUpTo(0) { inclusive = true }
                    } else {
                        popUpTo(AppShell) { inclusive = true }
                    }
                }
            }
            is WidgetLaunchRoute.EditNote -> {
                navController.navigate(QuicknoteEdit(id = "NEW", prefillFocus = route.prefillFocus)) {
                    if (popUpToInclusive) popUpTo(0) { inclusive = true } else popUpTo(AppShell) { inclusive = true }
                }
            }
            is WidgetLaunchRoute.Freewrite -> {
                navController.navigate(MorningFreewrite(date = route.date)) {
                    if (popUpToInclusive) popUpTo(0) { inclusive = true } else popUpTo(AppShell) { inclusive = true }
                }
            }
        }
    }

    // M4-4 · 同意后单向门 + widget route 回放(r1 H1 修)
    val consentState by resolvedConsentStore.consentFlow
        .collectAsStateWithLifecycle(initialValue = ConsentState.EMPTY)
    // onboarding-apikey-prompt · ack 状态镜像(用于二段门)
    val ackApikeyPrompt by resolvedUserPrefsStore.ackApikeyPromptFlow
        .collectAsStateWithLifecycle(initialValue = false)
    LaunchedEffect(consentState.accepted, consentState.version, ackApikeyPrompt) {
        if (consentState.accepted && consentState.version >= BuildConfig.CONSENT_VERSION) {
            val pending = widgetPendingRoute.value
            val currentRoute = navController.currentDestination?.route
            if (currentRoute?.contains("onboarding") == true) {
                // onboarding-apikey-prompt · 同意通过后，若 ack=false → 串到 apikey-prompt
                if (!ackApikeyPrompt) {
                    navController.navigate(OnboardingEntry.ROUTE_APIKEY_PROMPT) {
                        popUpTo(0) { inclusive = true }
                    }
                    return@LaunchedEffect
                }
                // widget 入口未同意时暂存的 route — 同意后回放
                widgetPendingRoute.value = null
                navigatePendingRoute(pending)
            }
        } else if (!consentState.accepted) {
            // review r2 修:DataStore 冷启动时 consentState 仍为初始值 EMPTY(accepted=false),
            // 已同意用户会短暂进入此分支闪现 onboarding 页。跳过 EMPTY 状态，
            // 等真实值到达后再决策(第一个 LaunchedEffect(Unit) 用 .first() 已正确等待)。
            if (consentState != ConsentState.EMPTY &&
                navController.currentDestination?.route?.contains("onboarding") != true
            ) {
                navController.navigate(OnboardingEntry.ROUTE_CONSENT) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    // animation-system · 导航过渡接 token(spec §REQ 4)。
    // NavHost transition lambda 不是 @Composable，无法直接读 CompositionLocal。
    // 解法:用 `remember { mutableStateOf(...) }` 持有可观察的 token 引用,
    // 每次重组时把最新的 LocalAnimationTokens.current 写入 state.value,
    // transition lambda 通过 `navTokensState.value` 读取 —— State 引用不变但 .value 会更新。
    // 注意:不能用 `key(navTokens)` 包裹 NavHost,因为 key 变化会销毁重建 NavHost,
    // 导致导航栈丢失(用户被弹回 startDestination)。
    val currentTokens = LocalAnimationTokens.current
    val navTokensState = remember { mutableStateOf(currentTokens) }
    navTokensState.value = currentTokens
    NavHost(
        navController = navController,
        startDestination = AppShell,
        enterTransition = { navTokensState.value.navEnter },
        exitTransition = { navTokensState.value.navExit },
        popEnterTransition = { navTokensState.value.navPopEnter },
        popExitTransition = { navTokensState.value.navPopExit }
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
                onNavigateToNote = { id -> navController.navigate(QuicknoteDetail(id)) },
                onNavigateToSettings = { navController.navigate(Settings) },
                // real-provider-integration §4:apikey-missing Snackbar action 跳模型管理
                onNavigateToModelManagement = { navController.navigate(SettingsModelManagement) },
                // note-graph-view §5.5:跳关联图屏(`NoteGraph(noteId)`)。
                onNavigateToGraph = { id -> navController.navigate(NoteGraph(id)) },
                // fix-review-r1 F2:缩略图 tap → 全屏 lightbox。
                onNavigateToLightbox = { id -> navController.navigate(AttachmentLightbox(id)) },
                onRequestConsent = {
                    AiwritingEntry.requestConsent(
                        navController
                    ) { nav -> OnboardingEntry.requestConsent(nav) }
                }
            )
        }
        // note-graph-view §5.4:关联图屏 route。`args.noteId` 由 Compose Navigation 类型安全解析
        // (`@Serializable NoteGraph(val noteId: String)`)。节点 tap 复用 `QuicknoteDetail(id)`,
        // 共享现有栈语义(D10:不开新 tab / 新窗口)。
        composable<NoteGraph> { backStackEntry ->
            val args = backStackEntry.toRoute<NoteGraph>()
            NoteGraphScreen(
                noteId = args.noteId,
                onBack = { navController.popBackStack() },
                onNodeTap = { id -> navController.navigate(QuicknoteDetail(id)) }
            )
        }
        // fix-review-r1 F2:全屏附件 lightbox 入口。`args.attachmentId` 由 Compose Navigation
        // 类型安全解析(`@Serializable AttachmentLightbox(val attachmentId: String)`),
        // VM 通过 `savedStateHandle` 接收。系统 back / 顶栏 Close / 单击背景均由
        // `onBack = popBackStack` 处理;不依赖 dialog,避免 predictive-back 冲突。
        composable<AttachmentLightbox> {
            AttachmentLightboxScreen(
                onBack = { navController.popBackStack() }
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
        // fix M62 (full-review):Settings 系列 12 个路由抽到 AppNavSettingsRoutes.kt,
        // 通过 NavGraphBuilder 扩展函数一次性注册(同包,无公开 API 变化)。
        settingsNavRoutes(navController)
        // entity-management-and-ai-decompose §7.1:实体管理 list
        composable<EntityManagement> {
            EntityManagementScreen(
                onBack = { navController.popBackStack() },
                onEntityClick = { key -> navController.navigate(EntityDetail(key)) }
            )
        }
        // §7.2:实体详情
        composable<EntityDetail> { backStackEntry ->
            val args = backStackEntry.toRoute<EntityDetail>()
            EntityDetailScreen(
                entityKey = args.entityKey,
                onBack = { navController.popBackStack() },
                onNavigateToNote = { id -> navController.navigate(QuicknoteDetail(id)) }
            )
        }
        // §7.3:开发者选项
        composable<DeveloperOptions> {
            DeveloperModeScreen(
                onBack = { navController.popBackStack() },
                onNavigateToPromptEditor = { navController.navigate(PromptEditor) }
            )
        }
        // §7.4:提示词编辑
        composable<PromptEditor> {
            PromptEditorScreen(onBack = { navController.popBackStack() })
        }
        // ai-usage-statistics §5:AI 用量统计页(从 MyScreen 数据管理区入口进入)
        composable<AiUsage> {
            AiUsageScreen(onBack = { navController.popBackStack() })
        }
        // morning-freewrite §3.4:沉浸晨写屏(通知点入 / widget 启动)
        composable<MorningFreewrite> { backStackEntry ->
            val args = backStackEntry.toRoute<MorningFreewrite>()
            com.yy.writingwithai.feature.freewrite.FreewriteEntry.MorningFreewriteRoute(
                date = args.date,
                onDismiss = { navController.popBackStack() }
            )
        }
        composable(OnboardingEntry.ROUTE_CONSENT) {
            OnboardingRoute(
                onExitApp = { /* OnboardingRoute 内部已 finishAffinity() */ }
            )
        }
        composable(OnboardingEntry.ROUTE_APIKEY_PROMPT) {
            ApikeyPromptRoute(
                onFinished = {
                    // M12 fix:复用 navigatePendingRoute 替代重复 when 分支。
                    // ack=true 后(VM 已写 DataStore)，此处手动 navigate 到主路由。
                    // 上面 LaunchedEffect(ackApikeyPrompt) 在 collect 触发新值后
                    // 也会再次 navigate，这里 popUpTo(0) 防止双跳。
                    val pending = widgetPendingRoute.value
                    widgetPendingRoute.value = null
                    navigatePendingRoute(pending)
                }
            )
        }
    }

    // M4-1 · widget 启动路由解析(已同意情况:走 initialRoute 路径;
    // 未同意情况:由 MainActivity 暂存到 widgetPendingRoute，本 LaunchedEffect
    // 在 consent 变 true 后统一处理。见上方"同意后单向门"块)。
    // M12 fix:复用 navigatePendingRoute 替代重复 when 分支，
    // popUpToInclusive=false → popUpTo(AppShell)(不清 consent 栈)。
    LaunchedEffect(initialRoute) {
        if (initialRoute == null) return@LaunchedEffect
        // review r2 修:consentFlow.value 在 DataStore 冷启动时返回 ConsentState.EMPTY,
        // 已同意用户从 widget 启动时路由被忽略。改为 .first() 挂起等待真实值。
        val state = resolvedConsentStore.consentFlow.first()
        if (state.accepted && state.version >= BuildConfig.CONSENT_VERSION) {
            navigatePendingRoute(initialRoute, popUpToInclusive = false)
        }
    }
}

@Serializable
data object QuicknoteList

/**
 * app-bottom-tab-bar · 底部 tab 容器根路由(类型安全)。
 * - `AppShell` 是根 NavHost 的 startDestination;`composable<AppShell>` 渲染 `AppShell(...)`。
 * - `Notes` / `Me` 是 AppShell 内部子 NavHost 的 tab 根路由(不由根 NavHost 注册 composable)。
 *   它们在这里声明，供 `AppShell.kt` 的 typed navigate / popUpTo 引用。
 */
@Serializable
data object AppShell

@Serializable
data object Notes

@Serializable
data object Me

@Serializable
data class QuicknoteDetail(val id: String)

/**
 * note-graph-view §5.4:关联图屏 route。`noteId` 是中心笔记 id(`Note.id`),
 * 由 `QuickNoteDetailScreen` overflow "查看关联图" 入口 `onNavigateToGraph(id)` 传入。
 */
@Serializable
data class NoteGraph(val noteId: String)

/**
 * fix-review-r1 F2:全屏附件 lightbox route。`attachmentId` 是 `NoteAttachmentEntity.id`,
 * 由 `InlineMarkdownText` 缩略图 tap → `QuickNoteDetailScreen.onNavigateToLightbox(id)` 传入。
 * `AttachmentLightboxViewModel` 从 `savedStateHandle[attachmentId]` 取该参数。
 */
@Serializable
data class AttachmentLightbox(val attachmentId: String)

@Serializable
data class QuicknoteEdit(val id: String? = "NEW", val prefillFocus: Boolean = false)

@Serializable
data object SettingsData

@Serializable
data object SettingsLanguage

@Serializable
data object Settings

@Serializable
data object SettingsPromptTemplate

@Serializable
data object SettingsAliasManagement

/**
 * feishu-import-from-folder:从文件夹导入 sub-screen route(纯 data object,无参数)。
 */
@Serializable
data object FeishuFolderImport

@Serializable
data object SettingsNoteAssociation

@Serializable
data object SettingsModelManagement

@Serializable
data class SettingsModelProviderDetail(val providerId: String)

@Serializable
data class SettingsCustomProviderEdit(val providerId: String? = null)

/**
 * animation-system-and-consent-redesign §11.2:动画风格设置 route(@Serializable data object)。
 * 由 MeTabTarget.SettingsAnimationStyle 经 `AppShell` 的 onNavigate 翻译，根 NavHost 注册
 * `composable<SettingsAnimationStyle>` 渲染 `AnimationStylePreviewScreen`。
 */
@Serializable
data object SettingsAnimationStyle

/**
 * animation-switch-redesign-followup §6.1:动画详细设置 route(@Serializable data object)。
 * 由 MeTabTarget.SettingsAnimationDetail 经 `AppShell` 的 onNavigate 翻译，根 NavHost 注册
 * `composable<SettingsAnimationDetail>` 渲染 `AnimationDetailScreen`(nav/tab 细分开关)。
 */
@Serializable
data object SettingsAnimationDetail

/** ux-2026-06-28 P6:飞书授权页专属 route(不再走 Settings hub)。 */
@Serializable
data object SettingsFeishu

/** entity-management-and-ai-decompose §7.1:实体管理 list route。 */
@Serializable
data object EntityManagement

/** entity-management-and-ai-decompose §7.2:实体详情 route。 */
@Serializable
data class EntityDetail(val entityKey: String)

/** entity-management-and-ai-decompose §7.3:开发者选项 route。 */
@Serializable
data object DeveloperOptions

/** entity-management-and-ai-decompose §7.4:提示词编辑 route。 */
@Serializable
data object PromptEditor

/** ai-usage-statistics §5:AI 用量统计 route(从 MyScreen 数据管理区进入)。 */
@Serializable
data object AiUsage

// ---- morning-freewrite ----

/**
 * morning-freewrite §3.4:沉浸晨写屏 route(date 是 ISO `yyyy-MM-dd`,
 * 来自 Notification PendingIntent extra route=freewrite/$date)。
 */
@Serializable
data class MorningFreewrite(val date: String)

/** morning-freewrite §2.3:「设置 → 每日晨写」配置页 route(无参数)。 */
@Serializable
data object SettingsFreewrite
