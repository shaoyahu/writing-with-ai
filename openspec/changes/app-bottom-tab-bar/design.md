## Context

应用当前只有 `QuickNoteListScreen` 一个主入口(`app/AppNav.kt:161` `startDestination = QuicknoteList`),所有非笔记功能(设置 / 数据导入导出 / Prompt 模板 / 实体别名 / 飞书同步)都挤在 `QuickNoteListScreen` 右上角 `MoreVert` DropdownMenu 中(`feature/quicknote/list/QuickNoteListScreen.kt:107-145`)。`feature/aiwriting/AiwritingEntry.kt` 已存在但**未挂进 NavHost**。

M4-3 把"数据导入导出"加进 overflow menu;M4-2 加了 `enableOnBackInvokedCallback="true"`;M4-1 widget 启动走 `TaskStackBuilder` + popUpTo(QuicknoteList) 锚点。本 change 在此基础上做导航层重构,不动数据层 / 不动 widget 协议(只改 popUpTo 锚点)。

约束(`CLAUDE.md`):
- 单 `MainActivity` + NavHost,无底部 tab 抽象
- feature 必须自包含,跨 feature 走 `<Feature>Entry.kt`
- 业务 lint 加在 `app/build.gradle.kts` 的 `ktlint {}` 块
- 包名 `com.yy.writingwithai`
- 复用 M3 `NavigationBar` + `FloatingActionButton`,不引入新依赖

## Goals / Non-Goals

**Goals**
- 引入底部 3 槽 tab 容器(`AppShell`),3 槽 = 笔记 / 中央 + / 我的
- 中央 + 全局快捷新建笔记,从任意 tab 可达
- 把 `QuickNoteListScreen` 的 overflow menu 与 FAB 全部下移到 tab 层
- 把所有设置 / 数据 / AI 配置 / 实体别名 / 飞书同步入口聚合到"我的" tab 的 `MyScreen`
- "导出全部为 ZIP"操作从 list 迁入 `SettingsDataScreen`
- widget pending route 回放的 popUpTo 锚点由 `QuicknoteList` 切到 `AppShell`

**Non-Goals**
- 不动数据层(`Note` schema / `NoteDao` / `NoteRepository` 字段保持 v2)
- 不动 widget 协议(`core/widget/*` 现有 PendingIntent 构造不变,只改 AppNav 的 popUpTo 锚点)
- 不引入底部 tab 之外的导航方式(无 Drawer / 无 SideRail)
- 不重写 `QuickNoteListScreen` 搜索 / 标签筛选 / 排序逻辑(只删 overflow + FAB + 瘦身入参)
- 不实现"AI"独立 tab(用户在 proposal 选 3 槽,AI 操作留在详情屏的 `AiActionFab`)
- 不实现深链(`App Links`);widget Intent 链路不变

## Decisions

### 1. 单 NavHost + 子图 vs 双 NavHost

选**单 NavHost**(整个 App 一个 `NavController`,tab 切换是 route 切换)。理由:
- Compose `NavigationBar` + Material3 推荐模式;选双 NavHost(每个 tab 一个独立 NavController)会让"跨 tab 跳转"困难(详情/编辑器跨 tab 可达)
- widget pending route 回放逻辑(`LaunchedEffect(initialRoute)` + popUpTo)天然适合单 NavHost,改动量小
- 已有 M4-1 / M4-4 逻辑都跑在单 NavHost 上,不重写

选 route 表达方式:`@Serializable data object AppShell` / `Notes` / `Me`,切换 tab 走 `navigate(Me) { popUpTo(AppShell) { saveState = true }; launchSingleTop = true; restoreState = true }`(标准 Material3 pattern)。

`QuicknoteList` 这个 data object **保留**作为 `Notes` 的实现 alias(archive 同步时 `quick-note` spec 已有 `quicknote/list` 路由的引用不会断);`Notes` route 用 `composable<Notes>` block 渲染 `QuickNoteListScreen`,内部仍走原有 `navController.navigate(QuicknoteDetail(id))` 链路。

### 2. 中央 FAB 用 `Box` 占位 vs `NavigationBarItem` 空内容

选**`Box` 占位 + 圆形 `FloatingActionButton` offset 抬出**(`Modifier.offset(y = -16.dp) + Modifier.shadow(8.dp)`)。理由:
- M3 `NavigationBarItem` 没有"凹陷圆槽"槽位类型,若用 `NavigationBarItem` + 空内容,FAB 与 bar 同高,视觉不"凸起"
- 抬出式 FAB 是 WeChat / iOS / 主流国产 App 的成熟模式;`Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))` 处理手势条遮挡
- 中央槽位保留 `Box` 是为了与两侧槽位等宽对齐(否则 FAB 居中而图标错位)

### 3. `QuickNoteListScreen` 入参瘦身 vs 保留回调

选**入参瘦身**——直接删 `onSettingsClick` / `onPromptSettingsClick` 两个形参。理由:
- 入参瘦身与 `app-tab-bar` spec 中"笔记 tab 不再有 overflow" Scenario 一致
- `AppNav.kt` 的 `composable<Notes>` block 也不再传这两个 lambda
- 后续若需要在 list 加新回调,统一走 `onXxxClick` 模式(当前 M1 入参命名风格)

调用方影响:`AppNav.kt:163-169` `QuickNoteListScreen(...)` 调用 block 同步更新。

### 4. "我的" tab 实现路径

选**新建 `feature/my/MyScreen.kt` + `feature/my/MyEntry.kt`**(独立 feature 包)。理由:
- `MyScreen` 不是设置子屏(它聚合多个 feature 的入口),不符合 `feature/settings/SettingsScreen.kt` 的单一职责
- 独立 `feature/my/` 符合 CLAUDE.md "feature 必须自包含"原则
- `MyEntry` object 暴露 `ROUTE_ME` 常量供 `AppNav.kt` 引用,跨 feature 走 Entry 模式(与 `OnboardingEntry` / `ModelManagementEntry` / `AiwritingEntry` 一致)

入口列表(6 条 `ListItem`):
- 数据导入/导出 → `SettingsData`
- AI 模型管理 → `SettingsModelManagement`
- Prompt 模板 → `SettingsPromptTemplate`
- 实体别名 → `SettingsAliasManagement`
- 飞书同步 → `Settings`(已有 `FeishuSyncLogSection`)
- 关于 → 纯展示,不 navigate(读 `BuildConfig.VERSION_NAME`)

### 5. "导出全部 ZIP"按钮归属

选**迁入 `SettingsDataScreen`**,不放 `MyScreen`。理由:
- `MyScreen` 已经有"数据导入导出"入口 → 推 `SettingsData` → 屏内含导出按钮,语义连贯
- 与"数据导入/导出"是同一类操作,放一起减少导航层数
- 保留原 `exportAllLauncher`(`ActivityResultContracts.CreateDocument("application/zip")`)+ `notes_export.zip` 文件名 + zip 内容写入流程(M4-3 实现,本 change 不动 IO 逻辑,只搬位置)

### 6. widget 启动 pending route 的 popUpTo 锚点

选**`popUpTo(AppShell)` 替代原 `popUpTo(QuicknoteList)`**。理由:
- widget PendingIntent 启动 MainActivity → navigate `QuicknoteEdit` / `QuicknoteDetail` 时,要清掉中间栈剩目标页 + AppShell
- 锚点跟着 startDestination 走,startDestination 变 `AppShell`,锚点也变 `AppShell`
- 这是 widget 协议唯一的改动(`MainActivity.onCreate` 的 widget extra 解析逻辑不变)

影响位置:`AppNav.kt` 三处(原 consent 后回放 + 直接 navigate 路径 + apikey-prompt 后回放)。

### 7. 不引入新依赖 + 不重写 Hilt 入口

`AppShell` 不需要 ViewModel(纯 UI 状态机),不引入 `@HiltViewModel`;`MyScreen` 也不需要 ViewModel(纯列表渲染,数据来自静态资源)。Hilt 改造零成本。

`MainActivity` 不变;`WritingApp` 不变;`AndroidManifest.xml` 不变(M4-2 predictive back flags 已就位)。

### 8. Preview 与主题 token

不引入新主题 token,复用现有 `LocalSpacing.current` + `MaterialTheme.colorScheme.*` + `app/ui/theme/Shape.kt`(无新增 Shape,中心 FAB 用 `CircleShape` M3 内置)。

`AppShellPreview` / `MyScreenPreview` 放各自文件底部(CLAUDE.md "Composable 函数必须大写开头"约定)。

## Risks / Trade-offs

[Risk] **中央 FAB 在手势导航设备被裁** → Mitigation:`Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))` + `Modifier.offset(y = -16.dp)` 抬出 bar 上沿,在异形屏(挖孔 / 灵动岛)走 `WindowInsets.safeDrawing` 兜底。`app-tab-bar` spec "Bottom-bar insets handled for gesture-nav devices" Requirement 覆盖。

[Risk] **tab 切换栈深度与 back 行为变化** → Mitigation:tab 切换走 `popUpTo(AppShell) { saveState = true } + launchSingleTop = true + restoreState = true`,Material3 标准 pattern,详情/编辑器跨 tab 可达;back 在 tab 根屏退出 App,详情/编辑器 back 回原 tab。已在 spec "Tab switching uses NavController state" Scenario 验证。

[Risk] **`QuickNoteListScreen.exportAllLauncher` 删除后回退路径缺失** → Mitigation:本 change 把 launcher 与按钮一起迁到 `SettingsDataScreen`,功能 100% 保留;若 archive 同步时 `quick-note` spec 的旧 Scenario 没删干净,`openspec validate` 会失败,需手动清理。

[Risk] **widget 启动 pending route popUpTo 锚点改坏** → Mitigation:仅改 `AppNav.kt` 三处 `popUpTo(QuicknoteList)` → `popUpTo(AppShell)`,其余 widget 协议(`core/widget/*`)不动;`openspec validate app-bottom-tab-bar` + `./gradlew :app:assembleDebug` + 真机 widget 启动冒烟覆盖。

[Risk] **`AiwritingEntry` 已存在但未挂进 NavHost,易被误以为新入口** → Mitigation:`app-tab-bar` spec 与 `tasks.md` 明确"本 change 不引入 AI tab",`AiwritingEntry` 仅在 `QuickNoteDetailScreen` / `QuickNoteEditorScreen` 通过 `AiwritingEntry.rememberAiActionViewModel(...)` 入口调用,导航层不挂。

[Risk] **ktlint rule-engine 1.0.x 与 Compose PascalCase 硬冲突(已知)** → Mitigation:`AppShell.kt` / `MyScreen.kt` Composable 用 PascalCase 命名;若 ktlintCheck 报 `FunctionNaming`,与 M4 既有代码一致加 `@file:Suppress("FunctionNaming")`(CLAUDE.md "约定" + memory `ktlint-compose-pascalcase-1.0` 已记录此为已知遗留)。

[Risk] **`AppShell` 与现有 `App` Composable 命名冲突** → Mitigation:`App.kt` 是顶层 `App()` Composable,`AppShell` 是子 Composable,层级清晰不冲突。

## Migration Plan

**部署步骤**(本 change 一次性提交,无中途版本):
1. M1 — `feature/my/MyEntry.kt` + `MyScreen.kt` 新建(空壳 6 条 ListItem 占位文案)
2. M2 — `app/AppShell.kt` 新建(`Scaffold` + `NavigationBar` + 中央 FAB + 子 NavHost 占位)
3. M3 — `app/AppNav.kt` startDestination 切到 `AppShell`;新增 `AppShell` / `Notes` / `Me` 三个 `@Serializable` route + `composable` block;`composable<Notes>` 渲染 `QuickNoteListScreen`(入参瘦身版);`composable<Me>` 渲染 `MyScreen`
4. M4 — `QuickNoteListScreen.kt` 删 overflow + 删 FAB + 删 `exportAllLauncher` + 删 `onSettingsClick` / `onPromptSettingsClick` 形参
5. M5 — `SettingsDataScreen.kt` 新增"导出全部为 ZIP"按钮(把 `exportAllLauncher` 整段搬过来)
6. M6 — strings.xml / values-en/strings.xml 增 11 个 key + 删 `quicknote_list_menu_cd`
7. M7 — `./gradlew :app:assembleDebug :app:ktlintCheck :app:testDebugUnitTest` 全绿 + 真机冒烟(启动 → 笔记 → 中央 + → 编辑 → 返回笔记 / 切我的 → 数据 → 导出 → 切笔记 → 无 overflow)

**回退策略**:本 change 提交后如需回退,`git revert <commit>` 即可——因为:
- 新文件 `app/AppShell.kt` / `feature/my/MyScreen.kt` / `feature/my/MyEntry.kt` 删除即可,无下游依赖
- `QuickNoteListScreen.kt` 改动可整体还原(原 overflow + FAB + exportAllLauncher 完整保留在 git history)
- `SettingsDataScreen.kt` 增的按钮独立,删除无副作用
- `AppNav.kt` 改 startDestination + 加 3 个 route,revert 后 startDestination 回 `QuicknoteList`,3 个 route 删除

## Open Questions

- **Q1:`AiwritingEntry` 是否需要在 MyScreen 暴露入口?** 提案阶段用户定 3 槽(笔记 / + / 我的),AI 不做独立 tab,但 MyScreen 现有 6 条 ListItem 已含"AI 模型管理"(`SettingsModelManagement`)。若后续 AI 操作需要单独入口(扩写/润色直接入口,不走详情选区),可加第 7 条"AI 助手"指向 `AiwritingEntry` 的新 Composable。本 change 不实现,留 TODO。
- **Q2:`MyScreen` 关于(版本号)是否需要 changelog / 开源链接?** 当前仅 `BuildConfig.VERSION_NAME` 字符串展示;若 v1.1+ 加 changelog,需要新 change 处理。本 change 不实现。
- **Q3:tab bar 是否需要支持滑动 / 长按 tooltip?** 当前仅 click;M3 `NavigationBarItem` 支持 `alwaysShowLabel = true`,本 change 不动;若未来需要 tooltip / 长按快捷菜单,新开 change。
- **Q4:中央 FAB 是否需要在 AI 写作流中变成"生成中"态(spinner + 禁用)?** 当前 FAB 永远可点;若 AI 流是模态(`ModalBottomSheet`)而非全屏编辑器,FAB 全程可点不冲突。本 change 维持永远可点,后续若改 FAB 语义再开 change。