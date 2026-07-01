# home-screen-widget-1x4 Specification

## Purpose

TBD — synced from OpenSpec change `widget-1x4-compact`(2026-06-20)。原 change 在 `openspec/changes/widget-1x4-compact/`。

1x4 compact home-screen widget:左侧 1x1 加号按钮(单击直达编辑器)+ 右侧 1x3 最近 1 条笔记(标题 + 相对时间，点击进详情)。复用 M4-1 widget 基础设施(Repository / Intent Helpers / Worker / Updater)，增量加 1x4 尺寸分支。

## Requirements

### Requirement: 1x4 widget renders single recent note beside create button

`QuickNoteWidget` 在 `currentSize.width <= 80.dp` 时 MUST 渲染 1x4 布局:
- **左侧 1x1 加号**:固定宽度 36dp,Material `Icons.Filled.Add` 图标，整块 `actionRunCallback` 或 `PendingIntent` 触发 `createTaskStackPendingIntent("quicknote/edit?prefillFocus=true", requestCode=1003)`(复用 M4-2 `WidgetIntentHelpers`)
- **右侧 1x3 笔记区**:`Column` 单条笔记(`Note.title.ifBlank { content.take(TITLE_FALLBACK_LEN) }` + `formatRelativeTime(updatedAt)`);`Text` `maxLines = 1, overflow = TextOverflow.Ellipsis`;无笔记时显示 `R.string.widget_empty` = "还没有笔记"
- 整块不带顶部"随手记"标题(高度仅 4 格，垂直空间给笔记)
- 笔记项点击触发 `ActionCallback<OpenNoteAction>`(`noteId` 作 parameter)，复用现有 `OpenNoteAction.onAction`

`widget_info.xml` `<appwidget-provider>` MUST 加 `targetCellWidth=1 targetCellHeight=4`,launcher widget picker 可见 1x4 选项。

#### Scenario: 1x4 widget 有笔记时显示标题 + 时间

- **WHEN** `QuickNoteWidgetRepository.observeRecent(1)` emit 1 条笔记(`title="晨跑计划"`, `updatedAt=<now-1h>`),widget 渲染宽度 ≈ 70dp
- **THEN** widget 左侧显示加号图标(36dp)，右侧显示"晨跑计划"(单行省略)+ "1 小时前"

#### Scenario: 1x4 widget 无笔记时显示空状态

- **WHEN** `observeRecent(1)` emit 空列表
- **THEN** widget 右侧显示 "还没有笔记"，左侧加号仍在

#### Scenario: 点 1x4 加号直达编辑器

- **WHEN** 用户在桌面 widget 点左侧加号按钮
- **THEN** `createTaskStackPendingIntent("quicknote/edit?prefillFocus=true", requestCode=1003)` 启动 MainActivity,navigate 到 `QuicknoteEdit(id = "NEW", prefillFocus = true)`，输入框自动 focus;back 走 `TaskStackBuilder` 回 launcher(同 M4-2 行为)

#### Scenario: 点 1x4 笔记项启动详情

- **WHEN** 用户在桌面 widget 点右侧笔记项
- **THEN** `OpenNoteAction.onAction(context, glanceId, parameters)` 用 `noteId` 启动 MainActivity 到 `quicknote/detail/{noteId}`;back 回 launcher

#### Scenario: launcher picker 显示 1x4 选项

- **WHEN** 系统读 `widget_info.xml`
- **THEN** `<appwidget-provider>` 含 `targetCellWidth=1 targetCellHeight=4`,launcher widget picker 把 1x4 列为推荐尺寸

### Requirement: 1x4 widget 数据访问复用现有 Repository

`QuickNoteWidget` 1x4 分支 MUST 走 `QuickNoteWidgetRepository.observeRecent(1)`,**不**新增 DAO SQL,**不**改 `Note` schema。

#### Scenario: observeRecent 1 条不破 schema

- **WHEN** 1x4 widget 拉数据
- **THEN** 走 `noteDao.observeAll() + take(1)` 内存截断，`NoteEntity` / `NoteDao` / `AppDatabase` 0 改动;`AppDatabase` 仍 `version = 2`

### Requirement: 1x4 widget 复用 Intent Helpers + Worker + Updater

`QuickNoteWidget` 1x4 分支 MUST 复用以下 M4-1 / M4-2 已有组件:
- `WidgetIntentHelpers.createTaskStackPendingIntent(route, requestCode)`(M4-2 抽出的 helper)
- `OpenNoteAction`(M4-1 实现)
- `QuickNoteWidgetUpdater.updateAll(context)` 主路径触发(M4-1)
- `QuickNoteWidgetWorker` 15min 兜底(M4-1)

`requestCode=1003` 用于 1x4 加号 PendingIntent;`createTaskStackPendingIntent` 的 extras 更新由 `FLAG_UPDATE_CURRENT` 处理，2x2 / 4x2 已用 `requestCode=1001 / 1002` 不冲突。

#### Scenario: 加号 PendingIntent 独立 requestCode

- **WHEN** grep `core/widget/QuickNoteWidget.kt`
- **THEN** 1x4 加号分支调 `createTaskStackPendingIntent("quicknote/edit?prefillFocus=true", requestCode=1003)`，与 2x2 / 4x2 的 requestCode 不冲突

#### Scenario: 主路径触发 widget 刷新

- **WHEN** 用户在 App 内新建 / 删除 / 编辑 / 接受 AI 输出笔记
- **THEN** `QuickNoteWidgetUpdater.updateAll(context)` 触发 → Glance `QuickNoteWidget().updateAll(context)` → 1x4 / 2x2 / 4x2 三种 widget 实例同步刷新

### Requirement: 1x4 widget i18n via R.string

2 个新增 widget i18n key MUST 走 `R.string.widget_1x4_*`:

| key | 中文 | 英文 | 用途 |
| --- | --- | --- | --- |
| `widget_1x4_layout_label` | 1x4 紧凑 | 1x4 Compact | launcher widget picker 尺寸标签 |
| `widget_1x4_title` | 随手记 | Quick Note | AndroidManifest `<receiver android:label>` 复用 |

Glance composable / Composable MUST 通过 `LocalContext.current.getString(R.string.widget_1x4_*)` 引用;**禁止**硬编码中文字符串。

#### Scenario: 中文系统语言显示权威中文

- **WHEN** 系统语言为中文
- **THEN** widget picker 显示"1x4 紧凑";grep 源码无 `widget_1x4_layout_label = "1x4 紧凑"` 等硬编码

#### Scenario: 英文系统语言显示 TODO 占位(M5 polish 补)

- **WHEN** 系统语言为英文，`values-en/strings.xml` 中 `widget_1x4_layout_label="TODO(en): widget_1x4_layout_label"`
- **THEN** widget picker 显示 `TODO(en): widget_1x4_layout_label`,APK 仍正常构建;M5 polish 阶段替换

### Requirement: 1x4 widget 复用 Glance 颜色约束

1x4 分支 MUST 沿用现有 widget 颜色约束:走 `ColorProvider(R.color.<token>)`,**禁止** import `androidx.compose.foundation.layout.*`,**禁止** import `androidx.compose.material3.*`(widget host process 不可用)。

#### Scenario: 1x4 widget 0 个 Compose foundation import

- **WHEN** grep `core/widget/QuickNoteWidget.kt`
- **THEN** 0 个 import 出现 `androidx.compose.foundation.*`;只允许 `androidx.glance.*` + `androidx.compose.ui.graphics.Color`(颜色转换)

### Requirement: 1x4 widget 自包含于 core/widget/

`core/widget/**` MUST **不** import `feature/quicknote/**` 内部(除通过 `MainActivity` + Intent 路径启动);**不** import `core/ai/**`;**不** import `feature/aiwriting/**`;1x4 widget 作为新分支放 `QuickNoteWidget.kt`，不改 package layout。

#### Scenario: widget 目录无 feature 内部 import(扩 1x4)

- **WHEN** grep `core/widget/**/*.kt`
- **THEN** 0 个 import 出现 `feature.quicknote.detail.*` / `feature.aiwriting.*` / `core.ai.*`;新 1x4 分支不引入新 import 路径

### Requirement: 1x4 widget 测试覆盖 Repository + Intent

JUnit5 + Turbine 测试 MUST 覆盖:
- `QuickNoteWidgetRepository.observeRecent(1)` emit 最多 1 条(已有，无需新测试)
- `WidgetIntentHelpers.createTaskStackPendingIntent("quicknote/edit?prefillFocus=true", 1003)` 返回 `PendingIntent`,flags 含 `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`(已有 helper test 扩 1 个 case)

#### Scenario: requestCode=1003 PendingIntent 构造

- **WHEN** 调 `context.createTaskStackPendingIntent("quicknote/edit?prefillFocus=true", 1003)`
- **THEN** 返回 `PendingIntent`,`FLAG_IMMUTABLE != 0` + `FLAG_UPDATE_CURRENT != 0`;intent extras 含 `route="quicknote/edit?prefillFocus=true"`

#### Scenario: observeRecent 1 条与 3 条不串

- **WHEN** `FakeNoteRepository.observeRecent(1)` 与 `observeRecent(3)` 同时 emit
- **THEN** 两条 Flow 独立，1 条 Flow 不受 3 条 Flow 影响;1x4 widget 拉 1 条，4x2 widget 拉 3 条

### Requirement: 1x4 widget ROM 兼容性

1x4 widget MUST 在国产 ROM launcher(小米 MIUI / 华为 HarmonyOS / OPPO ColorOS / vivo OriginOS)与原生 AOSP launcher 都可添加;部分 ROM 不展示 1x4 选项时，功能等价回退 2x2 widget(roadmap §8.3 已立)。

#### Scenario: AOSP launcher 1x4 可添加

- **WHEN** 用户在 AOSP launcher 长按桌面 → widget → 找"随手记"
- **THEN** picker 显示 1x4 / 2x2 / 4x2 三种推荐尺寸，选 1x4 添加成功

#### Scenario: 国产 ROM 不显示 1x4 走 2x2 回退

- **WHEN** 国产 ROM launcher 不识别 `targetCellWidth=1 targetCellHeight=4`
- **THEN** picker 仅显示 2x2 / 4x2，用户用 2x2 widget 也能新建 + 看最近笔记(功能等价)