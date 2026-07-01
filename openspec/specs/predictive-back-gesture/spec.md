# predictive-back-gesture Specification

## Purpose
TBD - created by archiving change predictive-back-gesture. Update Purpose after archive.
## Requirements
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

`MainActivity` MUST **不**在 Compose 层挂自定义 `BackHandler { enabled = ... }`(避免和系统手势打架);但 **MUST 允许** Activity 层挂 `OnBackPressedCallback`(Android 12+ 官方 idiom，行为是"back 结果处理"而非"手势拦截")实现防误触 UX(如主页"再按一次退出")。

#### Scenario: Activity 不挂自定义 BackHandler Composable

- **WHEN** `grep -rE "BackHandler" app/src/main/java/com/yy/writingwithai/`
- **THEN** 0 匹配(Compose `BackHandler` 禁用;`OnBackPressedCallback` 是 androidx.activity API，不受此约束)

#### Scenario: 主页 back 走 OnBackPressedCallback + Toast 二次确认

- **WHEN** 用户在 `QuicknoteList`(主页)按系统 back(虚拟键 / 三键导航)或侧滑手势
- **THEN** `MainActivity.onBackPressedDispatcher` 调 `backCallback.handleOnBackPressed()`(`backCallback.isEnabled == true` 当 destination 是 `QuicknoteList`);第一次触发 → `lastBackPressAt = now` + Toast "再按一次退出应用"(4s 自动消失);App **不** finish

### Requirement: Home screen back gesture shows "press again to exit" hint

`MainActivity` MUST 维护 `private val backCallback = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { val now = SystemClock.elapsedRealtime(); if (now - lastBackPressAt > 2000L) { lastBackPressAt = now; Toast.makeText(this@MainActivity, R.string.back_press_exit_hint, Toast.LENGTH_SHORT).show() } else { isEnabled = false; onBackPressedDispatcher.onBackPressed() } } }`;`onCreate` MUST 在 `setContent` 前调 `onBackPressedDispatcher.addCallback(this, backCallback)`。

`App.kt` MUST 暴露 `onNavControllerReady: (NavController) -> Unit = {}` 形参;`MainActivity` MUST 传 `onNavControllerReady = { nc -> nc.addOnDestinationChangedListener { _, dest, _ -> backCallback.isEnabled = dest.hasRoute(QuicknoteList::class) } }`。

#### Scenario: 主页首次 back 触发 Toast + App 保持前台

- **WHEN** 用户在主页首次 back;`lastBackPressAt` 初始 0
- **THEN** `now - lastBackPressAt > 2000L` → `lastBackPressAt = now` + Toast "再按一次退出应用" 显示;`backCallback.isEnabled` 保持 true;App 不 finish，留在主页

#### Scenario: 主页 2s 内再次 back 触发 finish

- **WHEN** 用户在主页 2s 内再次 back;`lastBackPressAt` 已记录
- **THEN** `now - lastBackPressAt <= 2000L` → `backCallback.isEnabled = false` + `onBackPressedDispatcher.onBackPressed()`(递归调用，callback 已 disabled → dispatcher 走默认 → Activity.finish())→ App 退 launcher

#### Scenario: 主页 2s 后 back 重新触发 Toast(窗口重置)

- **WHEN** 用户在主页 back → 等 3s → 再 back
- **THEN** `now - lastBackPressAt > 2000L`(因 3s 间隔)→ 重置 + Toast 再次显示(用户有再 2s 窗口)

#### Scenario: 非主页 back 不走 hint(默认 popBackStack)

- **WHEN** 用户在 `QuicknoteDetail` / `QuicknoteEdit` / `Settings*` / `ModelManagement*` 等非主页屏按 back
- **THEN** `backCallback.isEnabled == false`(destination listener 已切);`onBackPressedDispatcher` 默认行为 → `NavController.popBackStack()` → 返回上一页;无 Toast 显示

#### Scenario: widget 启动的 quicknote/edit 不被当主页

- **WHEN** 用户从 widget 启动进 `quicknote/edit?prefillFocus=true`(主页是 `QuicknoteList`,edit 不是主页)
- **THEN** `backCallback.isEnabled == false`;back 行为默认 `popBackStack()` → 退到 `QuicknoteList`(此时 `destination` listener 触发 `isEnabled = true` → 下次 back 走 Toast hint)

### Requirement: AndroidManifest declares required activity flags

`AndroidManifest.xml <activity>` MUST 声明 `android:launchMode="singleTask"` 或保留 M0 默认 standard;`android:windowSoftInputMode="adjustResize"` 配合编辑器键盘(roadmap §7.1)。

#### Scenario: 编辑器键盘 resize
- **WHEN** 用户在编辑器输入文字，弹出软键盘
- **THEN** `adjustResize` 让 layout 上移键盘不遮挡输入框

#### Scenario: launchMode 兼容 widget
- **WHEN** 用户从 widget 启动编辑页，Activity 不重用旧实例
- **THEN** `singleTask` 或默认 `standard` 行为下，每次 widget tap 都开新实例(task stack 内)

