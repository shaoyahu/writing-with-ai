# app-shell

## Purpose

TBD — synced from OpenSpec change `init-android-project`(2026-06-18)。原 change 在 `openspec/changes/archive/2026-06-18-init-android-project/`。

应用入口骨架:`WritingApp`(`@HiltAndroidApp`)+ `MainActivity`(`ComponentActivity` + `setContent { App() }`)+ `AppNav.kt` 空 NavHost;`App()` 承载整个应用根 Composable。

## Requirements

### Requirement: Application class is Hilt-enabled

The app MUST define a single `Application` subclass annotated with `@HiltAndroidApp`, registered as the application class in `AndroidManifest.xml`.

#### Scenario: Hilt application registered
- **WHEN** the manifest `app/src/main/AndroidManifest.xml` is inspected
- **THEN** the `application` element has `android:name=".app.WritingApp"` (or fully-qualified `com.yy.writingwithai.app.WritingApp`)

#### Scenario: WritingApp class annotated
- **WHEN** the `WritingApp` class file is inspected
- **THEN** it is declared as `class WritingApp : Application()` AND it carries the `@HiltAndroidApp` annotation

### Requirement: MainActivity hosts a Compose root

The app MUST have a single `MainActivity` extending `ComponentActivity` whose `onCreate` calls `setContent { App() }`; the `App()` Composable is the root of the entire Compose tree.

#### Scenario: Single Activity entry
- **WHEN** the manifest is inspected
- **THEN** exactly one `<activity>` element exists (the launcher), with `android:name=".app.MainActivity"`

#### Scenario: Compose root invoked
- **WHEN** `MainActivity.onCreate` runs
- **THEN** it calls `setContent { App() }` where `App()` is a top-level `@Composable` function in the `app/` package

### Requirement: AppNav defines an empty NavHost

`AppNav.kt` MUST define a `NavHostController`-based `NavHost` with at least one placeholder route, ready for subsequent changes to add real destinations.

#### Scenario: NavHost instantiated
- **WHEN** the `App()` Composable is rendered
- **THEN** `AppNav(...)` is invoked inside the Material 3 themed surface AND the `NavHost` has at least one destination that displays a placeholder Composable (e.g., "writing-with-ai" greeting)

#### Scenario: Back press behavior is system-driven
- **WHEN** the user triggers system back gesture on the only destination
- **THEN** the activity finishes (predictive back gesture is wired by `enableOnBackInvokedCallback = true` in M0; system back handling is delegated to `NavHostController`; M4's `predictive-back-gesture` change will refine this)

#### Scenario: 所有非主页 destination TopAppBar 含 navigationIcon = ArrowBack

- **WHEN** 任何非 `QuicknoteList`(主页)的 Screen Composable 使用 `Scaffold(topBar = { TopAppBar(...) })`
- **THEN** `TopAppBar` MUST 含 `navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } }`;`onBack` MUST 是由 `AppNav` 传入的 `() -> Unit = { navController.popBackStack() }`(或等价)
- 当前覆盖清单:`QuickNoteDetailScreen` / `QuickNoteEditorScreen` / `SettingsDataScreen` / `SettingsScreen` / `ModelManagementScreen` / `ModelProviderDetailScreen` / `PromptTemplateScreen`;`OnboardingRoute` 全屏滚动无 TopAppBar 豁免;`QuickNoteListScreen` 主页豁免(无上一页)
- 自动化校验:`grep -rE "topBar = \{" app/src/main/java/com/yy/writingwithai/feature/ | wc -l` 应等于含 ArrowBack 的屏数;`grep -rE "SettingsScreen\.kt" "navigationIcon" app/src/main/java/com/yy/writingwithai/feature/settings/` 至少 1 匹配

## MODIFIED Requirements (M4-4 onboarding-consent)

### Requirement: AppNav ConsentGate routes unauthenticated users to onboarding

`AppNav.kt` MUST 在 `NavHost` 启动时读 `ConsentStore.consentFlow.first()`,未同意或同意版本号过期 → 强制 navigate `onboarding/consent` 路由 + `popUpTo(0) { inclusive = true }`(清空 back stack);同意后 navigate 主路由 + 清栈(防止 back 回到 onboarding)。`LaunchedEffect(Unit)` 内完成判断,不阻塞主屏 Composable 首次渲染。

`MainActivity.onCreate` / `onNewIntent` 在解析 `intent.getStringExtra("route")` 之前 MUST 异步查 `ConsentStore.isConsented()`(走 `lifecycleScope.launch(Dispatchers.IO)` + `withContext(Dispatchers.Main)`,不在主线程 block),未同意 → 改 navigate `onboarding/consent` + 暂存 widget 启动 route 到 `AppNav` 的 `widgetPendingRoute: MutableState<String?>` state(由 `App` Composable 持有,跨 Activity 重建),同意后 `AppNav.LaunchedEffect(consentState)` 监听到 navigate 该 route + 清栈。

#### Scenario: 冷启未同意
- **WHEN** App 冷启,`ConsentStore.consentAccepted = false`
- **THEN** `AppNav.LaunchedEffect` 触发 `navigate("onboarding/consent") { popUpTo(0) { inclusive = true } }`;主路由不渲染

#### Scenario: 冷启已同意
- **WHEN** App 冷启,`ConsentStore.consentAccepted = true` 且版本号匹配
- **THEN** `AppNav.LaunchedEffect` 不触发 navigate;`NavHost` 直接渲染主路由(随手记列表)

#### Scenario: 同意后单向进入主路由
- **WHEN** 用户在 `OnboardingScreen` 点"同意并继续",`OnboardingViewModel.accept()` 完成
- **THEN** `AppNav` 监听到 `consentFlow` 变 `accepted=true` → `navigate("quicknote/list") { popUpTo(0) { inclusive = true } }`;back 不可回 onboarding

#### Scenario: widget 入口未同意时改走 onboarding + 同意后回放
- **WHEN** App 收到 M4-1 widget 的 PendingIntent 启动,`intent.getStringExtra("route") = "quicknote/edit?prefillFocus=true"`,`ConsentStore.isConsented() = false`
- **THEN** `MainActivity.onCreate` 写 `widgetPendingRoute = "quicknote/edit?prefillFocus=true"` + navigate `onboarding/consent`;`OnboardingViewModel.accept()` 后 `AppNav.LaunchedEffect(consentState)` 监听到 consent + 检查 `widgetPendingRoute.value` 非空 → navigate 该 route + 清栈 + 清 widgetPendingRoute

#### Scenario: 升级条款版本强制重同
- **WHEN** `R.integer.consent_version` 从 1 bump 到 2,`ConsentStore.consentVersion = 1`(已同意旧版)
- **THEN** `AppNav.LaunchedEffect` 判定 `consentVersion < CURRENT_CONSENT_VERSION` → navigate `onboarding/consent`;`OnboardingScreen` 重新显示新版条款

#### Scenario: 撤回同意后回 onboarding
- **WHEN** `ConsentStore.setAccepted(version=0, at=0L)` 调用(撤回),App 在前台
- **THEN** `AppNav` 监听到 `consentFlow` 变 `accepted=false` → navigate `onboarding/consent` + 清栈;主路由自动 pop

#### Scenario: onNewIntent 闸门
- **WHEN** App 已在主路由,新的 widget Intent 触发 `onNewIntent` 启动,`ConsentStore.isConsented() = false`
- **THEN** `MainActivity.onNewIntent` 同步 `onCreate` 逻辑:写 widgetPendingRoute + navigate `onboarding/consent`;已同意则更新 `lastInitialRoute` 让 AppNav 解析

#### Scenario: consent check uses IO dispatcher(M5 polish)
- **WHEN** `MainActivity.handleRawRoute` 被调用
- **THEN** `consentStore.isConsented` 调用在 `Dispatchers.IO` 协程调度器(不在主线程 `runBlocking`);主线程无阻塞;fire-and-forget route 决策

#### Scenario: Robolectric test covers MainActivity consent gate(M5 polish)
- **WHEN** `app/src/test/java/com/yy/writingwithai/app/` 检查
- **THEN** 存在 `SecureApiKeyStoreRobolectricTest.kt` 或等价 test 覆盖 E-SP roundtrip + reveal + lifecycle pause 行为

#### Scenario: Settings / SettingsPromptTemplate route 注册(custom-prompt-template)
- **WHEN** grep `AppNav.kt` "Settings"
- **THEN** 至少 1 个 `@Serializable data object Settings` + 1 个 `@Serializable data object SettingsPromptTemplate` + 2 个 `composable<...>` block

#### Scenario: widget Intent 不进 Settings 路径(custom-prompt-template)
- **WHEN** widget PendingIntent extra `route = "quicknote/edit?prefillFocus=true"` 或 `"quicknote/detail/{id}"`
- **THEN** AppNav 解析走 quicknote route,不进 Settings / SettingsPromptTemplate

#### Scenario: Settings 入口在 QuickNoteListScreen overflow menu(custom-prompt-template)
- **WHEN** 用户在 QuickNoteListScreen TopAppBar overflow menu 点"设置"
- **THEN** `navController.navigate(Settings)` 跳 SettingsScreen;从 SettingsScreen 走"AI 提示词模板" → `navController.navigate(SettingsPromptTemplate)`

### Requirement: AppNav startDestination is AppShell with My tab (app-bottom-tab-bar)

`AppNav.kt` MUST 把 `startDestination` 由 `QuicknoteList` 改为新引入的 `AppShell` route;`AppShell` 是承载底部 tab bar 的容器 Composable(`app/AppShell.kt`),内部嵌入子 NavHost 渲染 `Notes` / `Me` 两个 tab 根屏。

#### Scenario: AppNav startDestination 已切到 AppShell
- **WHEN** grep `AppNav.kt` `startDestination`
- **THEN** 值为 `AppShell`(不是 `QuicknoteList`);`composable<AppShell>` block 存在并渲染 `AppShell(...)`

#### Scenario: Settings 入口迁移到"我的" tab
- **WHEN** grep `AppNav.kt` "navigate(Settings" / `navigate(SettingsData`
- **THEN** 所有 navigate 调用来自 `feature/my/MyScreen.kt`(经 `onNavigate` lambda),**不**来自 `feature/quicknote/list/QuickNoteListScreen.kt`

#### Scenario: widget pending route 回放 popUpTo 锚点已切到 AppShell
- **WHEN** grep `AppNav.kt` `popUpTo(QuicknoteList)`
- **THEN** 0 匹配;`popUpTo(AppShell)` 至少 1 匹配(用于 widget 启动回放与 tab 切换)
