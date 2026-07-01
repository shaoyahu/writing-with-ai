# quick-note

## Purpose

随手记(M1)的完整数据模型与 UI 行为契约;定义 `Note` / `Tag` 实体形状、CRUD / 搜索 / 标签 / 固定 / 单条分享导出的端到端行为，以及 Nav 路由契约。本 spec 是 M2 AI 抽象层(`AiHistory` 关联 `Note.id` 与 `Note.lastAiOp`)+ M3 AI 操作 UI + M4-1 widget 启动参数的前置。

TBD — synced from OpenSpec change `quick-note-feature`(2026-06-18)。原 change 在 `openspec/changes/quick-note-feature/`。

## REMOVED Requirements

无(M1 / M3 全部要求保留)。

## RENAMED Requirements

无。

## ADDED Requirements

### Requirement: NoteRepository supports observeRecent for widget(M4-1 新增)

`NoteRepository` MUST 新增 `fun observeRecent(limit: Int): Flow<List<Note>>`，内部走 `noteDao.observeAll().map { it.take(limit).map { e -> e.toModel() } }`。**不**新增 DAO SQL(`take(n)` 内存截断足够，单用户笔记数 < 10k)。

#### Scenario: observeRecent(3) emit 最多 3 条
- **WHEN** 数据库有 5 条笔记(按 `updatedAt` desc)
- **THEN** `observeRecent(3)` Flow emit 头 3 条最新笔记

#### Scenario: observeRecent(1) emit 1 条
- **WHEN** 数据库有 5 条笔记
- **THEN** `observeRecent(1)` Flow emit 最新 1 条

#### Scenario: observeRecent 无笔记
- **WHEN** 数据库为空
- **THEN** `observeRecent(N)` Flow emit 空列表

#### Scenario: 笔记增删改后 observeRecent 自动 emit
- **WHEN** 用户新建一条笔记(`upsert` 后) → `observeRecent` 自动 emit 新列表(含新笔记)
- **THEN** Glance widget 收到新数据，触发 `provideContent` 重渲染

### Requirement: Note schema unchanged by widget(M4-1 验证项)

M4-1 MUST NOT 修改 `NoteEntity` / `NoteDao` / `Note` 字段;`observeRecent` 是 `NoteRepository` 新方法，schema 完全不变。

#### Scenario: Note 字段集合保持 v2
- **WHEN** `git diff openspec/changes/home-screen-widget/ core/data/`
- **THEN** 0 个字段新增 / 0 个字段类型变更;`AppDatabase` 仍 `version = 2`，无新 MIGRATION

#### Scenario: observeRecent 不改 DAO SQL
- **WHEN** `git diff openspec/changes/home-screen-widget/ core/data/db/NoteDao.kt`
- **THEN** 0 个 `@Query` 新增;只改 `NoteRepository.kt`

### Requirement: AppNav route supports prefillFocus param(M4-1 新增)

`app/AppNav.kt` 中 `QuicknoteEdit` data class MUST 加 `val prefillFocus: Boolean = false` 字段;`composable<QuicknoteEdit>` block 解析 `prefillFocus` 并透传给 `QuickNoteEditorScreen`。

Widget 通过 `PendingIntent.getActivity` 启动 MainActivity 时，`intent` extra 含 `"prefillFocus"=true` + `"route"="quicknote/edit"`;`MainActivity.onCreate(intent)` 解析后 navigate 到 `QuicknoteEdit(prefillFocus = true)`。

#### Scenario: 编辑器 route 默认 prefillFocus=false
- **WHEN** 用户从列表 FAB / 列表空状态"新建"按钮进编辑器
- **THEN** `prefillFocus = false`(默认)，输入框**不**自动 focus(行为同 M1)

#### Scenario: 编辑器 route 显式 prefillFocus=true
- **WHEN** 用户从 widget 点"+",MainActivity 启动并解析 route 含 `prefillFocus=true`
- **THEN** `QuickNoteEditorScreen` 接收 `prefillFocus = true`，输入框自动 focus，用户可立即键入

#### Scenario: 编辑器 route 不影响编辑器其他行为
- **WHEN** `prefillFocus = true`
- **THEN** 编辑器加载既有笔记(如编辑现有笔记)的逻辑不变;只是输入框自动 focus，无副作用

### Requirement: Editor screen supports prefillFocus param(M4-1 新增)

`QuickNoteEditorScreen` MUST 接受 `prefillFocus: Boolean = false` 参数，`LaunchedEffect(prefillFocus)` 内若 `prefillFocus == true` 则调 `FocusRequester.requestFocus()`(参考 Compose Focus API)。

`QuickNoteEditorViewModel` 不需要新增字段(参数是 UI 一过性行为，不持久化)。

#### Scenario: prefillFocus=true 触发 FocusRequester
- **WHEN** 用户从 widget 进编辑器，`prefillFocus=true`
- **THEN** `LaunchedEffect` 触发，`focusRequester.requestFocus()` 被调，输入框获得焦点

#### Scenario: prefillFocus=false 不触发
- **WHEN** 用户从列表 FAB 进编辑器，`prefillFocus=false`(默认)
- **THEN** `LaunchedEffect` 不触发 focus，用户需手动点击输入框

#### Scenario: prefillFocus 不改保存逻辑
- **WHEN** 用户在编辑器输入文字并保存(`upsert`)
- **THEN** 走 M1 既有 `NoteRepository.upsert(...)`,`prefillFocus` 仅影响首次 focus 行为

### Requirement: WidgetIntent launcher routes pass through MainActivity(M4-1 联动)

`core/widget/WidgetIntents.kt` 的 `PendingIntent.getActivity` MUST 启动 `MainActivity`(`.app.MainActivity`),`Intent` 含 `extra`:
- `"route" = "quicknote/edit?prefillFocus=true"`(新建按钮)或 `"quicknote/detail/{noteId}"`(笔记项)
- `FLAG_ACTIVITY_NEW_TASK`(从 widget host process 启动需要)

`MainActivity.onCreate(intent)` MUST 解析 `intent.getStringExtra("route")` 并 navigate 到对应 route;若 `route == null` 走默认 `quicknote/list`(M1 既有)。

#### Scenario: MainActivity 解析 widget extra
- **WHEN** 用户从 widget 点"+",MainActivity 启动并收到 `intent.extra("route") = "quicknote/edit?prefillFocus=true"`
- **THEN** `MainActivity` navigate 到 `QuicknoteEdit(prefillFocus = true)`，输入框自动 focus

#### Scenario: MainActivity 解析 widget extra detail
- **WHEN** 用户从 widget 点笔记项，MainActivity 收到 `intent.extra("route") = "quicknote/detail/n1"`
- **THEN** `MainActivity` navigate 到 `QuicknoteDetail(id = "n1")`，显示该笔记详情

#### Scenario: MainActivity 默认 route
- **WHEN** 用户从 launcher 图标启动 App(无 widget)
- **THEN** `intent.extra("route") == null`，走 M1 既有 `quicknote/list` 默认 destination

### Requirement: Widget tap back returns to launcher, not app list(M4-1 联动)

`WidgetIntents.createNotePendingIntent` / `openNotePendingIntent` MUST 用 `TaskStackBuilder` 构造 `PendingIntent`,**不**用裸 `PendingIntent.getActivity`。`TaskStackBuilder` 保证 back 行为:widget tap → MainActivity → back → launcher 桌面(不是 App 内列表页)。

#### Scenario: 新建按钮 back 回桌面
- **WHEN** 用户在 widget 点"+" → MainActivity 启动到编辑页 → 用户按 back
- **THEN** 返回到 launcher 桌面，**不**进入 App 内列表页

#### Scenario: 笔记项 back 回桌面
- **WHEN** 用户在 widget 点笔记项 → MainActivity 启动到详情 → 用户按 back
- **THEN** 返回到 launcher 桌面

#### Scenario: 编辑器保存后返回(launcher 行为)
- **WHEN** 用户在 widget 启动的编辑器保存笔记
- **THEN** 走 M1 既有 `popBackStack()` 行为 → launcher 桌面(因为任务栈独立)