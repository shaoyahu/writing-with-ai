## 1. 资源与字符串

- [x] 1.1 `res/values/strings.xml` 新增 11 个 key:`tab_notes` / `tab_my` / `tab_new_note_cd` / `me_title` / `me_data_title` / `me_model_title` / `me_prompt_title` / `me_alias_title` / `me_feishu_title` / `me_about_title` / `me_data_export_all`
- [x] 1.2 `res/values-en/strings.xml` 同步新增上述 11 个 key(英文，见 `app-tab-bar` spec "i18n coverage" 表格)
- [x] 1.3 `res/values/strings.xml` 删除 `quicknote_list_menu_cd`(已停用)

## 2. 我的 tab — 新 feature

- [x] 2.1 新建 `feature/my/MyEntry.kt`，暴露 `object MyEntry { const val ROUTE_ME = "me" }`(跨 feature 引用入口)
- [x] 2.2 新建 `feature/my/MyScreen.kt`，实现 `@Composable fun MyScreen(onNavigate: (String) -> Unit, onBack: () -> Unit)`，渲染 6 条 `ListItem`(数据 / AI 模型 / Prompt 模板 / 实体别名 / 飞书同步 / 关于)
- [x] 2.3 `MyScreen` 文件底部加 `@Preview` 函数 `MyScreenPreview`(CLAUDE.md "预览函数命名 XxxPreview 配 @Preview 注解"约定)
- [x] 2.4 6 条 `ListItem` 头部加 `TopAppBar(title = { Text(R.string.me_title) }, navigationIcon = { ArrowBack → onBack })`(遵循 app-shell spec "所有非主页 destination TopAppBar 含 navigationIcon = ArrowBack")

## 3. AppShell — 3 槽 tab 容器

- [x] 3.1 新建 `app/AppShell.kt`，实现 `@Composable fun AppShell(navController: NavHostController, onCreateClick: () -> Unit)`
- [x] 3.2 `AppShell.kt` 实现 `AppTabBar` 子 Composable:`NavigationBar` 含 3 个槽位(左 `NavigationBarItem` notes / 中 `Box` 占位 + 圆形 `FloatingActionButton` offset y=-16dp + shadow 8dp / 右 `NavigationBarItem` me),`Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))` 处理手势条
- [x] 3.3 `AppShell.kt` 渲染 `NavHost(navController, startDestination = Notes)`，内含 `composable<Notes> { QuickNoteListScreen(...) }` 与 `composable<Me> { MyScreen(...) }`
- [x] 3.4 tab 切换走 `navController.navigate(target) { popUpTo(AppShell) { saveState = true }; launchSingleTop = true; restoreState = true }`;当前选中态通过 `navController.currentBackStackEntryAsState()` 推导(`app-tab-bar` spec "Tab switching uses NavController state")
- [x] 3.5 中央 FAB `onClick` 调 `onCreateClick` lambda，生产路径下 lambda = `{ navController.navigate(QuicknoteEdit()) }`(由 `composable<AppShell>` block 注入)
- [x] 3.6 `AppShell.kt` 文件底部加 `@Preview` 函数 `AppShellPreview`

## 4. AppNav — 路由图重排

- [x] 4.1 `app/AppNav.kt` 新增三个 `@Serializable` route:`data object AppShell` / `data object Notes` / `data object Me`
- [x] 4.2 `AppNav.kt` `NavHost.startDestination` 由 `QuicknoteList` 改为 `AppShell`
- [x] 4.3 `AppNav.kt` 新增 `composable<AppShell> { AppShell(navController, onCreateClick = { navController.navigate(QuicknoteEdit()) }) }`
- [x] 4.4 `AppNav.kt` `composable<QuicknoteList>` block 重命名为 `composable<Notes>`(或保留 `QuicknoteList` 作为 alias,archive 同步时一致优先);调用 `QuickNoteListScreen(onNoteClick, onCreateClick)`(去掉 `onSettingsClick` / `onPromptSettingsClick`)
- [x] 4.5 `AppNav.kt` 新增 `composable<Me> { MyScreen(onNavigate = { route -> navController.navigate(route) }, onBack = { navController.popBackStack() }) }`
- [x] 4.6 `AppNav.kt` 三处 `popUpTo(QuicknoteList)` 改为 `popUpTo(AppShell)`(`LaunchedEffect(initialRoute)` widget 路径 + consent 后回放路径 + apikey-prompt 后回放路径)

## 5. QuickNoteListScreen — 删除 overflow / FAB / 瘦身入参

- [x] 5.1 `feature/quicknote/list/QuickNoteListScreen.kt` 删 `MoreVert` icon + `DropdownMenu` block(行 107-145)
- [x] 5.2 `QuickNoteListScreen.kt` 删 `Scaffold.floatingActionButton = { FloatingActionButton(onClick = onCreateClick) {...} }` block(行 147-154)
- [x] 5.3 `QuickNoteListScreen.kt` 删 `exportAllLauncher` 整段(`ActivityResultContracts.CreateDocument` + zip 写入 + Toast)(行 84-105)
- [x] 5.4 `QuickNoteListScreen.kt` 函数签名去 `onSettingsClick: () -> Unit = {}` 与 `onPromptSettingsClick: () -> Unit = {}` 两个形参
- [x] 5.5 `QuickNoteListScreen.kt` 删对应未使用的 import:`Icons.Filled.MoreVert` / `DropdownMenu` / `DropdownMenuItem` / `FloatingActionButton` / `rememberLauncherForActivityResult` / `ActivityResultContracts` / `Toast`

## 6. SettingsDataScreen — 新增"导出全部 ZIP"

- [x] 6.1 `feature/settings/data/SettingsDataScreen.kt` 把 `exportAllLauncher` 逻辑整段搬过来(`ActivityResultContracts.CreateDocument("application/zip")` + zip 写入 + Toast)
- [x] 6.2 `SettingsDataScreen.kt` 渲染"导出全部为 ZIP" `Button`，文案 `stringResource(R.string.me_data_export_all)`,onClick 调 `exportAllLauncher.launch("notes_export.zip")`
- [x] 6.3 `SettingsDataScreen.kt` 加新 import:`androidx.activity.compose.rememberLauncherForActivityResult` / `androidx.activity.result.contract.ActivityResultContracts` / `android.widget.Toast`

## 7. 验证

- [x] 7.1 `export JAVA_HOME=/opt/homebrew/opt/openjdk@17 && ./gradlew :app:assembleDebug` 构建通过
- [x] 7.2 `./gradlew :app:ktlintCheck` 通过(若 `AppShell.kt` / `MyScreen.kt` 触发 FunctionNaming，加 `@file:Suppress("FunctionNaming")`，与 M4 既有代码一致)
- [x] 7.3 `./gradlew :app:testDebugUnitTest` 既有测试全绿
- [x] 7.4 `openspec validate app-bottom-tab-bar --strict` 通过
- [x] 7.5 grep 校验(`app-tab-bar` spec scenarios):
  - `grep "MoreVert\|DropdownMenu" app/src/main/java/com/yy/writingwithai/feature/quicknote/list/QuickNoteListScreen.kt` → 0 匹配
  - `grep "FloatingActionButton" app/src/main/java/com/yy/writingwithai/feature/quicknote/list/QuickNoteListScreen.kt` → 0 匹配
  - `grep "popUpTo(QuicknoteList)" app/src/main/java/com/yy/writingwithai/app/AppNav.kt` → 0 匹配
  - `grep "popUpTo(AppShell)" app/src/main/java/com/yy/writingwithai/app/AppNav.kt` → ≥ 1 匹配
  - `grep "data object AppShell\|data object Notes\|data object Me" app/src/main/java/com/yy/writingwithai/app/AppNav.kt` → 各 ≥ 1
- [x] 7.6 真机 / 模拟器冒烟清单:
  - 冷启 App → 默认到笔记 tab
  - 笔记 tab 点中央 + → 编辑器(prefillFocus=true，输入框 focus)
  - 编辑器保存 → 回到笔记 tab，新笔记在列表顶部
  - 切到"我的" tab → 6 条 ListItem 可见
  - 点"数据导入导出" → `SettingsDataScreen` 含"导出全部为 ZIP"按钮
  - 点"AI 模型管理" → `ModelManagementScreen`
  - 笔记 tab TopAppBar 无 MoreVert，无 FAB
  - widget 启动 → 同意后 navigate 到 widget route(popUpTo 锚点 AppShell)