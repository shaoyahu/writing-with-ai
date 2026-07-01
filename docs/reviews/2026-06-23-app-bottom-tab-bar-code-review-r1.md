# Code Review: app-bottom-tab-bar r1

**Reviewed**: 2026-06-23
**Subject**: `app-bottom-tab-bar`(底部 3 槽 tab 容器 + 中央 FAB + 我的 tab + 路由图重排)
**Reviewer**: Claude(self-review)
**Decision**: **REQUEST CHANGES** — 3 个 HIGH bug 待修

## Summary

新代码结构清晰，spec/设计/实现三层一致，build + ktlint + test 全绿。但发现 **3 个 HIGH bug**:`popUpTo(AppShell::class)` 在 inner NavController 中会崩;`MyScreen.onBack` 错连 rootNavController，实际等于退出 App;`SettingsDataScreen` snackbar 硬编码中文(预存问题，本 change 暴露)。MEDIUM/LOW 各 5-6 条，review 后建议修。

## Findings

### CRITICAL

无。

### HIGH

**H1. `AppShell.kt:75` `popUpTo(AppShell::class)` 在 inner NavController 中找不到目标，tab 切换会崩**
- 位置:`app/src/main/java/com/yy/writingwithai/app/AppShell.kt:75`
- 现状:`innerNavController.navigate(target) { popUpTo(AppShell::class) { saveState = true } ... }`
- 问题:`AppShell` 是**根 NavHost** 的 route，不在 inner NavController 的 graph(只含 `Notes` / `Me`)。`popUpTo(KClass)` 在 NavController 中走 `findDestination(route)`，目标不在当前 graph 时抛 `IllegalArgumentException`。
- 后果:用户首次点"我的"或来回切 tab 即崩。
- 修法:用 inner graph 的 startDestination 作锚点:
  ```kotlin
  popUpTo(innerNavController.graph.findStartDestination().id) { saveState = true }
  ```
  或 `popUpTo(Notes::class) { saveState = true }`(Notes 是 inner 的 startDestination，等价)。

**H2. `MyScreen.kt:44` onBack 错连 rootNavController，从"我的" tab 按返回 = 退出 App**
- 位置:`app/src/main/java/com/yy/writingwithai/feature/my/MyScreen.kt` + `app/AppShell.kt:112` `onBack = { rootNavController.popBackStack() }`
- 现状:`Me` 是 inner NavController 的 destination,`rootNavController` 此时栈顶是 `AppShell`,`popBackStack()` 弹掉 `AppShell` → NavController 无更多栈 → 走系统 back → **退出 App 或回到 launcher**
- 后果:用户在"我的" tab 按 ArrowBack 直接退出 App，违反 tab 模式直觉。
- 修法(两选一):
  - A. 推荐:**直接删 MyScreen 的 TopAppBar navigationIcon**(tab 根屏不需要 back)
  - B. `onBack = { /* no-op */ }`(inner 内也无更多栈，no-op 更合理)

**H3. `SettingsDataScreen.kt:72,76` snackbar 文案硬编码中文(预存，但本 change 触及该文件)**
- 位置:`app/src/main/java/com/yy/writingwithai/feature/settings/data/SettingsDataScreen.kt:72` `showSnackbar(message = "报告已保存到所选位置")` 与 `:76` `showSnackbar(message = "保存失败:${r.reason}")`
- 问题:违反 CLAUDE.md "字符串一律走 res/values/strings.xml，不在 Composable 里硬编码中文"
- 性质:预存 bug,M4-3 change 引入，本 change 改 export 按钮 label 时没顺带修。英文系统下用户看到中文 snackbar。
- 修法:复用已有 `R.string.settings_data_report_saved` / `R.string.settings_data_save_failed`(已存在，strings.xml:160-163),screen 内直接引用。

### MEDIUM

**M1. `MyEntry.kt` `ROUTE_ME` 死代码**
- 位置:`app/src/main/java/com/yy/writingwithai/feature/my/MyEntry.kt:12-14`
- 现状:声明 `const val ROUTE_ME = "me"`，但 `AppNav.kt` 走 typed `Me` data object，无任何地方 import `MyEntry.ROUTE_ME`
- 后果:dead code，误导后续 reader 以为 MyEntry 被使用
- 修法:删 `MyEntry.kt`，或 wire `AppNav.kt` 用 `MyEntry.ROUTE_ME` 替代裸字

**M2. `MyScreen` route 翻译用裸 String key,AppShell 改名 → MyScreen 静默坏**
- 位置:`app/src/main/java/com/yy/writingwithai/feature/my/MyScreen.kt:62,68,74,80,86` `onNavigate("SettingsData")` 等 + `AppShell.kt:103-110` `when (routeKey)`
- 问题:字符串契约，无编译期检查。`AppNav` 改名 `SettingsData` → `SettingsDataScreen` 时，MyScreen 静默走 `else -> Unit`，不报错不崩但行为消失
- 修法:把 `onNavigate` 改成类型化 lambda:`onNavigate: (Target) -> Unit`,`Target` 是 sealed class(`Data` / `ModelManagement` / `PromptTemplate` / ...)

**M3. `MyScreen` TopAppBar 含 ArrowBack 按钮，但语义上是 tab 根屏(应无 back)**
- 位置:`app/src/main/java/com/yy/writingwithai/feature/my/MyScreen.kt:51-54`
- 现状:渲染 `navigationIcon = ArrowBack` + 提供 `onBack` 回调
- 问题:tab 根屏不需要 back，跟 `QuickNoteListScreen`(同样 tab 根，无 ArrowBack)不一致
- 修法:同 H2 修法 A — 直接删 `navigationIcon` 与 `onBack` 形参

**M4. `AppShell.kt` 中央 FAB `Modifier.offset(y = -16.dp)` + `Modifier.shadow(8.dp)` 与 FAB 自带 `elevation(8.dp)` 重复**
- 位置:`app/src/main/java/com/yy/writingwithai/app/AppShell.kt:166-169`
- 现状:`floatingActionButton.elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)` + `Modifier.shadow(8.dp)`
- 问题:M3 FAB 默认 elevation 已渲染阴影，加 `Modifier.shadow` 双重投影，可能颜色叠加或边缘锯齿
- 修法:删 `Modifier.shadow(8.dp)`，只留 FAB 自带 elevation

**M5. `AppShell.kt:138,142,179` tab 选中态用 `currentRoute?.contains("Notes")` substring 匹配**
- 位置:`app/src/main/java/com/yy/writingwithai/app/AppShell.kt:138,142,179`
- 现状:`currentRoute?.contains("Notes") == true` / `currentRoute?.contains("Me") == true`
- 问题:Compose Nav 2.8 用 FQN 作 route 字符串(如 `com.yy.writingwithai.app.Notes`),substring 匹配目前 OK，但脆弱。将来加 `Notes2` 或 `NotebookList` 等含 "Notes" 子串的 route 会误判选中。
- 修法:用 `currentEntry?.destination?.hasRoute<Notes>() == true`(typed `NavDestination.hasRoute<T>()` API,Nav 2.8+ 提供)

### LOW

**L1. `QuickNoteListScreen.kt:197` `contentPadding = PaddingValues(bottom = 80.dp)` 残留**
- 现状:列表 `LazyColumn` 留 80dp 底部内边距，原意是给 FAB 让出空间。FAB 已被删除。
- 修法:改 `PaddingValues(bottom = 0.dp)` 或删除该参数走默认

**L2. `AppShell.kt` `onSelectTab = { target -> innerNavController.navigate(target) {...} }` 入参 `target: Any`**
- 现状:`onSelectTab: (Any) -> Unit`,`navigate(target: Any)` 是 NavController 公开 API 但缺类型安全
- 修法:把 `target` 类型改为 `KClass<*>` 或用 sealed `Tab` enum

**L3. `AppShell.kt` `AppShell` Composable 名与 `data object AppShell` route 同名**
- 风险:reader 容易混淆，`AppShell::class` 在 inner NavController 上下文中不解析为 Composable 但看起来像
- 修法:加注释区分即可

**L4. `strings.xml` `settings_data_export` 字符串成孤儿**
- 位置:`app/src/main/res/values/strings.xml:152` `<string name="settings_data_export">导出全部数据</string>`
- 现状:`SettingsDataScreen.kt:117` 已切到 `me_data_export_all`,`settings_data_export` 无 caller
- 修法:删该 key(同步 `values-en`)。或保留无害

**L5. `AppShell.kt:151` NavigationBarItem 双重 a11y label**
- 现状:`NavigationBarItem` 同时渲染 `icon`(icon.contentDescription 也走 `tab_notes`)和 `label`(Text 也渲染 `tab_notes`)。屏幕阅读器可能播报两次。
- 修法:`icon` 内部 `Icon` 设 `contentDescription = null`，仅 `label` 提供 a11y 文案

**L6. `AppNav.kt:42,53` 头部 KDoc 仍提"启动默认目的地 = QuicknoteList"**
- 现状:KDoc 注释没更新，仍写 `navigate(QuicknoteList)` / "QuicknoteList(主页)" 等过期表述
- 修法:更新 KDoc:startDestination 改为 `AppShell`;`QuicknoteList` 标 deprecated(若仍保留 data object)

## Validation Results

| Check | Result |
| --- | --- |
| `./gradlew :app:assembleDebug` | ✅ Pass |
| `./gradlew :app:ktlintCheck` | ✅ Pass(经 ktlintFormat 自动修复) |
| `./gradlew :app:testDebugUnitTest` | ✅ Pass |
| `openspec validate app-bottom-tab-bar --strict` | ✅ Pass |

## Files Reviewed

| File | Change | Notes |
| --- | --- | --- |
| `app/src/main/java/com/yy/writingwithai/app/AppShell.kt` | NEW(205 行) | H1/M4/M5/L2/L3/L5 |
| `app/src/main/java/com/yy/writingwithai/app/AppNav.kt` | MODIFIED | H2 关联 + L6 KDoc 过时 |
| `app/src/main/java/com/yy/writingwithai/feature/quicknote/list/QuickNoteListScreen.kt` | MODIFIED | L1 残留 padding |
| `app/src/main/java/com/yy/writingwithai/feature/my/MyScreen.kt` | NEW(123 行) | H2/M2/M3 |
| `app/src/main/java/com/yy/writingwithai/feature/my/MyEntry.kt` | NEW | M1 dead code |
| `app/src/main/java/com/yy/writingwithai/feature/settings/data/SettingsDataScreen.kt` | MODIFIED | H3 snackbar 硬编码(预存) |
| `res/values/strings.xml` | MODIFIED | L4 孤儿字符串 |
| `res/values-en/strings.xml` | MODIFIED | L4 同步 |
| `openspec/changes/app-bottom-tab-bar/{proposal,design,tasks}.md` + `specs/**` | NEW | validate 通过 |

## Recommended Next Steps

按优先级:

1. **必修(HIGH)**:
   - 修 H1 `popUpTo(AppShell::class)` → `popUpTo(Notes::class)` 或 `popUpTo(innerNavController.graph.findStartDestination().id)`，否则 tab 切换即崩
   - 修 H2 + M3 联合处理:删 `MyScreen` TopAppBar navigationIcon + `onBack` 形参 + `AppShell.kt:112` 删 `onBack` 实参
   - 修 H3 snackbar 硬编码(直接复用已有 `R.string.settings_data_report_saved` / `settings_data_save_failed`)

2. **建议修(MEDIUM)**:
   - M1 删 `MyEntry.kt` 或 wire 它
   - M2 把 MyScreen `onNavigate` 改成 typed lambda
   - M4 删 FAB `Modifier.shadow`(FAB 自带 elevation)
   - M5 用 `hasRoute<T>()` 替 substring 匹配

3. **可选(LOW)**:L1 删残留 padding / L4 删孤儿字符串 / L6 更新 KDoc / L5 双重 a11y

修复 H1/H2/H3 后可以 APPROVE。

— Claude(self-review),2026-06-23