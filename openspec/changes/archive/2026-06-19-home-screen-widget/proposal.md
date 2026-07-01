## Why

M0-M3 已落地 4 个里程碑:`init-android-project`(脚手架)/ `quick-note-feature`(Note CRUD)/ `ai-abstraction-layer`(AI 抽象层)/ `ai-writing-actions`(AI 操作 UI)。但用户**必须先打开 App 才能用** — 不支持桌面快速记录。roadmap §3.3"桌面小组件"是 v1 价值链的关键扩展:用户在桌面 1 tap 即可"新建一条笔记"或"打开最近一条"，从"App 内主动记录"扩展为"随时随地随手记"，与 M1 随手记核心定位一致。

本 change 是 roadmap §8"桌面小组件(Glance)"的首次落地，**仅做最小可用版本**:2x2 widget(最近 1 条 + 新建按钮)+ 4x2 widget(最近 3 条 + 新建按钮)。国产 ROM(小米/华为/OPPO/vivo)兼容性留 M5 polish，本 change 只验证 AOSP / Pixel Launcher 跑通。

## What Changes

- **新增 widget receiver**(`core/widget/QuickNoteWidgetReceiver.kt`):`@AndroidEntryPoint`，声明在 `AndroidManifest.xml`;支持 2x2 / 4x2 两种 size mode;Glance `GlanceAppWidget` + `GlanceAppWidgetReceiver` 模式。
- **新增 widget UI**(`core/widget/QuickNoteWidget.kt`):`class QuickNoteWidget : GlanceAppWidget`，根据 `SizeMode` 切 2x2 / 4x2 渲染分支:
  - 2x2:1 条最近笔记(标题 + relativeTime)+ 底部"+"新建按钮
  - 4x2:3 条最近笔记(标题 + relativeTime，可滚动 LazyColumn 等价 `LazyColumn` in Glance)+ 右上角"+"新建按钮
- **新增 widget 数据源**(`core/widget/QuickNoteWidgetRepository.kt`):内部包装 `NoteRepository.observeNotesWithTags(query=null, tag=null).map { list.take(n) }`，走 Flow,Glance side 用 `provideContent` 订阅。
- **新增 widget 刷新器**(`core/widget/QuickNoteWidgetUpdater.kt`):Hilt 单例，提供 `updateAll(context)`(主路径 — 笔记增删改时调) + `WorkManager` 周期任务兜底(15 min,Glance `enqueue` API)。
- **新增 WorkManager worker**(`core/widget/QuickNoteWidgetWorker.kt`):兜底刷新，15 min 周期。
- **改 AndroidManifest.xml**:`QuickNoteWidgetReceiver` 注册，加 `<intent-filter>` `android.appwidget.action.APPWIDGET_UPDATE`;权限 `RECEIVE_BOOT_COMPLETED`(可选，boot 后自启 widget)。
- **改 res/xml/widget_info.xml**:新增 widget metadata(2x2 / 4x2 min size / target cell / resize mode / preview layout)。
- **改 NoteRepository**:无 schema 变更;新增 `observeRecent(limit: Int): Flow<List<Note>>`(走 `noteDao.observeAll()` + `take(n)`，见 M1 既有 `observeAll()`)。
- **改 QuickNoteEditorViewModel**:支持 `prefillFocus: Boolean` 参数(M1 r1 既有 `prefill` ? 走现有 path;若没有则新增),widget 点击"新建"启动 App 到编辑页时传入 true，自动 focus 输入框。
- **改 AppNav**:route `quicknote/edit?prefillFocus=true` 解析参数;`QuickNoteEdit` data class 加 `prefillFocus: Boolean = false`。
- **新增 PendingIntent 路由**(`core/widget/WidgetIntents.kt`):
  - "新建"按钮 → `PendingIntent.getActivity(...)` 启动 MainActivity 到 `quicknote/edit?prefillFocus=true`
  - "最近一条笔记" → `PendingIntent.getActivity(...)` 启动 MainActivity 到 `quicknote/detail/{id}`
  - 走 `TaskStackBuilder` 构造回退栈(roadmap §7.4 拍板)，保证 back 返回 launcher。
- **新增 i18n**(`values/strings.xml` + `values-en/`):
  - `widget_2x2_title` = "随手记" — widget 标题(2x2 顶部)
  - `widget_4x2_title` = "随手记" — widget 标题(4x2 顶部)
  - `widget_empty` = "还没有笔记\n点 + 创建第一条" — 空状态
  - `widget_create_cd` = "新建笔记" — "+" content description
  - `widget_open_note_cd` = "打开笔记 %1$s" — 笔记项 content description
- **新增依赖**:`androidx.work:work-runtime-ktx`(WorkManager 周期任务);`androidx.glance:glance-appwidget` 已 M0 配。
- **新增测试**:JUnit5 + Turbine 验 `QuickNoteWidgetRepository.observeRecent(n)` 走通(取最近 N 条);`WidgetUpdater.updateAll()` 调 Glance API 不抛异常。
- **新增资源预览**:`res/drawable/widget_preview.xml` — widget 添加时的预览图(简化版:最近 1 条 + +号)。
- **BREAKING**:无
- **不引入**:
  - 国产 ROM 适配(M5 polish)
  - widget 配置 Activity(widget 风格固定，不支持用户配置字号 / 主题)
  - widget 内 AI 操作(超出 v1 scope)
  - 笔记 pin 到 widget(M5 follow-up)
  - widget 透明 / 自定义背景 / 动态取色(M5)

## Capabilities

### New Capabilities
- `home-screen-widget`:2x2 / 4x2 Glance widget;QuickNoteWidgetReceiver + QuickNoteWidget(双 size mode)+ QuickNoteWidgetUpdater(updateAll 主路径 + WorkManager 兜底 15min)+ QuickNoteWidgetWorker + PendingIntent 路由(新建 + 打开最近)+ widget_info.xml metadata + i18n

### Modified Capabilities
- `quick-note`:编辑器屏支持 `prefillFocus: Boolean` 参数(widget "新建"启动时 focus 输入框);`AppNav.kt` `QuicknoteEdit` route 加 `prefillFocus: Boolean = false`;`NoteRepository` 加 `observeRecent(limit: Int): Flow<List<Note>>`(取最近 N 条，无 schema 变更)

## Impact

- **新增 package**:
  - `core/widget/` — QuickNoteWidgetReceiver / QuickNoteWidget / QuickNoteWidgetRepository / QuickNoteWidgetUpdater / QuickNoteWidgetWorker / WidgetIntents
- **新增 res**:
  - `res/xml/widget_info.xml` — widget metadata(2x2 / 4x2 min/max)
  - `res/drawable/widget_preview.xml` — widget 添加预览图
  - 5 个 R.string.widget_* 双语
- **修改**:
  - `AndroidManifest.xml` — 加 widget receiver + APPWIDGET_UPDATE intent-filter
  - `feature/quicknote/edit/QuickNoteEditorScreen.kt` + `QuickNoteEditorViewModel.kt` — `prefillFocus` 参数
  - `app/AppNav.kt` — `QuicknoteEdit(prefillFocus: Boolean = false)` data class + 路由解析
  - `core/data/repo/NoteRepository.kt` — `observeRecent(limit: Int): Flow<List<Note>>`
- **新增依赖**:`androidx.work:work-runtime-ktx`(libs.versions.toml 加 version + library entry)
- **风险**:
  - Glance API 在 widget receiver 内必须用 Glance 自己的 Composable(`GlanceModifier` + `Column` 等)，不能直接用 Compose 组件
  - Glance 渲染在 widget host process(非 App process)，跨进程通信通过 `GlanceStateDefinition` + `androidx.datastore` 持久化偏好(本 change 暂不持久化偏好，固定样式)
  - widget 点击后启动 MainActivity 到指定 route，需要 back 栈正确(TaskStackBuilder)
  - 国产 ROM 限制(小米后台 / 华为电池优化)会让 widget 刷新不及时 — 留 M5 polish，本 change 不做适配
  - WorkManager 兜底 15min:Glance API 推荐用 `enqueueUniquePeriodicWork`，避免重复 enqueue