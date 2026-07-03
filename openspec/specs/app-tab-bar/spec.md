# app-tab-bar Specification

## Purpose

应用底部 3 槽 tab 栏容器 + 中央凸起圆形 FAB 全局新建笔记。降低"设置 / 数据 / AI 配置 / 实体别名"等高频入口的发现性摩擦，让"新建笔记"在任何 tab 都可达。

Synced from OpenSpec change `app-bottom-tab-bar`(2026-06-26)。

## Requirements

### Requirement: Bottom tab bar styled to match the My tab's card aesthetic

应用 MUST 在主屏(`AppShell`)底部渲染底部 tab 栏，**视觉风格对齐【我的】tab 的 SectionCard 圆角 + primary tint icon + surface 容器**。共 3 槽，槽位从左到右依次为:

| 槽位 | 类型 | 选中态样式 | 未选中态样式 | 文案资源 |
| --- | --- | --- | --- | --- |
| 1(笔记) | `Surface` 子卡，16dp 圆角，`weight = 1f` | `containerColor = colorScheme.primary` + `contentColor = colorScheme.onPrimary` + `Icons.Filled.Notes` | `containerColor = Color.Transparent` + `contentColor = colorScheme.onSurfaceVariant` + `Icons.Outlined.Notes` | `R.string.tab_notes` |
| 2(中央创建) | `CenterCreateCard`(`Surface` 子卡，16dp 圆角，`weight = 1f`，无 elevation 无 offset)，始终 primaryContainer 高亮 | `containerColor = colorScheme.primaryContainer` + `contentColor = colorScheme.onPrimaryContainer` + `Icons.Filled.Add` + `Spacer(4.dp)` + `Text("+ 新建", style = labelSmall)` + `vertical padding = 12.dp`(总高 68dp 跟 TabCard 等高，无选中 / 未选中态区分) | — | `R.string.tab_new_note`(label) + `R.string.tab_new_note_cd`(contentDescription) |
| 3(我的) | `Surface` 子卡，16dp 圆角，`weight = 1f` | `containerColor = colorScheme.primary` + `contentColor = colorScheme.onPrimary` + `Icons.Filled.Person` | `containerColor = Color.Transparent` + `contentColor = colorScheme.onSurfaceVariant` + `Icons.Outlined.Person` | `R.string.tab_my` |

外层容器 MUST 是 1 个 `Surface`，顶部 24dp 圆角，`containerColor = colorScheme.surfaceVariant`,`tonalElevation = 1.dp`(营造跟【我的】tab 的 surfaceVariant 背景 + SectionCard 卡片同源的视觉)。

三槽 MUST 在同一 `Row` 内 `Arrangement.spacedBy(8.dp)` 均匀分布，完全 inline 在外层 `Surface` 内，**无**任何 `offset` / `Box.align(TopCenter)` / `FloatingActionButton` 凸起结构。

#### Scenario: 容器为 surfaceVariant 圆角 Surface
- **WHEN** `AppTabBar` 渲染
- **THEN** 外层是 1 个 `Surface`,`shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)`,`containerColor = colorScheme.surfaceVariant`,`tonalElevation = 1.dp`

#### Scenario: 三槽在同一 Row 内 inline 排列
- **WHEN** `AppTabBar` 渲染
- **THEN** `Surface { Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), Arrangement.spacedBy(8.dp), verticalAlignment = CenterVertically) { TabCard(Notes, weight=1f); CenterCreateCard(weight=1f); TabCard(Me, weight=1f) } }`,**不**存在 `Box.align(TopCenter)` / `offset(y = ...)` / `FloatingActionButton`

#### Scenario: 选中 tab 子卡用 primary 容器
- **WHEN** 当前选中 tab 是笔记 / 我的
- **THEN** 对应槽位 `Surface` 子卡 `containerColor = colorScheme.primary`,`contentColor = colorScheme.onPrimary`，图标为 `Icons.Filled.*` 实心版本，文案 `Text(stringResource(...))` 用 `contentColor`

#### Scenario: 未选中 tab 子卡透明容器
- **WHEN** 当前选中 tab 是 X(X ≠ 当前槽位)
- **THEN** 对应槽位 `Surface` 子卡 `containerColor = Color.Transparent`,`contentColor = colorScheme.onSurfaceVariant`，图标为 `Icons.Outlined.*` 描边版本，文案 `Text(stringResource(...))` 用 `contentColor`

#### Scenario: tab 子卡圆角 16dp
- **WHEN** `AppTabBar` 渲染两个 `Surface` 子卡
- **THEN** 子卡 `shape = RoundedCornerShape(16.dp)`,`clickable { onClick }`(整张子卡可点，不仅是图标)

#### Scenario: tab 槽文案来自 strings.xml
- **WHEN** 渲染任一 tab 槽位
- **THEN** 文案 MUST `Text(stringResource(R.string.tab_notes))` / `R.string.tab_my`,**不**直接 `Text("笔记")` 硬编码

#### Scenario: 中央 CenterCreateCard 始终 primaryContainer 高亮
- **WHEN** 渲染中央槽位
- **THEN** `CenterCreateCard` 是 `Surface(onClick = onCenterFabClick, shape = RoundedCornerShape(16.dp), color = colorScheme.primaryContainer, contentColor = colorScheme.onPrimaryContainer)`，内部 `Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = CenterHorizontally) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tab_new_note_cd)); Spacer(Modifier.size(4.dp)); Text(stringResource(R.string.tab_new_note), style = MaterialTheme.typography.labelSmall) }`，含"+ 新建" label 文本

#### Scenario: 中央槽位触控目标 ≥ 56dp
- **WHEN** 渲染中央 `CenterCreateCard`
- **THEN** Surface 整体高度 = icon 24dp + spacer 4dp + text ~16dp + 上下 padding 12dp × 2 = **68dp**，跟 `TabCard`(icon 24dp + spacer 4dp + text 16dp + padding 12dp × 2)同尺寸，三 Surface 在 Row 内等高且 icon 基线对齐;Surface 高度 ≥ 56dp 满足 M3 触控目标;`weight = 1f` 在 Row 内撑满剩余宽度

#### Scenario: 不再使用 M3 NavigationBar / NavigationBarItem / FloatingActionButton
- **WHEN** grep `AppShell.kt` `NavigationBar` / `NavigationBarItem` / `FloatingActionButton`
- **THEN** 0 匹配(标准 M3 组件整段替换为自定义 Surface + 3 子卡，CenterCreateCard 接管创建入口)

### Requirement: Center FAB creates a new note from any tab

`AppShell` 中央 FAB 点击 MUST `navController.navigate(QuicknoteEdit(id = "NEW", prefillFocus = true))`(走已有 `QuicknoteEdit` 类型安全路由);navigate **不**携带 `popUpTo`，也不切换当前 tab——返回时回到原 tab 选中态。

#### Scenario: 笔记 tab 点中央 + 进编辑器
- **WHEN** 当前 tab = 笔记(`NavController` 当前目的地是 `Notes`)，用户点中央 FAB
- **THEN** `NavController` navigate 到 `QuicknoteEdit(id="NEW", prefillFocus=true)`，编辑器输入框自动 focus;底部 tab bar 仍可见但 disabled 选中态保持"笔记"

#### Scenario: 我的 tab 点中央 + 进编辑器
- **WHEN** 当前 tab = 我的，用户点中央 FAB
- **THEN** navigate 到 `QuicknoteEdit(id="NEW", prefillFocus=true)`;编辑保存后 popBackStack 回到"我的" tab,**不**回到笔记 tab

#### Scenario: 编辑器保存返回原 tab
- **WHEN** 用户在编辑器保存笔记 → `popBackStack()`
- **THEN** 回到中央 FAB 触发时所在的 tab(笔记 / 我的)，系统返回手势亦同

#### Scenario: 编辑器不保存返回原 tab
- **WHEN** 用户在编辑器按 back / 系统返回手势(未保存)
- **THEN** 同上，回到原 tab，栈内仅多了一层 `QuicknoteEdit`

### Requirement: Tab switching uses NavController state, no separate source of truth

`AppShell` MUST **不**维护独立的 `selectedTab` state;`AppTabBar` 的"当前选中 tab"通过 `NavController.currentBackStackEntryAsState()` + 当前目的地的 route 字符串前缀推导(笔记 prefix=`notes`、我的 prefix=`me`)。这样 tab bar 选中态与 Nav 栈保持单向数据流，不会出现"栈在 tab A,bar 选 tab B"的不一致。

#### Scenario: 启动后 tab bar 默认选中笔记
- **WHEN** `AppShell` 首次渲染(consent gate 后)
- **THEN** `NavController.currentBackStackEntry` 对应 route 是 `Notes`,tab bar 槽 1 选中态为 `true`，槽 3 为 `false`

#### Scenario: 点"我的"切 tab
- **WHEN** 用户在 `AppTabBar` 槽 3 点击
- **THEN** `navController.navigate(Me) { popUpTo(AppShell) { saveState = true }; launchSingleTop = true; restoreState = true }`;`currentBackStackEntry` 切换到 `Me` route;tab bar 槽 3 选中态为 `true`

#### Scenario: 点"笔记"切 tab
- **WHEN** 用户在 `AppTabBar` 槽 1 点击(已在"我的")
- **THEN** 同上 popUpTo/saveState/restoreState 策略，导航回 `Notes`,tab bar 槽 1 选中态恢复

#### Scenario: 详情/编辑器页 tab bar 仍可见但未选中
- **WHEN** 用户从列表点条目进入 `QuicknoteDetail`，或从任意 tab 进 `QuicknoteEdit`
- **THEN** tab bar 继续渲染，选中态停留在最近一次 tab click(笔记 / 我的);详情/编辑器**不**改 tab 选中态

### Requirement: Settings and data entry live behind the My tab

`feature/my/MyScreen` MUST 是"我的" tab 的根屏，聚合以下入口(`ListItem` + 点击 push):

| 入口 | push 到 |
| --- | --- |
| 数据导入/导出 | `SettingsData` |
| AI 模型管理 | `SettingsModelManagement` |
| Prompt 模板 | `SettingsPromptTemplate` |
| 实体别名 | `SettingsAliasManagement` |
| 飞书同步 | `Settings`(已有 FeishuSyncLogSection) |
| 关于(版本号) | 纯展示，不 navigate |

`QuickNoteListScreen` MUST **不**再含任何 overflow menu 或 `navController.navigate(Settings*)` 的入口;所有设置/数据/AI 配置入口都从"我的" tab 走。

#### Scenario: 我的 tab 显示入口列表
- **WHEN** 用户切到"我的" tab
- **THEN** `MyScreen` 渲染 6 条 `ListItem`(数据 / AI 模型 / Prompt 模板 / 实体别名 / 飞书同步 / 关于)，每条带右侧 chevron

#### Scenario: 点数据导入导出
- **WHEN** 用户在 `MyScreen` 点"数据导入导出"
- **THEN** `navController.navigate(SettingsData)`，渲染 `SettingsDataScreen` 含"导出全部 ZIP"按钮(从 list 迁来)

#### Scenario: 点 AI 模型管理
- **WHEN** 用户在 `MyScreen` 点"AI 模型管理"
- **THEN** `navController.navigate(SettingsModelManagement)`，渲染 `ModelManagementScreen`

#### Scenario: 笔记 tab 不再有 overflow
- **WHEN** 用户切到笔记 tab
- **THEN** `QuickNoteListScreen` TopAppBar **不**含 `Icons.Filled.MoreVert` icon;**不**含 `DropdownMenu`;**不**调用 `navigate(Settings*)` / `navigate(SettingsData)`

### Requirement: Export-all ZIP entry moves to SettingsDataScreen

"导出全部为 ZIP" 按钮 MUST 从 `QuickNoteListScreen` 移除，迁入 `feature/settings/data/SettingsDataScreen.kt`，作为数据页内的一项操作。点击行为与 M4-3 既有 `ActivityResultContracts.CreateDocument("application/zip")` + `notes.json` 写入流程一致。

#### Scenario: 笔记 tab 不再含导出按钮
- **WHEN** grep `QuickNoteListScreen.kt` "Export" / "ZIP"
- **THEN** 0 个匹配(原 exportAllLauncher + DropdownMenuItem 整段删除)

#### Scenario: SettingsDataScreen 含导出按钮
- **WHEN** 用户从"我的 → 数据导入导出"进入 `SettingsDataScreen`
- **THEN** 该屏渲染 `R.string.me_data_export_all` 文案的按钮，点击触发原 exportAllLauncher 流程，zip 文件名仍为 `notes_export.zip`

### Requirement: AppShell is the new start destination

`AppNav` MUST 把 `startDestination` 由 `QuicknoteList` 改为 `AppShell`;`composable<AppShell>` block 渲染 `AppShell(navController, onCreateClick = { navController.navigate(QuicknoteEdit()) })`;`QuicknoteList` route 仍在 NavHost 中注册，但作为 `Notes` route 的实现(走 `composable<Notes>` 取代原 `composable<QuicknoteList>`，或保留 `QuicknoteList` 名字——以 archive 同步时方便对齐为准，实现细节走 design)。

widget pending route 启动回放逻辑(`LaunchedEffect(initialRoute)` 与 consent 后回放)的 `popUpTo` 锚点 MUST 由 `QuicknoteList` 改为 `AppShell`。

#### Scenario: 冷启进 AppShell
- **WHEN** App 冷启 + 已同意
- **THEN** `NavHost` 渲染 `composable<AppShell>` block → `AppShell` → 当前默认 tab = 笔记 → `Notes` route 渲染

#### Scenario: widget 启动 pending route 回放 popUpTo 锚点
- **WHEN** AppNav widget pending route navigate(如 `QuicknoteEdit` / `QuicknoteDetail`)，触发 `popUpTo(...)`
- **THEN** 锚点是 `AppShell`(不是 `QuicknoteList`)，清栈后剩 `AppShell` + 目标页

#### Scenario: 撤回同意回 onboarding
- **WHEN** `consentFlow.accepted = false`
- **THEN** `navController.navigate(OnboardingEntry.ROUTE_CONSENT) { popUpTo(0) { inclusive = true } }`;行为不变

### Requirement: My tab routes registered as @Serializable

`app/AppNav.kt` MUST 新增以下类型安全路由:

```kotlin
@Serializable data object AppShell
@Serializable data object Notes
@Serializable data object Me
```

`composable<Me>` block 渲染 `MyScreen(onNavigate = { route -> navController.navigate(route) }, onBack = ...)`。

#### Scenario: AppShell / Notes / Me 三个 route 注册
- **WHEN** grep `AppNav.kt`
- **THEN** 至少 1 个 `data object AppShell` + 1 个 `data object Notes` + 1 个 `data object Me`，每个对应 `composable<...>` block

#### Scenario: Notes route 走 QuickNoteListScreen
- **WHEN** `NavController` 当前目的地是 `Notes`
- **THEN** 渲染 `QuickNoteListScreen(onNoteClick = ..., onCreateClick = ...)`(无 `onSettingsClick` / `onPromptSettingsClick` 入参)

#### Scenario: Me route 走 MyScreen
- **WHEN** `NavController` 当前目的地是 `Me`
- **THEN** 渲染 `MyScreen(onNavigate = { route -> navController.navigate(route) })`

### Requirement: i18n coverage

新增的所有 tab / 我的 / 中央 FAB 文案 MUST 出现在 `values/strings.xml`(中文权威)与 `values-en/strings.xml`(英文)。

| key | 中文 | 英文 |
| --- | --- | --- |
| `tab_notes` | 笔记 | Notes |
| `tab_my` | 我的 | Me |
| `tab_new_note_cd` | 新建笔记 | New note |
| `me_title` | 我的 | Me |
| `me_data_title` | 数据导入导出 | Data |
| `me_model_title` | AI 模型管理 | AI Models |
| `me_prompt_title` | Prompt 模板 | Prompt Template |
| `me_alias_title` | 实体别名 | Entity Aliases |
| `me_feishu_title` | 飞书同步 | Feishu Sync |
| `me_about_title` | 关于 | About |
| `me_data_export_all` | 导出全部为 ZIP | Export all as ZIP |

删除 `quicknote_list_menu_cd`(不再用)。

#### Scenario: 新文案中文 + 英文都齐
- **WHEN** grep `res/values/strings.xml` 与 `res/values-en/strings.xml`
- **THEN** 上述 11 个 key 两个文件都有

#### Scenario: 旧 menu_cd 资源删除
- **WHEN** grep `res/values/strings.xml` `quicknote_list_menu_cd`
- **THEN** 0 匹配

#### Scenario: Compose 不硬编码文案
- **WHEN** grep `AppShell.kt` / `MyScreen.kt` / `QuickNoteListScreen.kt`
- **THEN** 任何中文 / 英文 UI 文本都通过 `stringResource(R.string.*)` 引用，不直接 `Text("笔记")` 等硬编码

### Requirement: Navigation routes changed — list no longer hosts new-note entry

`quick-note` spec 的 "Navigation routes for quick-note feature" Requirement 中，"新建入口" Scenario MUST 由"列表 FAB / 列表空状态按钮"改为"中央 FAB / 列表空状态按钮";列表自身的 FAB MUST 被移除(由 app-tab-bar 拥有)。

#### Scenario: 新建入口来自 tab bar 中央
- **WHEN** grep `QuickNoteListScreen.kt` "FloatingActionButton"
- **THEN** 0 个 `floatingActionButton = { FloatingActionButton(...) }`(列表 FAB 整段删除)

#### Scenario: 列表空状态按钮保留
- **WHEN** 列表空状态渲染
- **THEN** "新建第一条" 按钮(走 `R.string.quicknote_list_fab_new` 文案)仍存在，触发 `onCreateClick`

### Requirement: QuickNoteListScreen no longer hosts overflow menu

`quick-note` spec 的 "QuickNoteListScreen TopAppBar overflow menu 含 settings 入口(M4-3 新增)" Requirement MUST 被移除;新位置由 `app-tab-bar` 的 "Settings and data entry live behind the My tab" 接管。

#### Scenario: 列表 TopAppBar 无 MoreVert
- **WHEN** grep `QuickNoteListScreen.kt` "MoreVert" / "DropdownMenu"
- **THEN** 0 匹配

#### Scenario: 列表入参瘦身
- **WHEN** 读 `QuickNoteListScreen` 函数签名
- **THEN** 不含 `onSettingsClick` / `onPromptSettingsClick` 形参(原签名两个入参删除，只留 `onNoteClick` + `onCreateClick`)

### Requirement: Bottom-bar insets handled for gesture-nav devices

`AppTabBar` MUST `Modifier.windowInsetsPadding(WindowInsets.navigationBars)` 让 bar 在手势导航设备不被系统手势条遮挡;中央 FAB 的 `Modifier.offset(y = -16.dp)` 抬出部分由 `Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))` 或等效方式保证 FAB 阴影 / 点击区域不被裁。

#### Scenario: 手势导航设备 tab bar 不被遮挡
- **WHEN** App 运行在 Android 10+ 手势导航设备
- **THEN** `NavigationBar` 底部留出系统手势条空间，FAB 阴影 / 抬出部分完整可见

#### Scenario: 三按钮导航设备 tab bar 贴底
- **WHEN** App 运行在传统三按钮导航设备
- **THEN** `NavigationBar` 贴底，无多余空白
