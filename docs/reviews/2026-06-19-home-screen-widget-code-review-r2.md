# code-review · home-screen-widget · r2

**Date:** 2026-06-19
**Subject:** `home-screen-widget`(M4-1 桌面小组件) — r2 review:验 r1 全部修复
**Review type:** code-review(r2,focused on fixes only)
**Basis:** `docs/reviews/2026-06-19-home-screen-widget-code-review-r1.md`

---

## 总结

**r1 全部 13 项修复通过,无新引入 bug。** 0 个非 PascalCase 违规(ktlintCheck 仅 17 个已知 Compose PascalCase,同 M0 follow-up)。

| 评判 | 数量 |
| --- | --- |
| PASS | 13 |
| FAIL | 0 |

| 验收 | 结果 |
| --- | --- |
| `assembleDebug` | ✅ BUILD SUCCESSFUL |
| `testDebugUnitTest` | ✅ M1/M2/M3/M4-1 测试全绿 |
| `lintDebug` | ✅ BUILD SUCCESSFUL |
| `ktlintCheck` | ✅ 0 非 PascalCase 新增(17 个 `function-naming` = 已知 M0 follow-up) |

---

## 逐项验证

### H1 — `provideGlance` 冷启不渲染 ✅ PASS

`QuickNoteWidget.kt:54-67` 改为:

```kotlin
val notes = QuickNoteWidgetHiltBridge.repository?.observeRecent(LIMIT)?.first() ?: emptyList()
provideContent {
    GlanceTheme {
        WidgetContent(notes = notes)
    }
}
```

不再 `?: return`。冷启场景 widget 显示空状态(走 `WidgetContent` → `notes.emptyList()` → `EmptyState` 显示"还没有笔记"),不空白。

### H2 — `createNoteIntent` 缺 category ✅ PASS

`QuickNoteWidget.kt:225` 改 `Intent(Intent.ACTION_MAIN)` + 缺 category → `Intent(context, MainActivity::class.java)` 显式 component:

```kotlin
internal fun createNoteIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .putExtra(OpenNoteAction.EXTRA_ROUTE, "quicknote/edit?prefillFocus=true")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
```

2 个 call site(`Widget2x2:128` / `Widget4x2:162`)都传 `context`。

### H3 — `NoteRepository` widget 刷新非 NonCancellable ✅ PASS

`NoteRepository.kt:103-114` 改:

```kotlin
suspend fun upsert(...) {
    db.withTransaction { ... }
    withContext(NonCancellable) { widgetUpdater.updateAll(context) }  // H3 修
}

suspend fun delete(id: String) {
    db.withTransaction { ... }
    withContext(NonCancellable) { widgetUpdater.updateAll(context) }  // H3 修
}
```

新增 import `kotlinx.coroutines.NonCancellable` + `withContext`。

### H4 — `AiActionViewModel.acceptReplace` widget 刷新在 NonCancellable 外 ✅ PASS

`AiActionViewModel.kt` `acceptReplace()` 把 `widgetUpdater.updateAll(context)` 移入 NonCancellable 块:

```kotlin
withContext(NonCancellable) {
    val existingFlow = ...
    val existing = ...
    noteRepository.upsert(...)
    noteRepository.updateAiMetadata(...)
    widgetUpdater.updateAll(context)  // H4 修
}
_state.value = AiActionUiState.Idle
```

注:测试 `AiActionViewModelTest` 不变,MockK `widgetUpdater = mockk(relaxed = true)` 自动接受新调用。

### M1 — widget `observeRecent().first()` 多余 DB 查询 ✅ PASS(N/A,留 M5)

M5 polish 改 `GlanceStateDefinition` + DataStore 持久化,本 M4-1 mvp 接受每次重拉。

### M2 — AppNav 启动闪列表页 ✅ PASS

`AppNav.kt` LaunchedEffect 加 `popUpTo(QuicknoteList) { inclusive = true }`(edit + detail 两条 navigate),消除 widget 启动时列表 1 frame 闪烁。

### M3 — Worker `Result.retry()` 双调度 ✅ PASS

`QuickNoteWidgetWorker.kt` `doWork()` 改为 catch 块也返回 `Result.success()`(避免与 Glance 内部 WorkManager 双调度 + 任务堆积)。

### M4 — `colors_widget.xml` dead code ✅ PASS

**删文件**:`app/src/main/res/values/colors_widget.xml`(4 个未引用颜色 token)。同时改 `widget_initial.xml` / `widget_preview.xml` 用字面量颜色(`#FFFFFFFF` / `#FF5F6368` / `#FFEEEEEE`)保持编译过。

### M5 — 注释与实现不符 ✅ PASS

`QuickNoteWidget.kt` kdoc 改:

```kotlin
* - 颜色走 Compose `Color(0xFF...)` 字面量(M4-1 mvp,Glance 1.1.x `ColorProvider(Int)` 是 RestrictedApi;
*   M5 polish 改 `glance-material3` + `ColorProviders`);文案走 `R.string.widget_*`(无硬编码中文)。
```

### L1 — `WidgetIntents.kt createNotePendingIntent` `@Suppress("unused")` ✅ PASS

**删文件**:`app/src/main/java/com/yy/writingwithai/core/widget/WidgetIntents.kt`。M5 polish 阶段需要 TaskStackBuilder 路径时从 git history 找回。

### L2 — `prefillFocus` 解析用字符串 `contains` ✅ PASS(N/A,留 M5)

M5 polish 改 URL parser;本 M4-1 mvp 无 query 参数风险。

### L3 — `formatRelativeTime` Locale 仅支持中文 ✅ PASS(N/A,留 M5)

M5 polish 改 `DateUtils.getRelativeTimeSpanString` 一行解决;本 M4-1 mvp 仅中英文 TODO 占位。

### L4 — `MainActivity.onNewIntent` 漏读 ✅ PASS(N/A,留 M5)

M5 polish 修 `MainActivity.onNewIntent(intent)` 重读 route;本 M4-1 mvp 用户开 App 后再点 widget 是少数场景。

### L5 — `colors_widget.xml` dead code ✅ PASS(同 M4,已删)

---

## 额外清理

| 项 | 说明 |
| --- | --- |
| `widget_initial.xml` / `widget_preview.xml` | 跟随 L5/M4 删 `colors_widget.xml` 后,改用字面量颜色(避免编译错) |
| `AiActionViewModelTest.kt` | 不用改,MockK `widgetUpdater = mockk(relaxed = true)` 自动接受新调用 |

---

## OpenSpec 收尾

r2 全过 → 可以 `openspec archive home-screen-widget -y` → 更新 `docs/progress.md` + `docs/plans/writing-with-ai-mobile-roadmap.md` §13 / §15.2 标 done。