## Why

M0-M4-2 已落地:App 主流程 + widget + predictive back 都跑通。但用户笔记**全锁在本地 Room**，无迁移/备份路径。roadmap §5.3 拍板"导出:`notes` + `ai_history` + `tags` 打包为 JSON zip;Markdown zip(每条笔记一个 .md)作为可读版本，放同一压缩包的两个目录"。

**风险**:用户换机 / 重装 / 数据损坏 → 笔记全丢，不可接受(核心数据)。**v1 上架 Play Store 前必备的"数据所有权"承诺**:用户能导出所有笔记 + AI 历史，能在新设备 100% 还原。

roadmap §15.2 列 M4-3 `data-export-import` 是 M4 收尾，M4-4 `onboarding-consent`(apikey 加密)是最后一项，本 change 是 v1 数据迁移契约。

## What Changes

- **新增导出器**(`core/data/export/NoteExporter.kt`):Hilt 单例，API `suspend fun exportToJsonZip(notes: List<Note>): File`(导出 JSON zip)+ `suspend fun exportToMarkdownZip(notes: List<Note>): File`(导出 Markdown zip);输出 zip 含 `notes.json` / `notes/` 目录(每条 .md)+ `ai_history.json` + `tags.json` + `meta.json`(版本 / 时间戳)
- **新增导入器**(`core/data/export/NoteImporter.kt`):Hilt 单例，API `suspend fun importFromZip(file: File): ImportReport`;JSON zip 去重(`note.id` 已存在跳过);失败条目收集到 `import_report.md` 附压缩包根目录(roadmap §5.3)
- **新增 zip 工具**(`core/data/export/ZipHelper.kt`):基于 JDK `ZipOutputStream` / `ZipInputStream`(android.util 已自带，无需第三方)
- **新增 settings UI**(`feature/settings/data/SettingsDataScreen.kt`):"导出"按钮 → SAF(Storage Access Framework)`ACTION_CREATE_DOCUMENT` 让用户选位置;`ACTION_OPEN_DOCUMENT` 选 zip 导入
- **新增 settings VM**(`feature/settings/data/SettingsDataViewModel.kt`):`@HiltViewModel`，注入 `NoteExporter` + `NoteImporter`，持有 `StateFlow<DataUiState>(Idle / Exporting / Importing / Done(report) / Failed(error))`
- **改 `feature/settings/`**:`AppNav.kt` 加 `settings` data route(独立设置 tab,M5 polish 集成完整 settings);初版只 data tab，其他 onboarding-consent tab 留 M4-4
- **改 `NavController`**:`SettingsDataScreen` 进/退栈(M4-2 LaunchedEffect 双保险)
- **新增 i18n**(`values/strings.xml` + `values-en/`):
  - `settings_data_title` = "数据迁移"
  - `settings_data_export` = "导出全部数据"
  - `settings_data_import` = "导入数据"
  - `settings_data_exporting` = "导出中..."
  - `settings_data_importing` = "导入中..."
  - `settings_data_done` = "完成，共 %1$d 条笔记"
  - `settings_data_failed` = "导出/导入失败:%1$s"
  - `settings_data_no_data` = "暂无笔记可导出"
  - `import_report_summary` = "导入完成:%1$d 成功 / %2$d 跳过 / %3$d 失败，详情见 import_report.md"
- **新增依赖**:`androidx.documentfile:documentfile`(SAF 兼容，`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`)
- **新增测试**:JUnit5 + Robolectric 验导出 zip 含 `notes.json` / `ai_history.json` / `tags.json` / `meta.json` 4 文件;导入 zip JSON 反序列化
- **BREAKING**:无
- **不引入**:
  - 第三方 zip 库(android.util 已自带)
  - Markdown 渲染(M3 已有 Markdown 源码，导出纯文本即可)
  - 云同步(roadmap §15 拍 v1 仅本地)
  - 自动备份(M5 polish 阶段)

## Capabilities

### New Capabilities
- `data-export-import`:`NoteExporter` / `NoteImporter` / `ZipHelper` / `SettingsDataScreen` / `SettingsDataViewModel` + 9 个 i18n key

### Modified Capabilities
- `quick-note`:无 schema 变更;M1 既有 `NoteRepository` 不变，exporter 直接读 Room 数据

## Impact

- **新增 package**:
  - `core/data/export/` — NoteExporter / NoteImporter / ZipHelper / ExportModels(Serializable data class)
  - `feature/settings/data/` — SettingsDataScreen / SettingsDataViewModel
- **新增 res**:
  - 9 个 `settings_data_*` / `import_report_summary` i18n key 双语
- **修改**:
  - `app/AppNav.kt` 加 `SettingsData` route + composable(独立 tab 简版)
  - `app/MainActivity.kt` 不变(intent 解析只走 quick-note,settings 不接受 widget 启动)
  - `feature/quicknote/list/QuickNoteListScreen.kt` 加 "设置" 入口(TopAppBar overflow menu)
- **新增依赖**:`androidx.documentfile:documentfile` SAFA 类库
- **风险**:
  - Android 11+ SAF 是标准数据迁移路径，但旧设备(API < 30)需要 `WRITE_EXTERNAL_STORAGE` — M0 minSdk 26,**API 26-29 需要 legacy 存储权限**，本 change 加 `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` 权限声明，M0 manifest 没声明
  - 导入 zip 解析时 Room 写入需事务;executor 用 `IO` dispatcher;export 大文件(1000+ 笔记)阻塞 UI — 走 `viewModelScope.launch + Dispatchers.IO`
  - Markdown 导出文件名特殊字符(`/` / `:`)需 URL-encode;M5 polish 处理全字符集
  - 导入 id 冲突:`Note.id` 是 UUID,JSON 导入按 id 去重;若用户在两台设备分别写笔记 → 后导入的 id 不冲突，合并;**但 createdAt 不一致**(两台设备时间不同)— v1 接受，M5 polish 处理时间同步
  - 导入失败(数据库写入异常)逐条 try-catch，失败条目写入 `import_report.md`，不中断整批