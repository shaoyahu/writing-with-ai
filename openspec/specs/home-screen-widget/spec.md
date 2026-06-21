# home-screen-widget Specification

## Purpose
TBD - created by archiving change home-screen-widget. Update Purpose after archive.
## Requirements
### Requirement: 2x2 widget renders single recent note with create button

`QuickNoteWidget` 在 `currentSize.width <= 160.dp` 时 MUST 渲染 2x2 布局:
- 顶部标题区:`R.string.widget_2x2_title` = "随手记"
- 中部笔记区:1 条最近笔记(`Note.title.ifBlank { content.take(TITLE_FALLBACK_LEN) }` + `formatRelativeTime(updatedAt)`);无笔记时显示 `R.string.widget_empty` = "还没有笔记\n点 + 创建第一条"
- 底部"+"按钮:点击触发 `PendingIntent.getActivity` 启动 MainActivity 到 `quicknote/edit?prefillFocus=true`(`TaskStackBuilder` 构造回退栈)

#### Scenario: 2x2 widget 有笔记时显示标题
- **WHEN** `NoteRepository.observeRecent(1)` emit 1 条笔记(`title="晨跑计划"`, `updatedAt=<now-1h>`)
- **THEN** widget 中部显示"晨跑计划" + "1 小时前" + 底部"+"按钮

#### Scenario: 2x2 widget 无笔记时显示空状态
- **WHEN** `NoteRepository.observeRecent(1)` emit 空列表
- **THEN** widget 中部显示 "还没有笔记\n点 + 创建第一条"(走 `R.string.widget_empty`)

#### Scenario: 点 2x2 "+" 启动到编辑页
- **WHEN** 用户在桌面 widget 点"+"
- **THEN** MainActivity 启动,navigate 到 `quicknote/edit?prefillFocus=true`,输入框自动 focus

### Requirement: 4x2 widget renders three recent notes with create button

`QuickNoteWidget` 在 `currentSize.width > 160.dp` 时 MUST 渲染 4x2 布局:
- 顶部标题区:`R.string.widget_4x2_title` = "随手记"
- 中部笔记区:3 条最近笔记(标题 + relativeTime),用 Glance `Column`(不用 `LazyColumn`,3 条不超高度);无笔记时显示空状态
- 右上角"+"按钮:同上(2x2)

每条笔记点击触发 `ActionCallback<OpenNoteAction>`(`noteId` 作 parameter),`onAction` 内启动 MainActivity 到 `quicknote/detail/{noteId}`。

#### Scenario: 4x2 widget 3 条笔记
- **WHEN** `observeRecent(3)` emit 3 条笔记
- **THEN** widget 中部按更新时间倒序显示 3 行,每行 "标题 · 时间前";右上角"+"按钮

#### Scenario: 4x2 widget 不足 3 条
- **WHEN** `observeRecent(3)` emit 1 条笔记
- **THEN** widget 中部显示 1 行(不补占位),空行不留;右上角"+"按钮

#### Scenario: 4x2 点笔记项启动详情
- **WHEN** 用户在桌面 widget 点第 2 条笔记项
- **THEN** MainActivity 启动,navigate 到 `quicknote/detail/{noteId}`,显示该笔记详情 + wordCount + lastAiOp metadata(若有)

#### Scenario: 4x2 点笔记项不影响 widget UI
- **WHEN** `ActionCallback<OpenNoteAction>` 触发
- **THEN** widget 进程内同步处理,无 UI 闪烁;MainActivity 启动在新任务栈

### Requirement: Widget UI uses Glance composables and Material You colors

`QuickNoteWidget.provideGlance()` MUST 用 Glance API(`androidx.glance.layout.*` / `androidx.glance.text.*` / `androidx.glance.unit.ColorProvider`),**禁止**直接 import `androidx.compose.foundation.layout.*`(widget host process 不可用 Compose)。

颜色 MUST 走 `ColorProvider(R.color.<token>)`,从 Material You 主题取色(由 Compose theme 提供 `androidx.compose.ui.graphics.Color`);本 change 不引入 `glance-material3` 依赖,颜色从 `R.color` 读取。

#### Scenario: Widget 渲染在 widget host 进程
- **WHEN** launcher 把 widget 添加到桌面并 resize 到 2x2
- **THEN** widget UI 在 widget host process(非 App process)渲染;Glance 调用 `provideGlance` 拿到 widget UI

#### Scenario: Widget 不 import Compose foundation
- **WHEN** grep `core/widget/**/*.kt`
- **THEN** 0 个 import 出现 `androidx.compose.foundation.*`;只允许 `androidx.glance.*` 与 `androidx.compose.ui.graphics.Color`(颜色转换)

### Requirement: Widget main-path refresh via WidgetUpdater

`QuickNoteWidgetUpdater : @Singleton` MUST 提供 `suspend fun updateAll(context: Context)`,内部调 Glance `QuickNoteWidget().updateAll(context)`。

主路径触发点(M4-1 实现,后续 §7 落):
- `NoteRepository.upsert` 后(走 Hilt `Lazy<WidgetUpdater>` 注入,或 `AppContext` 拿 `WidgetUpdater` 单例)
- `NoteRepository.delete` 后
- `QuickNoteEditorViewModel.saveNote` 后(M1 既有 upsert 路径,加 1 行)
- `QuickNoteDetailViewModel.delete` 后
- AI `acceptReplace` 后(`AiActionViewModel.acceptReplace` 内,在 `_state.value = Idle` 之前调,避免 UI 已关)

每次 `updateAll` MUST 在 `IO` dispatcher 上执行,不阻塞主线程。

#### Scenario: 新建笔记后 widget 立即刷新
- **WHEN** 用户在编辑器屏保存一条笔记
- **THEN** `NoteRepository.upsert` 落库 → `WidgetUpdater.updateAll` 触发 → Glance 重新拉 `observeRecent(N)` → widget UI 更新显示新笔记

#### Scenario: 删除笔记后 widget 立即刷新
- **WHEN** 用户在详情屏删除笔记
- **THEN** `WidgetUpdater.updateAll` 触发 → widget UI 不再显示该笔记

#### Scenario: AI acceptReplace 后 widget 立即刷新
- **WHEN** 用户在 AI 流式面板接受 AI 输出
- **THEN** `acceptReplace` 落库(content + lastAiOp + lastAiAt)→ `WidgetUpdater.updateAll` 触发 → widget UI 显示新内容(updatedAt 变化,排到最近)

#### Scenario: updateAll 在 IO dispatcher
- **WHEN** `WidgetUpdater.updateAll(context)` 被调用
- **THEN** 内部 `withContext(Dispatchers.IO) { QuickNoteWidget().updateAll(context) }`,不阻塞 caller 线程

### Requirement: Widget fallback refresh via WorkManager 15min

`QuickNoteWidgetWorker : CoroutineWorker` MUST 在 `doWork()` 内调 `QuickNoteWidget().updateAll(applicationContext)`。

`Application.onCreate` 时(在 `WritingApp.onCreate`) MUST 注册周期任务:
```kotlin
val periodic = PeriodicWorkRequestBuilder<QuickNoteWidgetWorker>(15, TimeUnit.MINUTES).build()
WorkManager.getInstance(this).enqueueUniquePeriodicWork(
    "quicknote-widget-refresh",
    ExistingPeriodicWorkPolicy.KEEP,
    periodic,
)
```

`ExistingPeriodicWorkPolicy.KEEP` 保证 App 重启不重复 enqueue。

#### Scenario: WorkManager 15min 兜底刷新
- **WHEN** 用户在桌面放 widget 后 15 分钟(App 未被主动打开)
- **THEN** WorkManager 触发 `QuickNoteWidgetWorker` → `updateAll` → widget UI 更新(即使无笔记增删改)

#### Scenario: App 重启不重复 enqueue
- **WHEN** App 重启后 `WritingApp.onCreate` 再调 `enqueueUniquePeriodicWork`
- **THEN** WorkManager 已有同名任务,`KEEP` policy 保留旧任务,不重置下次执行时间

#### Scenario: WorkManager 兜底不阻塞 UI
- **WHEN** `QuickNoteWidgetWorker.doWork()` 执行
- **THEN** `CoroutineWorker` 在后台 IO dispatcher 上跑,不影响主线程

### Requirement: AndroidManifest declares widget receiver

`AndroidManifest.xml` MUST 在 `<application>` 内声明 `QuickNoteWidgetReceiver`:
```xml
<receiver
    android:name=".core.widget.QuickNoteWidgetReceiver"
    android:exported="false"
    android:label="@string/widget_2x2_title">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/widget_info" />
</receiver>
```

`res/xml/widget_info.xml` MUST 包含 `appwidget-provider` 元数据:`minWidth=160dp minHeight=160dp targetCellWidth=2 targetCellHeight=2 resizeMode=horizontal|vertical updatePeriodMillis=0 initialLayout=@layout/widget_initial previewLayout=@drawable/widget_preview widgetCategory=home_screen`。

#### Scenario: Widget 可添加到桌面
- **WHEN** 用户长按桌面 → widget → 找到"随手记"widget
- **THEN** 添加后 widget 出现在桌面,点击"+"或笔记项正常工作

#### Scenario: Widget 元数据正确
- **WHEN** 系统读 `widget_info.xml`
- **THEN** `minWidth=160dp`(2x2 起步),`targetCellWidth=2` 提示 launcher 这是 2x2 widget,`resizeMode=horizontal|vertical` 允许拉伸

#### Scenario: 系统自带的 30min 更新被禁用
- **WHEN** `widget_info.xml` `updatePeriodMillis=0`
- **THEN** 系统不走默认 30min 最小更新,完全由 WorkManager 15min 兜底

### Requirement: PendingIntent routes via TaskStackBuilder for back stack

`WidgetIntents.kt` MUST 提供:
- `createNotePendingIntent(context)`:`TaskStackBuilder` 构造 → MainActivity `quicknote/edit?prefillFocus=true`
- `openNotePendingIntent(context, noteId)`:`TaskStackBuilder` 构造 → MainActivity `quicknote/detail/{noteId}`

back 行为:widget tap → MainActivity(指定 route)→ back → launcher 桌面(不是 App 内的列表页)。

#### Scenario: 新建按钮 back 回桌面
- **WHEN** 用户在 widget 点"+" → MainActivity 启动到编辑页 → 用户按 back
- **THEN** 返回到 launcher 桌面,不是 App 内的列表页(`TaskStackBuilder` 任务栈独立)

#### Scenario: 笔记项 back 回桌面
- **WHEN** 用户在 widget 点笔记项 → MainActivity 启动到详情 → 用户按 back
- **THEN** 返回到 launcher 桌面

### Requirement: Widget i18n via R.string

5 个 widget 相关 key MUST 走 `R.string.widget_*`(中文 + 英文 TODO 占位):

| key | 中文 | 用途 |
| --- | --- | --- |
| `widget_2x2_title` | 随手记 | 2x2 widget 顶部标题 + AndroidManifest label |
| `widget_4x2_title` | 随手记 | 4x2 widget 顶部标题 |
| `widget_empty` | 还没有笔记\n点 + 创建第一条 | 空状态 |
| `widget_create_cd` | 新建笔记 | "+" content description |
| `widget_open_note_cd` | 打开笔记 %1$s | 笔记项 content description(`%1$s` = 标题) |

Composable / Glance composable 内 MUST 通过 `LocalContext.current.getString(R.string.widget_*)` 引用;**禁止**硬编码中文字符串。

#### Scenario: 英文系统语言显示 TODO 占位
- **WHEN** 系统语言为英文,`values-en/strings.xml` 中 `widget_2x2_title="TODO(en): widget_2x2_title"`
- **THEN** widget 顶部显示 `TODO(en): widget_2x2_title`,APK 仍正常构建,M5 polish 时替换

#### Scenario: 中文系统语言显示权威中文
- **WHEN** 系统语言为中文
- **THEN** widget 顶部显示"随手记";grep 源码无 `widget_2x2_title = "随手记"` 等硬编码中文

### Requirement: Widget package layout under core/widget/

`core/widget/` MUST 自包含:
```
core/widget/
├── QuickNoteWidgetReceiver.kt
├── QuickNoteWidget.kt
├── QuickNoteWidgetRepository.kt
├── QuickNoteWidgetUpdater.kt
├── QuickNoteWidgetWorker.kt
├── WidgetIntents.kt
└── OpenNoteAction.kt
```

`core/widget/**` MUST **不** import `feature/quicknote/**` 内部(除 Nav route 类型 / 通过 `MainActivity` 启动);**不** import `core/ai/**`;**不** import `feature/aiwriting/**` — widget 是平台入口(放最外层 `core/widget/`),与 Nav 根(`app/AppNav.kt`)同等级别。

#### Scenario: widget 目录无 feature 内部 import
- **WHEN** grep `core/widget/**/*.kt`
- **THEN** 0 个 import 出现 `feature.quicknote.detail.*` / `feature.aiwriting.*` / `core.ai.*`;只 import `feature.quicknote.edit.QuickNoteEditorViewModel`(如需要 prefillFocus 透传类型)

### Requirement: Widget 测试覆盖 Repository + Updater

JUnit5 + Turbine MUST 覆盖:
- `QuickNoteWidgetRepository.observeRecent(n)` 走通(emit N 条)
- `QuickNoteWidgetUpdater.updateAll()` 不抛异常(Mock Glance 调用或用 Robolectric)
- `OpenNoteAction` parameter 解析 noteId 正确

`WidgetInfo` 元数据可用 `aapt dump xmltree` 验证(放整体验收)。

#### Scenario: observeRecent 走通
- **WHEN** `FakeNoteRepository.observeRecent(3)` 调用
- **THEN** Flow emit 最多 3 条最近笔记,按 updatedAt desc

#### Scenario: WidgetUpdater.updateAll 不抛异常
- **WHEN** Mock `QuickNoteWidget().updateAll(context)` 后调 `WidgetUpdater.updateAll(context)`
- **THEN** 不抛异常,内部 `withContext(IO)` 完成

### Requirement: Widget 数据访问不破坏 Note schema

本 change MUST NOT 修改 `NoteEntity` / `NoteDao` / `Note` 字段;`observeRecent` 走 `noteDao.observeAll()` + `take(limit)` 内存截断,**不**新增 DAO SQL。

#### Scenario: Note 字段保持 v2 schema
- **WHEN** `git diff openspec/changes/home-screen-widget/ core/data/`
- **THEN** 0 个字段新增 / 0 个字段类型变更;`AppDatabase` 仍 `version = 2`,无新 MIGRATION

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

### Requirement: 1x4 widget uses only Glance 1.1+ supported APIs

`QuickNote1x4Widget` MUST **不** 引用以下不存在于 Glance 1.1+ 的 API(2026-06-20 实地验证 `compileDebugKotlin` 报 unresolved):
- `androidx.glance.unit.RoundedCornerRadius`(数据类不存在)
- `Modifier.fillMaxHeight()`(modifier 不存在)
- `background(color, RoundedCornerRadius)` 多参重载(背景只接 `ColorProvider` 单参,圆角走 `Modifier.cornerRadius(...)` 链式)

`QuickNote1x4Widget` 圆角 MUST 改用 `GlanceModifier.cornerRadius(radius: Dp)`(从 `androidx.glance.appwidget.cornerRadius` import,具体路径以 `libs.versions.toml` 实际 Glance 版本为准);按钮高度 MUST 改用 `defaultWeight()` + `Modifier.height(Dp)` 组合。

#### Scenario: 1x4 widget 编过 assembleDebug
- **WHEN** 跑 `./gradlew :app:assembleDebug`
- **THEN** `compileDebugKotlin` 阶段 0 个 `Unresolved reference` / `Argument type mismatch` 错误,产物 `app/build/outputs/apk/debug/app-debug.apk` 存在

#### Scenario: 1x4 widget 圆角走 chain modifier
- **WHEN** 读 `QuickNote1x4Widget.kt`
- **THEN** import 不出现 `RoundedCornerRadius`;`background()` 调用形如 `background(cp(cWhite))`(单参)或 `background(cp(cBlue))`;圆角通过 `GlanceModifier.cornerRadius(16.dp)` 链式设置

#### Scenario: 1x4 widget 高度用 defaultWeight + height
- **WHEN** 读 `QuickNote1x4Widget.kt` 按钮 Box
- **THEN** 不出现 `fillMaxHeight()`;改用 `Modifier.defaultWeight().height(48.dp)` 之类组合;外层 `Row` `verticalAlignment = Alignment.CenterVertically`

