# home-screen-widget

## Purpose

M4-1 桌面小组件(详见 `openspec/changes/archive/2026-06-19-home-screen-widget/`)。本 spec 在 M4-2 阶段被 `predictive-back-gesture` change 修改:widget Intent 启动改走 `TaskStackBuilder` 构造 `PendingIntent`,back 行为统一回 launcher 桌面(非 App 内列表)。

TBD — synced from OpenSpec change `home-screen-widget`(2026-06-19)。

## REMOVED Requirements

无(M4-1 全部要求保留)。

## RENAMED Requirements

无。

## ADDED Requirements

### Requirement: Widget createNoteIntent uses TaskStackBuilder + CLEAR_TASK(M4-2 新增)

`QuickNoteWidget.kt createNoteIntent(context)` MUST 改用 `TaskStackBuilder.create(context).addNextIntentWithParentStack(intent).getPendingIntent(...)` 构造 `PendingIntent`,从 M4-1 的 `Intent(context, MainActivity::class.java)` + `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP` 改为 `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK` + `TaskStackBuilder` 路径。

Intent flags MUST 包括 `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`;PendingIntent flags MUST 包括 `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`。

#### Scenario: widget tap "+" 启动到编辑页
- **WHEN** 用户在桌面 widget 点 "+" 按钮
- **THEN** `PendingIntent` 启动 MainActivity,navigate 到 `quicknote/edit?prefillFocus=true`,输入框自动 focus

#### Scenario: 系统 back 行为回到 launcher
- **WHEN** 用户在 widget 启动的编辑页按系统 back(虚拟键或侧滑手势)
- **THEN** 走 `TaskStackBuilder` 构造的任务栈,**回到 launcher 桌面**,不进 App 内列表页

#### Scenario: 多个 widget 共存
- **WHEN** 用户加 2 个 widget,点其中一个 "+"
- **THEN** `CLEAR_TASK` 清空 launcher task,启动新 task,另一个 widget 不受影响(独立 widget host 进程)

#### Scenario: PendingIntent flag 验证
- **WHEN** 通过 Robolectric `ApplicationProvider.getApplicationContext().createTaskStackPendingIntent(route, requestCode)`
- **THEN** 验 `PendingIntent.FLAG_IMMUTABLE != 0` + `PendingIntent.FLAG_UPDATE_CURRENT != 0`

### Requirement: Widget OpenNoteAction uses TaskStackBuilder for note detail(M4-2 新增)

`OpenNoteAction.kt onAction(context, glanceId, parameters)` MUST 改用 `TaskStackBuilder.startActivities()` 启动带 back stack 的任务栈(而非裸 `context.startActivity(intent)`),让 widget 笔记项点击 → MainActivity → 系统 back → launcher 桌面。

#### Scenario: widget 点笔记项启动到详情
- **WHEN** 用户在 widget 点笔记项
- **THEN** MainActivity 启动,navigate 到 `quicknote/detail/{noteId}`

#### Scenario: 详情页 back 回 launcher
- **WHEN** 用户在 widget 启动的详情页按系统 back
- **THEN** 走 `TaskStackBuilder` 构造的任务栈,**回到 launcher 桌面**

### Requirement: WidgetIntent helper unifies create + open intent construction(M4-2 新增)

`core/widget/WidgetIntentHelpers.kt`(或 inline) MUST 提供 `internal fun Context.createTaskStackPendingIntent(route: String, requestCode: Int): PendingIntent`,被 `QuickNoteWidget.createNoteIntent(context)` 与 `OpenNoteAction.onAction` 共用,避免重复 PendingIntent 构造逻辑。

#### Scenario: 两个 widget intent 共享 helper
- **WHEN** grep `core/widget/**/*.kt`
- **THEN** 0 个 inline `TaskStackBuilder.create(this).addNextIntentWithParentStack(intent).getPendingIntent(...)`;全部走 `createTaskStackPendingIntent(route, requestCode)` helper

#### Scenario: requestCode 区分
- **WHEN** widget 加进桌面,创建 2 个 PendingIntent(create + open)
- **THEN** `createTaskStackPendingIntent(route="quicknote/edit...", requestCode=1001)` 与 `createTaskStackPendingIntent(route="quicknote/detail/n1", requestCode=1002)` 共存不互相覆盖(FLAG_UPDATE_CURRENT 让 extras 更新)

### Requirement: M4-1 widget intent flag MUST 已移除(M4-2 替换)

M4-1 的 `Intent.FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP` MUST 已被 M4-2 的 `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK` 替换;`Intent(context, MainActivity::class.java)` 裸 Intent MUST 已被 `TaskStackBuilder.addNextIntentWithParentStack(intent)` 替换。

#### Scenario: 旧 flag 不存在
- **WHEN** grep `core/widget/QuickNoteWidget.kt createNoteIntent`
- **THEN** 0 个 `FLAG_ACTIVITY_CLEAR_TOP`(已被 `CLEAR_TASK` 替换)

#### Scenario: 裸 Intent 不存在
- **WHEN** grep `core/widget/QuickNoteWidget.kt` "Intent(context, MainActivity"
- **THEN** 0 个匹配(裸 Intent 已被 `createTaskStackPendingIntent` helper 替换)