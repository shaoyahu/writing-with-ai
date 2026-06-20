# code-review · home-screen-widget · r1

**Date:** 2026-06-19
**Subject:** `home-screen-widget`(M4-1 桌面小组件) — r1 review:全文件 AI 自审
**Review type:** code-review(r1,initial)
**Basis:** `openspec/changes/home-screen-widget/`(4 artifacts)+ 15 个产物文件

---

## 总结

**M4-1 Glance widget 主体(Glance 1.1.x API / PendingIntent / Worker)实现路径正确,主路径刷新已落库。但有 4 个 HIGH 阻断 UI 闭环跑通(冷启空 widget / Intent category 漏 / acceptReplace widget 刷新在 NonCancellable 外 / upsert 后 widget 刷新非 NonCancellable)。**

| 严重度 | 数量 |
| --- | --- |
| HIGH | 4 |
| MEDIUM | 5 |
| LOW | 5 |

| 验收 | 结果 |
| --- | --- |
| `assembleDebug` | ✅ BUILD SUCCESSFUL |
| `testDebugUnitTest` | ✅ 既有 M1/M2/M3 测试全绿 |
| `lintDebug` | ✅ BUILD SUCCESSFUL(0 errors) |
| `ktlintCheck` | ⚠️ 17 个 `function-naming` = 已知 Compose PascalCase(M0 follow-up),0 非 PascalCase 新增 |

---

## HIGH — 必须修

### H1 · 冷启 widget 不渲染(`?: return`) ⚠️ 阻断 UI

**文件:** `app/src/main/java/com/yy/writingwithai/core/widget/QuickNoteWidget.kt:58-60`

```kotlin
val repository =
    QuickNoteWidgetHiltBridge.repository
        ?: return // 未初始化(冷启 + widget 早于 App 启动),只渲染空状态
```

**问题:** Glance 1.1.x widget host process 启动顺序:用户长按桌面 → 添加 widget → widget host 进程拉起 widget receiver → 调 `provideGlance`。**整个过程不需要 App 主进程启动**。如果用户刚装完 App / 杀进程后第一次添加 widget,`WritingApp.onCreate` 没跑过,`QuickNoteWidgetHiltBridge.repository == null`,**`provideGlance` 直接 return,widget UI 不渲染**(空白 2x2 块)。

**后果:** 全新安装 / 杀进程场景,用户看到空白 widget,不知所谓。要么 App 启动一次后 widget 才正常。

**修法:** `provideGlance` 内若 `repository == null`,**仍调 `provideContent` 渲染空状态**(显示"还没有笔记"提示),不是 return:把 `notes` fallback 成 `emptyList()`。

```kotlin
val notes = repository?.observeRecent(LIMIT)?.first() ?: emptyList()
// 仍调 provideContent { GlanceTheme { WidgetContent(notes = notes) } }
```

### H2 · widget `Intent.ACTION_MAIN` 缺 category `LAUNCHER`,某些 launcher 启动失败

**文件:** `app/src/main/java/com/yy/writingwithai/core/widget/QuickNoteWidget.kt`(`createNoteIntent()`)

```kotlin
internal fun createNoteIntent(): Intent =
    Intent(Intent.ACTION_MAIN)
        .setClassName("com.yy.writingwithai", MainActivity::class.java.name)
        .putExtra(OpenNoteAction.EXTRA_ROUTE, "quicknote/edit?prefillFocus=true")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
```

**问题:** `MainActivity` `<intent-filter>` 只声明 `<action MAIN>` + `<category LAUNCHER>`。`Intent(Intent.ACTION_MAIN)` **不设置 category**,Android 启动匹配需要 `MAIN` action + `LAUNCHER` category,缺一不可。

**后果:** 某些 launcher(Pixel Launcher 1.x 严格匹配,AOSP 老版本)启动 widget "+" 时 `ActivityNotFoundException` 抛。

**修法:** 用 `Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)`,或更稳用 `Intent(context, MainActivity::class.java)` 显式 component — **后者是 spec §"PendingIntent.getActivity 启动 MainActivity" 的推荐写法**。

```kotlin
internal fun createNoteIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .putExtra(OpenNoteAction.EXTRA_ROUTE, "quicknote/edit?prefillFocus=true")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
```
**注意:** `createNoteIntent` 是 `@Composable` 上下文调,需要 `LocalContext.current` 作参数。

### H3 · `NoteRepository.upsert/delete` 主路径 widget 刷新非 NonCancellable

**文件:** `app/src/main/java/com/yy/writingwithai/core/data/repo/NoteRepository.kt:103-114`

```kotlin
suspend fun upsert(...) {
    db.withTransaction { ... }
    widgetUpdater.updateAll(context)  // 在事务外,但仍可能因 scope 取消而中断
}
```

**问题:** `upsert` 是 `suspend` 函数,被 viewModelScope 调用。若用户在 upsert 完成 + widget 刷新之间 back 退出,viewModelScope 取消,`widgetUpdater.updateAll(context)` 被中断。**数据库已写入但 widget 没刷新**,用户回桌面看到旧数据。

**后果:** 极端 race 条件下 widget 显示陈旧。

**修法:** 把 `widgetUpdater.updateAll(context)` 包进 `NonCancellable`(沿用 M1 r1 M6 修):
```kotlin
suspend fun upsert(...) {
    db.withTransaction { ... }
    withContext(NonCancellable) { widgetUpdater.updateAll(context) }
}
```
**注意:** `QuickNoteDetailViewModel.delete` 调 `repository.delete(id)` 后已自动走 `NonCancellable` 路径(M1 r1 修),但 Repository 内部仍需自防御。

### H4 · `AiActionViewModel.acceptReplace` widget 刷新在 NonCancellable 外

**文件:** `app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/AiActionViewModel.kt`

```kotlin
fun acceptReplace() {
    viewModelScope.launch {
        withContext(NonCancellable) {
            val existingFlow = ...
            val existing = ...
            noteRepository.upsert(...)
            noteRepository.updateAiMetadata(...)
        }
        // M4-1:AI 接受替换后 widget 立即刷新。
        widgetUpdater.updateAll(context)  // ← NonCancellable 外!
        _state.value = AiActionUiState.Idle
    }
}
```

**问题:** 同 H3,widget 刷新在 NonCancellable 块外。若 viewModelScope 在 `withContext(NonCancellable){...}` 完成 → 退出 NonCancellable → 用户 back → scope 取消,`widgetUpdater.updateAll` 中断。**Note 已落库 + lastAiOp 已写,但 widget 看不到更新**。

**修法:** 把 `widgetUpdater.updateAll(context)` 也包进 NonCancellable(或者移到 NonCancellable 块尾部):
```kotlin
withContext(NonCancellable) {
    ...
    noteRepository.upsert(...)
    noteRepository.updateAiMetadata(...)
    widgetUpdater.updateAll(context)
}
_state.value = AiActionUiState.Idle  // 状态切回可放外面(UI 已关,影响小)
```

---

## MEDIUM — 应该修

### M1 · widget `provideGlance` 每次 `observeRecent().first()` 多余一次 DB 查询

**文件:** `QuickNoteWidget.kt:61`

`first()` 等同 `observeLatest + take(1)`。每次 `updateAll` 都重拉一次 Room,**浪费**。widget 数据可走 `GlanceStateDefinition` 持久化,mvp 阶段可接受,M5 polish 修。

**修法:** 短期不改;留 M5 polish。

---

### M2 · `AppNav` 启动时 LaunchedEffect navigate 会闪列表页

**文件:** `app/src/main/java/com/yy/writingwithai/app/AppNav.kt`

```kotlin
NavHost(
    navController = navController,
    startDestination = QuicknoteList,
) {
    // composables
}
LaunchedEffect(initialRoute) {
    if (initialRoute == null) return@LaunchedEffect
    when {
        initialRoute.startsWith("quicknote/edit") -> navController.navigate(...)
        initialRoute.startsWith("quicknote/detail/") -> navController.navigate(...)
    }
}
```

**问题:** NavHost startDestination = `QuicknoteList`,Compose 第一次组合渲染 QuicknoteList。`LaunchedEffect` 在第一次组合后跑,然后 navigate 到 `QuicknoteEdit` / `QuicknoteDetail`。**用户看到列表闪一下再跳目标**。

**后果:** widget 启动 UX 体验差(看到列表 1 frame 再闪到目标)。

**修法:** 用 `popUpTo(QuicknoteList) { inclusive = true }` 立刻把列表 pop 掉,或 NavHost `startDestination` 动态设:
```kotlin
LaunchedEffect(initialRoute) {
    if (initialRoute == null) return@LaunchedEffect
    when {
        initialRoute.startsWith("quicknote/edit") -> {
            navController.navigate(QuicknoteEdit(...)) {
                popUpTo(QuicknoteList) { inclusive = true }
            }
        }
        ...
    }
}
```

### M3 · Worker `Result.retry()` 与 Glance 内部 WorkManager 双调度

**文件:** `QuickNoteWidgetWorker.kt:23-26`

Glance `updateAll(context)` 内部自带 WorkManager 异步任务调度,可能创建子 worker。`Result.retry()` 让 WorkManager 再次调度本 worker,可能与 Glance 内部任务双跑。

**修法:** 改 `Result.success()`,因为 widget UI 已经渲染成功,Glance 内部任务已 enqueue;retry 没意义。
```kotlin
override suspend fun doWork(): Result =
    try {
        QuickNoteWidget().updateAll(applicationContext)
        Result.success()  // 不 retry,Glance 内部已调度
    } catch (e: Exception) {
        Result.success()  // 兜底周期失败也别 retry,避免堆积
    }
```

### M4 · `colors_widget.xml` dead code

**文件:** `app/src/main/res/values/colors_widget.xml`

写了 4 个颜色 token(`widget_bg / widget_title / widget_subtitle / widget_accent`),但 widget 代码因 Glance `ColorProvider(int)` RestrictedApi 用 `Color(0xFF...)` 字面量,**colors_widget.xml 无任何引用**。

**修法:** 选其一:
- (A) 把 widget 改成 `ColorProvider(R.color.widget_title, R.color.widget_title)` — 但 lint 会再报 RestrictedApi,需要 `@OptIn` 注解
- (B) 删 `colors_widget.xml` 文件,等 M5 polish 改 `glance-material3` 时重新做颜色 token

**建议 (B)** — M5 polish 阶段再决定。

### M5 · 注释与实现不符(`R.color.widget_*` vs `Color(0xFF...)`)

**文件:** `QuickNoteWidget.kt` kdoc

```kotlin
* - 颜色 / 文案走 `R.color.widget_*` / `R.string.widget_*`,**不** import `androidx.compose.foundation.layout.*`。
```

实际是字面量 `Color(0xFF202124)`(因 H1/M4 修法变化),不是 `R.color.widget_*`。**注释误导后续 reviewer**。

**修法:** kdoc 改:"颜色走 Compose `Color(0xFF...)` 字面量(M4-1 mvp;M5 polish 改 `glance-material3` + ColorProviders)"。

---

## LOW — 可选

### L1 · `WidgetIntents.kt createNotePendingIntent` `@Suppress("unused")`

没人调(M4-1 mvp 走 `createNoteIntent`)。M3 proposal 提到 M5 polish 切回 — 但现状纯 dead code,直接删更干净。`M5 polish` task 在 `tasks.md` §12 已记录。

**修法:** 删 `WidgetIntents.kt`(M5 polish 阶段需要时再从 git history 找回)。

### L2 · `prefillFocus` 解析用字符串 `contains`

```kotlin
val prefill = initialRoute.contains("prefillFocus=true")
```

**风险:** 如果 future 加 query 参数 `?prefillFocus=true&foo=bar` 仍 OK(`contains` true);但 `?prefillFocus=false&...` 误判为 true(其实 false 也要走到 `prefillFocus=false`)。**当前 mvp 没问题,M5 polish 改 URL parser**。

### L3 · `formatRelativeTime` 自定义,Locale 仅支持中文

`formatRelativeTime(context, epochMs)` 用 `R.string.widget_time_*` 中文文案,英文环境显示"刚刚/分钟前/..."。**M5 polish 改 `DateUtils.getRelativeTimeSpanString` 一行解决**(locale 自动,英文显示 "1 minute ago" 等)。

### L4 · `MainActivity.getIntent()` vs `intent` 问题

`Intent.FLAG_ACTIVITY_NEW_TASK` 启动 widget intent,`MainActivity.onCreate` 的 `intent` 字段**可能保留原始 LAUNCHER intent**(已被 `getIntent()` 缓存)。代码用 `intent?.getStringExtra(EXTRA_ROUTE)` 是 OK 的(因为这是 `onCreate` 第一次读),但若用户从 widget → App 已开 → 再回到 widget,**第二次 onCreate 不会触发**,`getIntent()` 仍读原始 widget intent,**不会刷新**。

**风险:** 实际场景 widget 已开 App 后用户再点 widget "+",`MainActivity` 走 `onNewIntent(intent)`(不 `onCreate`)。当前实现不重读 route。**M5 polish 修**。

### L5 · `colors_widget.xml` dead code(同 M4)

---

## 推荐修复优先级

| 顺序 | 项 | 阻断 |
| --- | --- | --- |
| 1 | **H1** | 🔴 冷启 widget 空白 |
| 2 | **H2** | 🔴 widget "+" 在某些 launcher 启动失败 |
| 3 | **H3** | 🟠 数据已落库但 widget 不刷新(race) |
| 4 | **H4** | 🟠 同上,AI acceptReplace 路径 |
| 5 | **M1-M5** | 🟢 顺手修 |
| 6 | **L1-L5** | 🟢 review r2 一起过 |

---

## OpenSpec 收尾

H1-H4 + M2 + M3 + M5 修完后跑 §13 4 项验收,再写 r2 review 验,最后 `openspec archive home-screen-widget -y`。