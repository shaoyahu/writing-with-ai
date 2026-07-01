# predictive-back-gesture

## Purpose

M0-M4-1 已落地完整产品骨架，但 **Android 13+ predictive back gesture 未声明 / 启用**，违反 Google Play 2025-06 后上架要求(targetSdk 35 + Android 14+ 必备 `enableOnBackInvokedCallback="true"`)。本 change 落 AndroidManifest 配置 + widget Intent 任务栈语义(`TaskStackBuilder` 构造 `PendingIntent`,back 行为 = widget tap → MainActivity → 系统 back → launcher 桌面)，让 v1 上架 Play Store 通过审。

TBD — synced from OpenSpec change `predictive-back-gesture`(2026-06-19)。

## ADDED Requirements

### Requirement: AndroidManifest enables predictive back on app + activity

`AndroidManifest.xml` MUST 在 `<application>` 与 `MainActivity <activity>` 双重声明 `android:enableOnBackInvokedCallback="true"`(Android 13+ 推荐，Android 14+ 强制);`targetSdk = 35`(M0 已配)继续保持。

#### Scenario: Play Store 上架检查通过
- **WHEN** 上架 APK,Play Store 检查 `targetSdk` 与 `enableOnBackInvokedCallback`
- **THEN** `targetSdk=35` + `enableOnBackInvokedCallback="true"` 在 `<application>` 与 `<activity>` 都声明，卡审项通过

#### Scenario: Android 13 (API 33) 设备启用 predictive back
- **WHEN** 用户在 Android 13 设备上系统手势侧滑返回
- **THEN** App 走 `OnBackPressedDispatcher` 新 API，显示 predictive back 动画过渡(系统级预览)

#### Scenario: Android 14+ (API 34+) 强制启用
- **WHEN** App 在 Android 14+ 设备上运行
- **THEN** `enableOnBackInvokedCallback` 强制 true，系统强制走新 back 框架

#### Scenario: Android < 13 (API < 33) 兼容
- **WHEN** App 在 Android 12 (API 31) 设备上运行
- **THEN** `enableOnBackInvokedCallback` 无效(API < 33 不支持)，系统走旧 `onBackPressed` 路径，App 仍正常 back(因 `NavHost` 自带返回集成)

### Requirement: widget createNoteIntent uses TaskStackBuilder + CLEAR_TASK

`QuickNoteWidget.kt createNoteIntent(context)` MUST 改用 `TaskStackBuilder.create(context).addNextIntentWithParentStack(intent).getPendingIntent(...)` 构造 `PendingIntent`，而非裸 `Intent(context, MainActivity::class.java)`。

Intent flags MUST 包括 `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`(从 M4-1 的 `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP` 改);PendingIntent flags MUST 包括 `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`。

#### Scenario: widget tap "+" 启动到编辑页
- **WHEN** 用户在桌面 widget 点 "+" 按钮
- **THEN** `PendingIntent` 启动 MainActivity,navigate 到 `quicknote/edit?prefillFocus=true`，输入框自动 focus

#### Scenario: 系统 back 行为回到 launcher
- **WHEN** 用户在 widget 启动的编辑页按系统 back(虚拟键或侧滑手势)
- **THEN** 走 `TaskStackBuilder` 构造的任务栈，**回到 launcher 桌面**，不进 App 内列表页

#### Scenario: 多个 widget 共存
- **WHEN** 用户加 2 个 widget，点其中一个 "+"
- **THEN** `CLEAR_TASK` 清空 launcher task，启动新 task，另一个 widget 不受影响(独立 widget host 进程)

#### Scenario: PendingIntent flag 验证
- **WHEN** 通过 Robolectric `ApplicationProvider.getApplicationContext().createTaskStackPendingIntent(route, requestCode)`
- **THEN** 验 `PendingIntent.FLAG_IMMUTABLE != 0` + `PendingIntent.FLAG_UPDATE_CURRENT != 0`

### Requirement: widget OpenNoteAction uses TaskStackBuilder for note detail

`OpenNoteAction.kt onAction(context, glanceId, parameters)` MUST 改用 `TaskStackBuilder.startActivities()` 启动带 back stack 的任务栈(而非裸 `context.startActivity(intent)`)，让 widget 笔记项点击 → MainActivity → 系统 back → launcher 桌面。

#### Scenario: widget 点笔记项启动到详情
- **WHEN** 用户在 widget 点笔记项
- **THEN** MainActivity 启动，navigate 到 `quicknote/detail/{noteId}`

#### Scenario: 详情页 back 回 launcher
- **WHEN** 用户在 widget 启动的详情页按系统 back
- **THEN** 走 `TaskStackBuilder` 构造的任务栈，**回到 launcher 桌面**

### Requirement: Predictive back animation MUST falls back gracefully

`MainActivity` MUST 不写自定义 `BackHandler { enabled = ... }`，不拦截系统 back 事件;让 `NavHost` + `OnBackPressedDispatcher` 自管(roadmap §7.2 明确"不自定义手势拦截，避免和系统手势打架")。

#### Scenario: 不自定义 BackHandler
- **WHEN** grep `MainActivity.kt` / `AppNav.kt` / 各 Composable
- **THEN** 0 个 import `androidx.activity.compose.BackHandler`;不调用 `BackHandler { ... }`

#### Scenario: predictive back 动画在 AOSP launcher 可见
- **WHEN** Android 14+ 用户在 widget 启动页侧滑返回
- **THEN** 系统显示 predictive back 动画过渡(Material 3 NavHost 默认支持)，返回后 launcher 显示

### Requirement: AndroidManifest declares required activity flags

`AndroidManifest.xml <activity>` MUST 声明 `android:launchMode="singleTask"` 或保留 M0 默认 standard;`android:windowSoftInputMode="adjustResize"` 配合编辑器键盘(roadmap §7.1)。

#### Scenario: 编辑器键盘 resize
- **WHEN** 用户在编辑器输入文字，弹出软键盘
- **THEN** `adjustResize` 让 layout 上移键盘不遮挡输入框

#### Scenario: launchMode 兼容 widget
- **WHEN** 用户从 widget 启动编辑页，Activity 不重用旧实例
- **THEN** `singleTask` 或默认 `standard` 行为下，每次 widget tap 都开新实例(task stack 内)

## ADDED Requirements (Tests)

### Requirement: JUnit5 Robolectric tests cover TaskStackBuilder flag

JUnit5 + Robolectric 必须覆盖 `createTaskStackPendingIntent`:
- 验 `PendingIntent.FLAG_IMMUTABLE != 0`
- 验 `PendingIntent.FLAG_UPDATE_CURRENT != 0`
- 验 `getIntent(context)` 含 `OpenNoteAction.EXTRA_ROUTE` extra

#### Scenario: PendingIntent FLAG_IMMUTABLE 已设
- **WHEN** `ApplicationProvider.getApplicationContext<Context>().createTaskStackPendingIntent(...)`
- **THEN** `pendingIntent.flags and FLAG_IMMUTABLE != 0`

#### Scenario: PendingIntent FLAG_UPDATE_CURRENT 已设
- **WHEN** 同上
- **THEN** `pendingIntent.flags and FLAG_UPDATE_CURRENT != 0`

#### Scenario: Intent extra route 已传
- **WHEN** 同上(route = "quicknote/edit?prefillFocus=true")
- **THEN** `pendingIntent` 取 `getIntent(...)` 后 `getStringExtra("route") == "quicknote/edit?prefillFocus=true"`

## MODIFIED Requirements

无(M4-1 `home-screen-widget` 的 OpenNoteAction / QuickNoteWidget / QuickNoteWidgetUpdater 等文件被本 change 修改，但 spec §"package layout" 已覆盖 self-containment 语义，不需要新增 Requirement)。

## REMOVED Requirements

无。

## RENAMED Requirements

无。