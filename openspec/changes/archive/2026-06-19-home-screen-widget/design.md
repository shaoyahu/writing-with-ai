## Context

M0-M3 已落地 4 个里程碑:`init-android-project`(脚手架)/ `quick-note-feature`(Note CRUD + 列表 / 详情 / 编辑 + 分享 + 字数 + tags)/ `ai-abstraction-layer`(AiGateway + AnthropicCompatibleAdapter + FakeProvider + AiHistory + SSE + 错误降级)/ `ai-writing-actions`(ActionSheet + StreamingPanel + 4 态状态机 + acceptReplace NonCancellable + 编辑器零改动 + AppNav 零新增 route)。

需求进入 M4-1:把"App 内主动记录"扩展为"随时随地随手记"。roadmap §3.3 拍板"桌面小组件"是 v1 价值链的关键扩展，§8 给出 widget 形式 / 交互 / 兼容性细则，§7.4 拍板 widget 启动的回退栈用 `TaskStackBuilder`。

目标:
- 用户在桌面 1 tap"新建"→ 启动 App 到快速记录页(自动 focus 输入框)
- 用户在桌面 1 tap"最近一条笔记"→ 启动 App 到该笔记详情
- 笔记增删改 → widget 自动刷新(主路径 `WidgetUpdater.updateAll`)
- 兜底刷新(15 min)→ WorkManager `enqueueUniquePeriodicWork`
- Glance 样式(Compose for AppWidget)+ Material You 取色
- AOSP / Pixel Launcher 跑通;国产 ROM(小米 / 华为 / OPPO / vivo)留 M5 polish

## Goals / Non-Goals

**Goals:**
- 2x2 widget:1 条最近笔记(标题 + relativeTime)+ 底部"+"按钮
- 4x2 widget:3 条最近笔记(标题 + relativeTime，可滚动)+ 右上角"+"按钮
- "新建"按钮 → `PendingIntent` 启动 MainActivity 到 `quicknote/edit?prefillFocus=true`
- "最近一条"按钮 → `PendingIntent` 启动 MainActivity 到 `quicknote/detail/{id}`
- 主路径刷新:笔记增删改时调 `WidgetUpdater.updateAll(context)`，立即更新 widget UI
- 兜底刷新:WorkManager 周期任务 15 min,Glance `updateAll(context)`
- i18n 完整(中文 + 英文 TODO 占位)
- 走 Glance(`androidx.glance:glance-appwidget` 1.1.1,M0 已配)

**Non-Goals:**
- 国产 ROM 适配(M5 polish)
- widget 配置 Activity(固定样式)
- widget 内 AI 操作(超出 v1 scope)
- 笔记 pin 到 widget(M5 follow-up)
- widget 透明 / 自定义背景 / 动态取色(M5)
- GlanceStateDefinition 持久化偏好(本 change 固定样式)
- widget 数据访问的加密 — 数据从 Room 数据库读，Room 已用 `EncryptedSharedPreferences` 保护 apikey(M2 既有);widget 不涉及 apikey
- widget 的 `EncryptedSharedPreferences` 加密 — 不涉及 apikey，无加密需求

## Decisions

### 1. Glance `GlanceAppWidget` + `GlanceAppWidgetReceiver` 模式

**Why:** Glance 1.1.x 官方推荐模式 — `GlanceAppWidget` 暴露 `provideGlance(context, id)` 渲染入口，`GlanceAppWidgetReceiver` 在 `AndroidManifest.xml` 注册并桥接到 widget host。Compose 派生但 API 不同(`GlanceModifier` / `androidx.glance.layout.Column` / `androidx.glance.text.Text`)，不能直接用 `androidx.compose.foundation.layout.Column`。

**替代方案:** 传统 `AppWidgetProvider` + RemoteViews — API 老旧，Material You 取色需手动处理，放弃。

### 2. widget 数据通过 `NoteRepository.observeRecent(limit: Int): Flow<List<Note>>`

```kotlin
fun observeRecent(limit: Int): Flow<List<Note>> =
    noteDao.observeAll().map { list -> list.take(limit).map { it.toModel() } }
```

**Why:** widget 只需要最近 N 条，不关心 tags / wordCount / readMinutes(那些是详情屏的事)。复用 M1 `NoteDao.observeAll()`，不加新 DAO 方法。

**替代方案:** 加 `noteDao.observeRecent(limit: Int)` SQL 端 `LIMIT` — Room KSP 重生成，启动慢;`take(n)` 内存截断足够(单用户笔记数 < 10k)。

### 3. widget 刷新:主路径 `WidgetUpdater.updateAll` + 兜底 WorkManager 15min

**Why:** 笔记增删改事件主路径(用户主动操作时立即刷新),WorkManager 兜底(进程被杀 / 国产 ROM 后台限制 / 用户不在 App 内时仍能保证数据大致新鲜)。

**主路径触发点**(待后续 §7 实现):
- `NoteRepository.upsert` 后调 `WidgetUpdater.updateAll(context)`(M1 r1 还未做 — 加在 upsert 后)
- `NoteRepository.delete` 后调
- `NoteRepository.setPinned` 后调(M1 既有，不需要重排)
- AI `acceptReplace` 后调(走 NonCancellable 之外，新 launch)

**替代方案:** 只用 WorkManager — 用户写笔记后要等 15min 才看到 widget 更新，体验差。只用 `updateAll` — 进程被杀后 widget 不刷新，数据陈旧。

### 4. `PendingIntent.getActivity` 走 `TaskStackBuilder` 构造回退栈

```kotlin
val createIntent = MainActivity intent + extra "route"="quicknote/edit?prefillFocus=true"
val backStack = TaskStackBuilder.create(context)
    .addNextIntentWithParentStack(createIntent)
    .getPendingIntent(0, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)
```

**Why:** roadmap §7.4 拍板"从 widget 启动的快速记录卡片用 single-task 独立任务栈，返回时优先回主任务栈"。`TaskStackBuilder` 让 back 行为:widget tap → 编辑页 → back → launcher(M3 之前的 nav stack 也可，因 `popBackStack` 正确)。

**替代方案:** `PendingIntent.getActivity` 不带 stack — back 行为可能直接退出 App 到 launcher，而非回到 launcher 桌面。

### 5. `SizeMode` 双分支:`Sizes = [DpSize(160.dp, 160.dp), DpSize(250.dp, 110.dp)]`

```kotlin
override val sizeMode: SizeMode = SizeMode.Single

// 在 provideGlance 内根据 currentSize 判断:
when {
    currentSize.width <= 160.dp -> render2x2(state)
    else -> render4x2(state)
}
```

**Why:** Glance `SizeMode.Single` 适合响应式(一个 widget 可拖拽拉伸 2x2 / 4x2 / 4x4)，比 `SizeMode.Exact` + 多 receiver 注册更简单。

**替代方案:** 双 receiver(QuickNoteWidgetReceiver2x2 + QuickNoteWidgetReceiver4x2)— user 在 widget 选择器看到 2 个选项，UX 差。

### 6. widget 点击笔记项 → `actionRunCallback<OpenNoteAction>()` (Glance Action API)

**Why:** Glance 推荐用 `ActionCallback` 而不是直接 `PendingIntent`(后者会让 widget UI 闪一下再跳)。`actionRunCallback` 在 widget host 进程内执行，延迟小。

**实现:**
```kotlin
override suspend fun onAction(context: Context, glanceId: GlanceId, action: ActionParameters) {
    when (action.actionClass) {
        OpenNoteAction::class.java -> {
            val noteId = action[OpenNoteAction.KEY_NOTE_ID]
            context.startActivity(MainActivity intent + extra route "quicknote/detail/$noteId")
        }
    }
}
```

**替代方案:** `PendingIntent.getActivity` — 见 §4，只用于"新建"按钮(没上下文 noteId 的场景)。

### 7. Glance `LazyColumn` vs `Column`

**4x2 widget** 需要可滚动(3 条以上):Glance 提供 `LazyColumn`，但需要 widget 高度足够(2x2 高度 110dp 不可滚)。本 change 固定 3 条，不超 widget 高度 — 用普通 `Column` 即可。`LazyColumn` 留 M5 当笔记多时可滚动。

**Why:** 简化，M3 闭环的逻辑(`LazyColumn` 增加复杂度，3 条没意义)。

### 8. widget UI 文案走 R.string + content description

所有用户可见字符串走 `R.string.widget_*`(中文 + 英文 TODO 占位);`+` 按钮 / 笔记项 content description 走 `R.string.widget_create_cd` / `R.string.widget_open_note_cd`，无障碍朗读正常。

### 9. WorkManager 兜底周期 15min

```kotlin
val periodicWork = PeriodicWorkRequestBuilder<QuickNoteWidgetWorker>(15, TimeUnit.MINUTES)
    .build()
WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "quicknote-widget-refresh",
    ExistingPeriodicWorkPolicy.KEEP,
    periodicWork,
)
```

**Why:** roadmap §8.2 "短间隔(15 min)兜底轮询"。`KEEP` policy 保证不重复 enqueue。

**替代方案:** AlarmManager 定时 — 国产 ROM 限制更严;Glance API 自带 WorkManager 集成更自然。

### 10. widget metadata `res/xml/widget_info.xml`

```xml
<appwidget-provider xmlns:android="..."
    android:minWidth="160dp" android:minHeight="160dp"
    android:targetCellWidth="2" android:targetCellHeight="2"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/widget_initial"
    android:previewLayout="@drawable/widget_preview"
    android:widgetCategory="home_screen"
/>
```

**Why:** `targetCellWidth/Height` 提示 launcher 这是 2x2 widget;`resizeMode` 允许用户拖拽拉伸(Glance `SizeMode.Single` 响应);`updatePeriodMillis="0"` 禁用系统自带的 30min 最小更新(我们用 WorkManager 15min);`initialLayout` 给出添加瞬间的占位 Glance 渲染。

## Risks / Trade-offs

- **[Risk] widget 启动路由与 M1/M3 nav 不兼容** → 走 `TaskStackBuilder` + `extra route` 显式构造回退栈;AppNav.kt 在 `MainActivity` `onCreate(intent)` 解析 `intent.getStringExtra("route")` 并 navigate — 若已有此逻辑(查 `MainActivity.kt`)，复用;若没有，新增(M4-1 内)
- **[Risk] Glance API 与 Compose UI 差异** → Glance 自己的 Composable(`GlanceModifier` / `Column` / `Text` / `Image`)，不能 import `androidx.compose.foundation.layout.*`;M5 polish 阶段可考虑封装 `core/ui/GlanceComponents.kt` 抽出常用 widget
- **[Risk] widget 数据访问权限** → Glance widget 渲染在 widget host process，跨进程访问 Room 需要 provider 或 GlanceStateDefinition;本 change 用 `runInterruptible(IO)` + `provideContent { ... }` 异步拉数据，Glance `updateAll` 触发新 compose session
- **[Risk] WorkManager 兜底频率太高** → 15 min 一次，每次做 DB 读取 + Glance `updateAll`,IO 开销可接受;若笔记 100+ 可考虑 30min(M5 polish)
- **[Risk] 国产 ROM 杀进程** → 不在 M4-1 scope，留 M5 polish;README 注明"AOSP / Pixel Launcher 验证，小米 / 华为 / OPPO / vivo 待 M5 polish"
- **[Risk] widget 显示"最近一条"隐私** → roadmap §8.2 "不显示完整正文，只显示标题 + 时间";`title` 由用户自填，空时显示 "Untitled" / `content.take(30)` 派生(M1 既有 `TITLE_FALLBACK_LEN`)

## Migration Plan

无(纯新增 + 既有 schema 不变)。

回滚:`git revert` 即可，无数据库迁移，无新权限(RECEIVE_BOOT_COMPLETED 可选)。

## Open Questions

- **widget 显示 pinned 笔记还是最近更新时间?** 倾向:**最近更新时间**(pinned 已在列表页优先，widget 是快捷访问，不该再把 pinned 顶上来 — 否则 widget 里看到 pinned,App 里列表也是 pinned，体验重复)。M5 polish 阶段可加配置项
- **widget 是否需要 `androidx.glance:glance-material3` 依赖?** Glance 1.1.x 有 `glance-material3` 包提供 Material You 颜色 — 倾向:**暂不引入**,M4-1 用系统默认配色，M5 polish 时升级
- **widget 启动 vs Glance API 6.x(2026 年发布)?** Glance 当前 1.1.1 stable,6.x 是 alpha — 倾向:**保持 1.1.1**,M5 时评估 6.x
- **`NoteRepository.upsert` / `delete` 后调 `WidgetUpdater.updateAll` 是否会破坏 M1 既有测试?** → M1 测试都是 JVM，不涉及 widget;新加的调用 `WidgetUpdater` 是 Hilt 单例，在测试里 mock 掉即可