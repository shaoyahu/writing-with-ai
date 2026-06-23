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

### Requirement: GlanceStateDefinition persists widget state via DataStore

`QuickNoteWidget` 与 `QuickNote1x4Widget` MUST 在 `glanceAppWidget.stateDefinition` 注入自定义 `WidgetStateDefinition`,数据后端走 DataStore(`androidx.datastore.core.DataStore` + `kotlinx.serialization` Serializer);**不** 用 Glance 默认 `PreferencesGlanceStateDefinition`(SharedPreferences)。

`WidgetState` MUST 是 `@Serializable data class`(kotlinx.serialization):
- `cachedNoteIds: List<String>` — 最近 N 条笔记 id 缓存(widget 进程被杀后 stale 兜底显示)
- `lastRefreshAt: Long` — 上次 refresh epoch millis(0 = 未 refresh 过)
- `romVendor: RomVendor` — `enum RomVendor { MIUI / EMUI / COLOROS / ORIGINOS / AOSP }`

DataStore 文件名 MUST 为 `widget_state`,目录与 `consent_store` / `prompt_template_store` 同级;Serializer MUST 是自定义 `WidgetStateSerializer : Serializer<WidgetState>`(非 default JSON,GlanceStateDefinition 内部用)。

Widget receiver 注入方式:`override val stateDefinition: GlanceStateDefinition<*> = WidgetStateDefinition(context.applicationContext)`(在 `QuickNoteWidgetReceiver` 与 `QuickNote1x4WidgetReceiver` 各一份)。

#### Scenario: widget 进程被杀后状态恢复

- **WHEN** widget 进程被国产 ROM(MIUI 等)杀 → 30s 后系统拉起 widget host process
- **THEN** `WidgetStateDefinition.getDataStore(context, fileKey)` 返回原 `widget_state` DataStore 实例;`provideGlance` 内 `currentState<WidgetState>()` 拿到 stale `cachedNoteIds`;若 `lastRefreshAt` 距今 < 15 分钟 → 显示该 id 列表(即便 Room 已更新)作为兜底;若 > 15 分钟 → 显示空状态 + ROM hint + "最后更新于 X 分钟前"占位

#### Scenario: 默认 WidgetState 初始化

- **WHEN** 用户首次添加 widget,`widget_state` DataStore 还不存在
- **THEN** Serializer 默认值生效:`cachedNoteIds = emptyList()` / `lastRefreshAt = 0L` / `romVendor = RomDetector.current()`(此时 AOSP 或 4 国产之一)

#### Scenario: DataStore round-trip 完整性

- **WHEN** `WidgetState(cachedNoteIds = ["n1", "n2"], lastRefreshAt = 1234567890L, romVendor = MIUI)` 写入 DataStore → 读回
- **THEN** 字段值与写入完全一致(无字段丢失 / 类型丢失)

### Requirement: Widget colors derive from GlanceTheme ColorScheme

`QuickNoteWidget` 与 `QuickNote1x4Widget` MUST 删除 6 个硬编码 hex 颜色(`cBlue` / `cWhite` / `cBg` / `cTitle` / `cBody` / `cMeta` 与 `cp(...)` 工具函数);改走 `WidgetTheme.kt` 暴露的 `WidgetColors` token,`@Composable @ReadOnlyComposable fun widgetColors(): WidgetColors`。

`WidgetColors` MUST 含 6 个 token(`ColorProvider` 双套 light/dark):
- `widgetPrimary`(主色 / "+" 按钮背景)
- `widgetBackground`(widget 整体背景)
- `widgetOnBackground`(笔记标题色)
- `widgetOnSurfaceVariant`(正文色)
- `widgetPrimaryContainer`(强调)
- `widgetOutline`(边框)

6 个 token MUST 从 `androidx.compose.material3.MaterialTheme.colorScheme` 派生(系统跟随 + Material You 取色);系统暗色 / 亮色 / 跟随三档自适应。

#### Scenario: 删除硬编码颜色

- **WHEN** `grep "Color(0xFF" app/src/main/java/com/yy/writingwithai/core/widget/QuickNoteWidget.kt app/src/main/java/com/yy/writingwithai/core/widget/QuickNote1x4Widget.kt`
- **THEN** 0 个匹配(颜色硬编码全部走 token)

#### Scenario: widget 颜色跟系统暗色 / 亮色

- **WHEN** 系统设置切到深色模式 → widget 重新渲染
- **THEN** widget 6 个 token 自动应用 `colorScheme` 的 dark 套色;反之亦然

#### Scenario: Material You 取色生效

- **WHEN** Android 12+ 设备用户在系统设置选 Material You 壁纸取色
- **THEN** widget `widgetPrimary` 跟随 wallpaper 派生色调(`colorScheme.primary`);非 hex 硬编码

### Requirement: Relative time formatting uses DateUtils.getRelativeTimeSpanString

`core/widget/QuickNoteWidget.kt` 与 `QuickNote1x4Widget.kt` 内 `formatRelativeTime` / `formatRelativeTimeCompact` MUST 改 `android.text.format.DateUtils.getRelativeTimeSpanString(epochMs, now, DateUtils.MINUTE_IN_MILLIS, flags)`,`flags` 含 `FORMAT_ABBREV_RELATIVE`;**删除** 30 行手写 `when (diff < m / h / d / 7d)` 分支。

输出 MUST locale-aware:中文系统 → "1 小时前" / "刚刚";英文系统 → "1h ago" / "just now";日文 / 其他 locale → framework 默认。

#### Scenario: locale-aware 时间格式

- **WHEN** `formatRelativeTime(context, epochMs = now - 60_000)` 在中文系统调用
- **THEN** 输出 "1 分钟前" 或 framework 等价 locale 文案(非固定英文 "1 minute ago")

#### Scenario: 短时间 < 1 分钟

- **WHEN** `epochMs = now - 10_000`(10 秒前)
- **THEN** 输出 "刚刚"(中文)/ "just now"(英文)/ locale 等价(非 "0 分钟前")

#### Scenario: 删除手写 when 分支

- **WHEN** `grep "when (diff" app/src/main/java/com/yy/writingwithai/core/widget/`
- **THEN** 0 个匹配(手写 when 已替换 framework)

### Requirement: Domestic ROM optimization hints displayed on empty widget

`QuickNoteWidget` 与 `QuickNote1x4Widget` 在 `notes.isEmpty()` 状态 MUST 走 `RomDetector.current()` 命中分支显示 hint 文案;`RomDetector` MUST 是 `object`,内部 `current(): RomVendor` 用 `Build.MANUFACTURER` + `Build.BRAND` 判 4 国产 ROM。

ROM 命中映射:
- `MIUI`:`Build.MANUFACTURER == "Xiaomi"` || `Build.BRAND.contains("Redmi", ignoreCase=true)` → 显示 `R.string.widget_rom_miui_hint`
- `EMUI`:`Build.MANUFACTURER == "HUAWEI"` || `Build.BRAND.contains("Honor", ignoreCase=true)` → 显示 `R.string.widget_rom_emui_hint`
- `COLOROS`:`Build.MANUFACTURER == "OPPO"` || `Build.BRAND.contains("realme", ignoreCase=true)` → 显示 `R.string.widget_rom_coloros_hint`
- `ORIGINOS`:`Build.MANUFACTURER == "vivo"` || `Build.BRAND.contains("iQOO", ignoreCase=true)` → 显示 `R.string.widget_rom_originos_hint`
- `AOSP`(默认 / Pixel / 其他):**不** 显示 hint,只显示既有 `widget_empty` 文案

4 个 hint 文案 MUST 在 `values/strings.xml`(中文权威)+ `values-en/strings.xml`(TODO 占位)。

#### Scenario: 小米设备显示 MIUI hint

- **WHEN** `Build.MANUFACTURER = "Xiaomi"` + `RomDetector.current() == MIUI` + widget 空状态
- **THEN** widget 显示 "还没有笔记\n点 + 创建第一条"(既有)+ 下行 "小米设备请到设置 → 电池 → 自启动管理开启本应用"(`R.string.widget_rom_miui_hint`)

#### Scenario: AOSP / Pixel 设备不显示 ROM hint

- **WHEN** `Build.MANUFACTURER = "Google"` + `RomDetector.current() == AOSP` + widget 空状态
- **THEN** widget 仅显示 "还没有笔记\n点 + 创建第一条"(既有),**不** 显示额外 ROM hint

#### Scenario: ROM 检测覆盖子品牌

- **WHEN** `Build.BRAND = "Redmi"`(红米子品牌)+ `Build.MANUFACTURER = "Xiaomi"`
- **THEN** `RomDetector.current()` 返回 `MIUI`,显示 `R.string.widget_rom_miui_hint`

#### Scenario: 已知 ROM 全部命中

- **WHEN** `grep -E "Build.MANUFACTURER|Build.BRAND" app/src/main/java/com/yy/writingwithai/core/widget/RomDetector.kt`
- **THEN** 至少含 8 个命中(`Xiaomi` + `Redmi` + `HUAWEI` + `Honor` + `OPPO` + `realme` + `vivo` + `iQOO`)

