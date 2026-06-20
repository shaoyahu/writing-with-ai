## MODIFIED Requirements

### Requirement: AppNav ConsentGate routes unauthenticated users to onboarding

`AppNav.kt` MUST 在 `NavHost` 启动时读 `ConsentStore.consentFlow.first()`,未同意或同意版本号过期 → 强制 navigate `onboarding/consent` 路由 + `popUpTo(0) { inclusive = true }`(清空 back stack);同意后 navigate 主路由 + 清栈(防止 back 回到 onboarding)。`LaunchedEffect(Unit)` 内完成判断,不阻塞主屏 Composable 首次渲染。

`MainActivity.onCreate` / `onNewIntent` 在解析 `intent.getStringExtra("route")` 之前 MUST 同步查 `ConsentStore.isConsented()`(走 runBlocking + DataStore first(),冷启可接受),未同意 → 改 navigate `onboarding/consent` + 暂存 `pendingRoute` 到 `MainActivity` 字段,同意后回放。

#### Scenario: 冷启未同意
- **WHEN** App 冷启,`ConsentStore.consentAccepted = false`
- **THEN** `AppNav.LaunchedEffect` 触发 `navigate("onboarding/consent") { popUpTo(0) { inclusive = true } }`;主路由不渲染

#### Scenario: 冷启已同意
- **WHEN** App 冷启,`ConsentStore.consentAccepted = true` 且版本号匹配
- **THEN** `AppNav.LaunchedEffect` 不触发 navigate;`NavHost` 直接渲染主路由(随手记列表)

#### Scenario: 同意后单向进入主路由
- **WHEN** 用户在 `OnboardingScreen` 点"同意并继续",`OnboardingViewModel.accept()` 完成
- **THEN** `AppNav` 监听到 `consentFlow` 变 `accepted=true` → `navigate("quicknote/list") { popUpTo(0) { inclusive = true } }`;back 不可回 onboarding

#### Scenario: widget 入口未同意时改走 onboarding
- **WHEN** App 收到 M4-1 widget 的 PendingIntent 启动,`intent.getStringExtra("route") = "quicknote/edit?prefillFocus=true"`,`ConsentStore.isConsented() = false`
- **THEN** `MainActivity.onCreate` 暂存 `pendingRoute = "quicknote/edit?prefillFocus=true"` → navigate `onboarding/consent`;`OnboardingViewModel.accept()` 后 `AppNav` 监听到 consent + 检查 `MainActivity.pendingRoute` 存在 → navigate 该 route + 清栈

#### Scenario: 升级条款版本强制重同
- **WHEN** `R.integer.consent_version` 从 1 bump 到 2,`ConsentStore.consentVersion = 1`(已同意旧版)
- **THEN** `AppNav.LaunchedEffect` 判定 `consentVersion < CURRENT_CONSENT_VERSION` → navigate `onboarding/consent`;`OnboardingScreen` 重新显示新版条款

#### Scenario: 撤回同意后回 onboarding
- **WHEN** `ConsentStore.setAccepted(version=0, at=0L)` 调用(撤回),App 在前台
- **THEN** `AppNav` 监听到 `consentFlow` 变 `accepted=false` → navigate `onboarding/consent` + 清栈;主路由自动 pop
