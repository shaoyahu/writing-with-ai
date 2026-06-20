## Why

M0-M4-1 已落地:M0 工程脚手架 / M1 quick-note CRUD / M2 AI 抽象层 / M3 AI 操作 UI / M4-1 Glance 桌面 widget。但 roadmap §7 拍板的"predictive back gesture + widget 启动的回退栈"未实装。M4-1 用了 `popUpTo(QuicknoteList) { inclusive = true }` 修 widget 闪列表页,但**手势返回**(系统级侧滑 / 虚拟键 back)与**任务栈语义**(widget 启动后点 back 应回 launcher,而不是 App 列表)未显式声明与验证。

roadmap §7.4 拍板"从 widget 启动的快速记录卡片用 single-task 独立任务栈,返回时优先回主任务栈(用 `TaskStackBuilder` 显式构造回退栈)" — 本 change 落这一条 + Android 13+ `enableOnBackInvokedCallback = true` + Android 14+ `predictive back` 适配(targetSdk 35 必备)。

Android 14 起 `android:enableOnBackInvokedCallback="true"` 是预测式返回的强制要求,**没适配 Play Store 上架会卡审**。本 change 是 v1 上架的硬阻塞。

## What Changes

- **新增 predictive back 配置**(`AndroidManifest.xml` `<application>`):`android:enableOnBackInvokedCallback="true"`(targetSdk 35 + Android 13+ 必备);`android:windowSoftInputMode="adjustResize"` 配合键盘。
- **改 AndroidManifest.xml `<activity>` for `MainActivity`**:加 `android:enableOnBackInvokedCallback="true"`,M1/M2/M3 沿用 `NavHost` 自带返回集成,这里只是显式声明。
- **改 widget Intent 启动**(`core/widget/QuickNoteWidget.kt` `createNoteIntent`):用 `TaskStackBuilder` 构造 `PendingIntent`(M4-1 r1 L1 删除的 `createNotePendingIntent` 现在需要)— back 行为 = widget tap → 编辑页 → back → launcher 桌面(走 spec §"PendingIntent routes via TaskStackBuilder for back stack")。
- **改 widget 笔记项 Intent**(`OpenNoteAction.kt`):用 `TaskStackBuilder` 构造 back stack(同样回 launcher)— 让系统手势 back 与虚拟键 back 行为一致。
- **改 M4-1 AppNav `LaunchedEffect`**:M4-1 r2 已加 `popUpTo(QuicknoteList) { inclusive = true }`,本 change 不重复改 AppNav,只补 widget 启动路径用 TaskStackBuilder(让 back 行为一致)。
- **新增 i18n**(`values/strings.xml` + `values-en/`):无新增文案(手势 back 无 UI 文案;`R.string.quicknote_editor_cancel` = "取消" 沿用)。
- **新增测试**:JUnit5 验 `TaskStackBuilder` 构造 `PendingIntent` flag 正确(`FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`,requestCode 唯一)。
- **BREAKING**:无(M4-1 路径 AppNav 已修;本 change 主要补 widget 启动路径)
- **不引入**:
  - 自定义手势拦截库(roadmap §7.2 明确不用)
  - Android 14+ predictive back 自定义动画过渡(`OnBackPressedDispatcher` 默认 `predictiveBackProgress` 已够)
  - `PredictiveBackHandler` Compose API 单独拦截(让 NavHost 自管)

## Capabilities

### New Capabilities
- `predictive-back-gesture`:`MainActivity` 显式 `enableOnBackInvokedCallback="true"` + widget 启动 Intent 走 `TaskStackBuilder` 构造独立任务栈 + `OpenNoteAction` 同样走 TaskStackBuilder(back 行为统一回 launcher,不走 App 内列表)

### Modified Capabilities
- `quick-note`:无 schema 变更,`AppNav` 已 M4-1 修;本 change 只补 widget 路径,与 AppNav 不冲突
- `home-screen-widget`:`createNoteIntent` 改走 `TaskStackBuilder`(`WidgetIntents.kt` M4-1 L1 删的 `createNotePendingIntent` 恢复为 `TaskStackBuilder` 版本并被 widget 实际使用);`OpenNoteAction` 同样改 TaskStackBuilder

## Impact

- **修改**:
  - `AndroidManifest.xml` `<activity>` + `<application>` 加 `enableOnBackInvokedCallback="true"`
  - `core/widget/QuickNoteWidget.kt` `createNoteIntent` 改 TaskStackBuilder(M4-1 r2 L1 删的 `WidgetIntents.kt` 内容复归 + inline)
  - `core/widget/OpenNoteAction.kt` 启动 Intent 改 TaskStackBuilder
- **新增**:
  - 内部 helper `internal fun Context.createTaskStackPendingIntent(route: String, requestCode: Int): PendingIntent`(共 QuickNoteWidget + OpenNoteAction 用)
- **风险**:
  - `TaskStackBuilder` 配合 `FLAG_ACTIVITY_NEW_TASK` 才能让 widget 启动后 back 回 launcher;两个 flag 必须都设
  - Android 13(API 33)+ `enableOnBackInvokedCallback` 默认 false,**需显式 true**(否则 back 由旧 `onBackPressed` 路径,可能导致 predictive back 不工作)
  - Android 14(API 34)+ 强制 `enableOnBackInvokedCallback = true`,否则 Play Store 卡审
  - widget 启动 Intent 用 `Intent.FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`(CLEAR_TASK 让 launcher tap 单击回到 launcher 干净)— M4-1 用 `CLEAR_TOP` 但任务栈可能未清,改 `CLEAR_TASK`
  - predictive back 动画在国产 ROM 可能不显示(系统手势拦截机制差异)— 留 M5 polish