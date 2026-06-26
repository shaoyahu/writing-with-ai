## REMOVED Requirements

### Requirement: QuickNoteListScreen TopAppBar overflow menu 含 settings 入口(M4-3 新增)
**Reason**: overflow menu 在主屏上发现性差,与 Android 主流 tab 模式不符。设置 / 数据 / AI 配置入口全部下沉到"我的" tab 的 `MyScreen`(`app-tab-bar` spec 接管)。
**Migration**: 用户从笔记 tab 切到底部"我的" tab → `MyScreen` → 选"数据导入导出" / "AI 模型管理" / "Prompt 模板" / "实体别名" / "飞书同步"。"导出全部为 ZIP" 操作迁入 `SettingsDataScreen` 的对应按钮。`onSettingsClick` / `onPromptSettingsClick` 两个 `QuickNoteListScreen` 入参删除。

## ADDED Requirements

### Requirement: QuickNoteListScreen no longer hosts overflow menu or FAB

`feature/quicknote/list/QuickNoteListScreen.kt` MUST **不**含:
- `Icons.Filled.MoreVert` icon 与 `DropdownMenu` block
- `Scaffold(floatingActionButton = { FloatingActionButton(...) })`
- `onSettingsClick` / `onPromptSettingsClick` 形参

入口职责全部由 `app/AppShell.kt` 的 `AppTabBar`(中央 FAB)与 `feature/my/MyScreen.kt` 接管。

#### Scenario: overflow menu 整段删除
- **WHEN** grep `QuickNoteListScreen.kt` "MoreVert" / "DropdownMenu" / "DropdownMenuItem"
- **THEN** 0 匹配

#### Scenario: 列表 FAB 整段删除
- **WHEN** grep `QuickNoteListScreen.kt` "FloatingActionButton" / "floatingActionButton ="
- **THEN** 0 匹配(`Scaffold` 的 `floatingActionButton` 参数省略或为空)

#### Scenario: 列表函数签名瘦身
- **WHEN** 读 `fun QuickNoteListScreen(...)` 形参列表
- **THEN** 仅含 `onNoteClick: (String) -> Unit` + `onCreateClick: () -> Unit` + `viewModel: QuickNoteListViewModel = hiltViewModel()`(无 `onSettingsClick` / `onPromptSettingsClick`)

#### Scenario: 列表空状态"新建"按钮保留
- **WHEN** 列表无数据
- **THEN** `EmptyState` Composable 仍渲染 `Button(onClick = onCreateClick) { Text(R.string.quicknote_list_fab_new) }`;点击触发 `onCreateClick` —— 但生产路径由 `AppShell` 调用 `navigate(QuicknoteEdit())`,测试环境仍可单独 verify `EmptyState` onClick

### Requirement: Export-all ZIP button moves from list to SettingsDataScreen

`feature/settings/data/SettingsDataScreen.kt` MUST 新增"导出全部为 ZIP"按钮(走 `R.string.me_data_export_all` 文案);点击触发原 `QuickNoteListScreen.exportAllLauncher` 流程:`ActivityResultContracts.CreateDocument("application/zip")` + zip 文件名 `notes_export.zip` + 逐条笔记写入。

#### Scenario: SettingsDataScreen 含导出按钮
- **WHEN** 用户进入 `SettingsDataScreen`
- **THEN** 渲染"导出全部为 ZIP" `Button`,点击触发 `exportAllLauncher.launch("notes_export.zip")`

#### Scenario: 导出 zip 文件名与 M4-3 一致
- **WHEN** 用户点导出按钮 + 选择保存位置
- **THEN** 系统文件选择器默认文件名 = `notes_export.zip`;zip 内逐条笔记 `.md` 文件 = `${title.ifBlank { id }}.md`

#### Scenario: 笔记 tab 不再含导出入口
- **WHEN** grep `QuickNoteListScreen.kt` "Export" / "exportAll" / "CreateDocument"
- **THEN** 0 匹配(原 exportAllLauncher 与 DropdownMenuItem 整段删除)