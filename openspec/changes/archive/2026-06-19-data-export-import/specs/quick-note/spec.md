# quick-note

## Purpose

随手记(M1)的完整数据模型与 UI 行为契约;定义 `Note` / `Tag` 实体形状、CRUD / 搜索 / 标签 / 固定 / 单条分享导出的端到端行为，以及 Nav 路由契约。本 spec 是 M2 AI 抽象层 + M3 AI 操作 UI + M4-1 widget + M4-2 predictive back + M4-3 数据迁移的前置。

TBD — synced from OpenSpec change `quick-note-feature`(2026-06-18)。

## REMOVED Requirements

无(M1 / M3 / M4-1 / M4-2 全部要求保留)。

## RENAMED Requirements

无。

## ADDED Requirements

### Requirement: QuickNoteListScreen TopAppBar overflow menu 含 settings 入口(M4-3 新增)

`feature/quicknote/list/QuickNoteListScreen.kt` TopAppBar `actions` MUST 含 overflow menu(`Icons.Filled.MoreVert` icon → DropdownMenu)，菜单项 "数据迁移"(R.string.settings_data_title)点击后 `navController.navigate(SettingsData)`(M4-3 新增 nav route)。

#### Scenario: overflow menu 含数据迁移
- **WHEN** grep `QuickNoteListScreen.kt` "MoreVert"
- **THEN** 至少 1 个 `IconButton(onClick = { ... DropdownMenu(...) { DropdownMenuItem(text = { Text(stringResource(R.string.settings_data_title)) }, onClick = { navController.navigate(SettingsData) }) } })`

#### Scenario: 点数据迁移跳 settings
- **WHEN** 用户点 "数据迁移" 菜单项
- **THEN** `navController.navigate(SettingsData)` 跳 SettingsDataScreen

#### Scenario: 数据迁移仅在 overflow menu，不在 TopAppBar 显眼位置
- **WHEN** grep `QuickNoteListScreen.kt` `navigate(SettingsData)`
- **THEN** 0 个直接 `IconButton(onClick = { navController.navigate(SettingsData) })`(只能在 overflow menu 内)— TopAppBar 不被数据迁移按钮占据

### Requirement: Note schema 不变(M4-3 验证项)

M4-3 MUST NOT 修改 `NoteEntity` / `NoteDao` / `Note` 字段;`NoteExporter` 读 M1 既有 schema，导出字段 = 数据库字段。

#### Scenario: Note 字段保持 v2
- **WHEN** `git diff openspec/changes/data-export-import/ core/data/db/`
- **THEN** 0 个字段新增 / 0 个字段类型变更;`AppDatabase` 仍 `version = 2`

#### Scenario: 导出字段集 = Note data class
- **WHEN** `NoteExporter.exportToJsonZip` 序列化 `Note` 实例
- **THEN** `notes.json` 元素的字段集 = `Note(id, title, content, createdAt, updatedAt, isPinned, lastAiOp, lastAiAt)`(M1 schema，无缺无多)

### Requirement: ai_history schema 不变(M4-3 验证项)

M4-3 MUST NOT 修改 `AiHistoryEntity` / `AiHistoryDao` / `AiHistory` 字段;`NoteExporter` 读 M2 既有 schema。

#### Scenario: ai_history 字段保持 v2
- **WHEN** `git diff openspec/changes/data-export-import/ core/data/db/`
- **THEN** 0 个 `ai_history` 相关字段变更

### Requirement: note_tags schema 不变(M4-3 验证项)

M4-3 MUST NOT 修改 `NoteTagCrossRef` / `NoteTagDao` 字段;`tags.json` 导出格式 `Map<noteId, List<String>>` 是 M1 `note_tags` 表的反查投影。

#### Scenario: tags.json 投影自 note_tags
- **WHEN** `NoteExporter` 调 `noteTagDao.observeAllCrossRefs().first()` 拿 `(noteId, tag)` 行列表
- **THEN** 转 `Map<noteId, List<String>>` 写入 `tags.json`(导入时按 map 写回 `note_tags` 表)

### Requirement: AppNav 加 SettingsData route(M4-3 新增)

`app/AppNav.kt` MUST 加:
- `@Serializable data object SettingsData`
- `composable<SettingsData> { SettingsDataScreen(onBack = { navController.popBackStack() }) }`

#### Scenario: SettingsData route 注册
- **WHEN** grep `AppNav.kt` "SettingsData"
- **THEN** 至少 1 个 `data object SettingsData` + 1 个 `composable<SettingsData> {`

#### Scenario: back 行为回 QuickNoteListScreen
- **WHEN** 用户从 SettingsDataScreen 按 back
- **THEN** `navController.popBackStack()` 回 QuickNoteListScreen(非退出 App)— launchSingleTask + M4-2 popUpTo 兜底

#### Scenario: SettingsData 不在 widget Intent 启动路径
- **WHEN** `core/widget/QuickNoteWidget.kt createNoteIntent(context)` route = "quicknote/edit?prefillFocus=true" 或 "quicknote/detail/{id}"
- **THEN** 0 个 "quicknote/settings" route — widget 启动不到 SettingsDataScreen

### Requirement: NoteRoomSchema ExportModels 不重(M4-3 验证项)

`core/data/export/ExportModels.kt` 内 `ExportNote` / `ExportAiHistory` 等 Serializable data class 字段 MUST 与 `Note` / `AiHistory` 一一对应(无缺无多);导入时字段缺失用默认值。

#### Scenario: ExportNote 字段 = Note 字段
- **WHEN** grep `ExportModels.kt` "data class ExportNote"
- **THEN** 字段集 `(id, title, content, createdAt, updatedAt, isPinned, lastAiOp, lastAiAt)` 与 `Note` 一致

#### Scenario: 旧版本 zip 导入新版本字段兼容
- **WHEN** zip `notes.json` 缺 `lastAiOp` / `lastAiAt`(M1 老版本导)
- **THEN** `ImportReport` 验 `meta.schema_version`，字段缺失用默认值 `null`(`lastAiOp` / `lastAiAt` 本就是可空)

### Requirement: NoteListScreen 不被 M4-3 改其他功能(M4-3 验证项)

M4-3 MUST NOT 改 QuickNoteListScreen 其他功能(搜索 / 列表 / 分享 / pin)，只增 overflow menu 数据迁移入口。

#### Scenario: 列表屏搜索保留
- **WHEN** grep `QuickNoteListScreen.kt` "search"
- **THEN** M1 既有搜索框仍在 TopAppBar(代码未改)

#### Scenario: 列表屏分享保留
- **WHEN** grep `QuickNoteListScreen.kt` "Share"
- **THEN** M1 既有分享按钮仍在每条 row(代码未改)

#### Scenario: pin 固定保留
- **WHEN** grep `QuickNoteListScreen.kt` "PushPin"
- **THEN** M1 既有 pin/unpin 逻辑保留(代码未改)