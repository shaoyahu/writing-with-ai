## MODIFIED Requirements

### Requirement: AppNav ConsentGate routes unauthenticated users to onboarding

`AppNav.kt` MUST 在 `NavHost` 启动时读 `ConsentStore.consentFlow.first()`，未同意或同意版本号过期 → 强制 navigate `onboarding/consent` 路由 + `popUpTo(0) { inclusive = true }`(清空 back stack);同意后 navigate 主路由 + 清栈(防止 back 回到 onboarding)。`LaunchedEffect(Unit)` 内完成判断，不阻塞主屏 Composable 首次渲染。

`MainActivity.onCreate` / `onNewIntent` 在解析 `intent.getStringExtra("route")` 之前 MUST 异步查 `ConsentStore.isConsented()`(走 `lifecycleScope.launch(Dispatchers.IO)` + `withContext(Dispatchers.Main)`，不在主线程 block)，未同意 → 改 navigate `onboarding/consent` + 暂存 widget 启动 route 到 `AppNav` 的 `widgetPendingRoute: MutableState<String?>` state(由 `App` Composable 持有，跨 Activity 重建)，同意后 `AppNav.LaunchedEffect(consentState)` 监听到 navigate 该 route + 清栈。

#### Scenario: 冷启未同意
- **WHEN** App 冷启，`ConsentStore.consentAccepted = false`
- **THEN** `AppNav.LaunchedEffect` 触发 `navigate("onboarding/consent") { popUpTo(0) { inclusive = true } }`;主路由不渲染

#### Scenario: 冷启已同意
- **WHEN** App 冷启，`ConsentStore.consentAccepted = true` 且版本号匹配
- **THEN** `AppNav.LaunchedEffect` 不触发 navigate;`NavHost` 直接渲染主路由(随手记列表)

#### Scenario: 同意后单向进入主路由
- **WHEN** 用户在 `OnboardingScreen` 点"同意并继续",`OnboardingViewModel.accept()` 完成
- **THEN** `AppNav` 监听到 `consentFlow` 变 `accepted=true` → `navigate("quicknote/list") { popUpTo(0) { inclusive = true } }`;back 不可回 onboarding

#### Scenario: widget 入口未同意时改走 onboarding + 同意后回放
- **WHEN** App 收到 M4-1 widget 的 PendingIntent 启动，`intent.getStringExtra("route") = "quicknote/edit?prefillFocus=true"`,`ConsentStore.isConsented() = false`
- **THEN** `MainActivity.onCreate` 写 `widgetPendingRoute = "quicknote/edit?prefillFocus=true"` + navigate `onboarding/consent`;`OnboardingViewModel.accept()` 后 `AppNav.LaunchedEffect(consentState)` 监听到 consent + 检查 `widgetPendingRoute.value` 非空 → navigate 该 route + 清栈 + 清 widgetPendingRoute

#### Scenario: 升级条款版本强制重同
- **WHEN** `R.integer.consent_version` 从 1 bump 到 2,`ConsentStore.consentVersion = 1`(已同意旧版)
- **THEN** `AppNav.LaunchedEffect` 判定 `consentVersion < CURRENT_CONSENT_VERSION` → navigate `onboarding/consent`;`OnboardingScreen` 重新显示新版条款

#### Scenario: 撤回同意后回 onboarding
- **WHEN** `ConsentStore.setAccepted(version=0, at=0L)` 调用(撤回),App 在前台
- **THEN** `AppNav` 监听到 `consentFlow` 变 `accepted=false` → navigate `onboarding/consent` + 清栈;主路由自动 pop

#### Scenario: onNewIntent 闸门
- **WHEN** App 已在主路由，新的 widget Intent 触发 `onNewIntent` 启动，`ConsentStore.isConsented() = false`
- **THEN** `MainActivity.onNewIntent` 同步 `onCreate` 逻辑:写 widgetPendingRoute + navigate `onboarding/consent`;已同意则更新 `lastInitialRoute` 让 AppNav 解析

#### Scenario: consent check uses IO dispatcher(M5)
- **WHEN** `MainActivity.handleRawRoute` 被调用
- **THEN** `consentStore.isConsented` 调用在 `Dispatchers.IO` 协程调度器(不在主线程 `runBlocking`);`navigate` 在 `withContext(Dispatchers.Main)` 回到主线程;主线程无阻塞

#### Scenario: Robolectric test covers MainActivity consent gate
- **WHEN** `app/src/test/java/com/yy/writingwithai/app/MainActivityConsentGateTest.kt` 文件存在
- **THEN** 包含 `@RunWith(AndroidJUnit4::class) @Config(sdk = [34])` + 至少 3 个 test(未同意 `/` 已同意 `/` onNewIntent 未同意)
