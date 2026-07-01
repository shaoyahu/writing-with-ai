# quick-note

## Purpose

随手记(M1)的完整数据模型与 UI 行为契约;定义 `Note` / `Tag` 实体形状、CRUD / 搜索 / 标签 / 固定 / 单条分享导出的端到端行为，以及 Nav 路由契约。本 spec 是 M2 AI 抽象层 + M3 AI 操作 UI + M4-1 widget 启动参数 + M4-2 predictive back 的前置。

TBD — synced from OpenSpec change `quick-note-feature`(2026-06-18)。

## REMOVED Requirements

无(M1 / M3 / M4-1 全部要求保留)。

## RENAMED Requirements

无。

## ADDED Requirements

### Requirement: AndroidManifest declares predictive back flags(M4-2 新增)

`AndroidManifest.xml` MUST 在 `<application>` 与 `MainActivity <activity>` 双重声明 `android:enableOnBackInvokedCallback="true"`(M4-2 新增);`<activity>` 增 `android:windowSoftInputMode="adjustResize"` 配合编辑器键盘。

#### Scenario: AndroidManifest enableOnBackInvokedCallback 在 application
- **WHEN** grep `AndroidManifest.xml` `<application`
- **THEN** `android:enableOnBackInvokedCallback="true"` 在 `<application>` 属性列表内

#### Scenario: AndroidManifest enableOnBackInvokedCallback 在 activity
- **WHEN** grep `AndroidManifest.xml` `<activity.*MainActivity`
- **THEN** `android:enableOnBackInvokedCallback="true"` 在 `<activity>` 属性列表内

#### Scenario: windowSoftInputMode 配合编辑器
- **WHEN** grep `AndroidManifest.xml` `<activity.*MainActivity`
- **THEN** `android:windowSoftInputMode="adjustResize"` 在 `<activity>` 属性列表内

#### Scenario: targetSdk = 35 保持
- **WHEN** grep `app/build.gradle.kts` `targetSdk`
- **THEN** `targetSdk = 35`(M0 已配，M4-2 不改)— Android 14+ predictive back 强制要求

### Requirement: MainActivity honors enableOnBackInvokedCallback(M4-2 验证)

`MainActivity` MUST 在 `onCreate` 不写自定义 `BackHandler { enabled = ... }`(M4-2 spec 要求"不自定义手势拦截");让 `NavHost` + `OnBackPressedDispatcher` 自管 back 行为，触发 predictive back 系统动画。

#### Scenario: MainActivity 不自定义 BackHandler
- **WHEN** grep `MainActivity.kt`
- **THEN** 0 个 import `androidx.activity.compose.BackHandler`;0 个 `BackHandler { ... }` 调用

#### Scenario: AppNav 不自定义 BackHandler
- **WHEN** grep `AppNav.kt`
- **THEN** 0 个 import `androidx.activity.compose.BackHandler`

#### Scenario: NavHost 自带 back 集成
- **WHEN** `androidx.navigation:navigation-compose:2.8.4`(M1 已配) + `enableOnBackInvokedCallback="true"` 启用
- **THEN** NavHost 在系统 back 触发时自动 `popBackStack()`，并触发 predictive back 系统动画(Android 14+)

### Requirement: AppNav LaunchedEffect initialRoute MUST 不动(M4-2 保留 M4-1 修)

M4-1 r2 MUST 已加 `popUpTo(QuicknoteList) { inclusive = true }` 在 `LaunchedEffect(initialRoute)` 内;**M4-2 MUST 不重复改 AppNav**。

#### Scenario: LaunchedEffect 仍含 popUpTo
- **WHEN** grep `AppNav.kt` "popUpTo"
- **THEN** 至少 1 个 `popUpTo(QuicknoteList)` 在 `LaunchedEffect(initialRoute)` block 内(M4-1 修后保留)

#### Scenario: widget Intent 走 TaskStackBuilder(M4-2 新要求)
- **WHEN** `core/widget/QuickNoteWidget.kt createNoteIntent(context)` 调用 `TaskStackBuilder`
- **THEN** AppNav LaunchedEffect 内 `popUpTo(QuicknoteList) { inclusive = true }` 仍生效(双保险)— widget Intent 走 TaskStackBuilder 构造的栈，**AppNav 内的 popUpTo 是兜底**，即使某天 TaskStackBuilder 出问题，popUpTo 仍能清理栈