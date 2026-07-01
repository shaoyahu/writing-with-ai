## Why

当前应用只有 `QuickNoteListScreen` 一个主屏，右上角 `MoreVert` DropdownMenu 把"设置"、"数据导入导出"、"Prompt 模板"等高频入口挤进二级菜单，发现性差、与 iOS/Android 主流 tab 模式不符。FAB 已经存在但只服务于列表 tab，且"新建笔记"在 AI 写作场景下用户也常需要，被迫先切回列表。重新组织成"底部 tab + 中央 +"是降低导航摩擦的必要重构。

## What Changes

- **新增**底部 3 槽 tab 栏:笔记 / 中央 + / 我的;中央 + 槽位用浮起的圆形 `FloatingActionButton` 实现，点击从**任意 tab**快速新建笔记(不切换 tab)。
- **移除** `QuickNoteListScreen` 右上角 `MoreVert` DropdownMenu 与其 3 个菜单项(设置 / 数据设置 / 导出全部)。
- **移除** `QuickNoteListScreen` 自带的右下角 FAB(职责上移至 tab shell)。
- **新增** `feature/my/MyScreen`:聚合头像区 / 数据 / AI 模型管理 / Prompt 模板 / 实体别名 / 飞书同步 / 关于。点击 push 到现有 `Settings*` 路由。
- **迁移**"导出全部 ZIP"入口:从 `QuickNoteListScreen` 移入 `SettingsDataScreen`(数据操作放数据页，语义一致)。
- **重排** `AppNav` 启动图:startDestination 由 `QuicknoteList` 改为 `AppShell`,shell 内嵌套子 NavHost 承载笔记 / 我的;`QuicknoteDetail` / `QuicknoteEdit` 仍是 stack 内跨 tab 子路由。
- **BREAKING**:`QuickNoteListScreen` 签名去掉 `onSettingsClick` / `onPromptSettingsClick` 两个入参(改由 tab 入口分发)。
- widget pending route 启动回放逻辑保留，`popUpTo` 锚点由 `QuicknoteList` 改为 `AppShell`。

## Capabilities

### New Capabilities

- `app-tab-bar`:应用底部 3 槽 tab 栏(笔记 / 中央 + / 我的)+ 中央凸起圆形 FAB 全局新建笔记。

### Modified Capabilities

- `app-shell`:startDestination 由 `QuicknoteList` 改为 `AppShell`;`AppNav` 路由图增加 tab shell 入口与"我的"子路由。
- `quick-note`:`QuickNoteListScreen` 不再含 FAB 与右上角 overflow menu;list tab 内容仍由 `QuickNoteListScreen` 承载(签名瘦身)。

## Impact

- **改动代码**:
  - `app/AppNav.kt` — startDestination + 新增 `AppShell` route + `Me` route
  - `app/AppShell.kt`(**新增**)— 3 槽 tab 容器 + 中央 FAB + 当前 tab 路由同步
  - `feature/quicknote/list/QuickNoteListScreen.kt` — 删 overflow + 删 FAB + 入参瘦身
  - `feature/quicknote/list/QuickNoteListViewModel.kt` — 不动(只是 UI 入参变化)
  - `feature/my/MyScreen.kt`(**新增**)— 聚合各项入口
  - `feature/my/MyEntry.kt`(**新增**)— `MyEntry.ROUTE` 常量
  - `feature/settings/data/SettingsDataScreen.kt` — 新增"导出全部 ZIP"按钮(从 list 迁来)
- **资源**:`res/values/strings.xml` / `res/values-en/strings.xml` 新增 `tab_notes` / `tab_my` / `tab_new_note_cd` / `me_data_export_all`;删除 `quicknote_list_menu_cd`。
- **依赖**:无新增第三方依赖;复用 M3 `NavigationBar` + `FloatingActionButton`。
- **风险**:见 design.md "风险与回退"。