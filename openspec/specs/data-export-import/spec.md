# data-export-import Specification

## Purpose
TBD - created by archiving change data-export-import. Update Purpose after archive.
## Requirements
### Requirement: NoteExporter exports notes + ai_history + tags to JSON zip

`core/data/export/NoteExporter.kt` MUST 是 `@Singleton class NoteExporter @Inject constructor(noteRepository: NoteRepository, aiHistoryRepository: AiHistoryRepository, zipHelper: ZipHelper)`,提供 `suspend fun exportToJsonZip(notes: List<Note>): File`,返回 zip File 路径。

zip MUST 含 4 JSON 文件:
- `notes.json`:`List<Note>`(M1 schema)
- `ai_history.json`:`List<AiHistory>`(M2 schema)
- `tags.json`:`Map<noteId, List<String>>`(note_tags 映射)
- `meta.json`:`{"export_timestamp": ISO-8601, "app_version": "0.4.0", "schema_version": "1"}`

#### Scenario: 导出 zip 含 4 JSON 文件
- **WHEN** `NoteExporter.exportToJsonZip(notes = [Note(id="n1", title="晨跑", ...)])`
- **THEN** 返回 zip File,内含 `notes.json` / `ai_history.json` / `tags.json` / `meta.json` 4 个 JSON 文件 + `notes/` 子目录

#### Scenario: meta.json 含时间戳与版本
- **WHEN** 导出 zip
- **THEN** `meta.json` 含 `"export_timestamp": "2026-06-19T10:30:00.000Z"`,`"app_version": "0.4.0"`,`"schema_version": "1"`

#### Scenario: tags.json 是 noteId → List<String> 映射
- **WHEN** 笔记 `id="n1"` 有 tag "灵感" 与 "日记"
- **THEN** `tags.json` `{"n1": ["灵感", "日记"]}`

#### Scenario: notes.json 反序列化兼容 schema_version
- **WHEN** 旧版本(0.3.x)导出的 zip 导入到新版本(0.4.0)
- **THEN** `meta.json.schema_version == "1"` 兼容,新字段缺失时用默认值

### Requirement: NoteExporter 同时生成 Markdown 视图(可读副本)

zip MUST 含 `notes/` 子目录,每条笔记一个 `.md` 文件,内容 `# 标题\n\n内容` 格式。文件名 MUST 是 `${noteId}.md`(noteId 是 UUID,特殊字符安全)。

#### Scenario: Markdown 文件名用 noteId
- **WHEN** 笔记 `id="abc-123"`,`title="晨跑"`
- **THEN** 文件 `notes/abc-123.md`,内容 `# 晨跑\n\n...`

#### Scenario: 空标题 fallback
- **WHEN** 笔记 `title=""`,`content="今天天气很好"`
- **THEN** 文件 `notes/<id>.md`,内容 `# Untitled\n\n今天天气很好`(fallback `# Untitled`)

#### Scenario: Markdown 与 JSON 内容一致
- **WHEN** 同一笔记,导出 zip
- **THEN** `notes/<id>.md` 正文 = `notes.json[i].content`(人可读视图与机器可读视图一致)

### Requirement: NoteImporter imports zip via id-dedup

`core/data/export/NoteImporter.kt` MUST 是 `@Singleton class NoteImporter @Inject constructor(noteRepository: NoteRepository, aiHistoryRepository: AiHistoryRepository, zipHelper: ZipHelper)`,提供 `suspend fun importFromZip(file: File): ImportReport`。

每条笔记检查 `noteDao.getById(note.id)`:
- `null` → upsert(导入),ImportResult.IMPORTED
- 非 null → 跳过(不覆盖),ImportResult.SKIPPED

#### Scenario: 导入新笔记
- **WHEN** zip 内 `notes.json` 含 `note.id = "n1"`,Room 无此 id
- **THEN** `noteRepository.upsert(note, tags)` 落库,ImportResult.IMPORTED(成功 +1)

#### Scenario: 导入已存在的笔记跳过
- **WHEN** zip 内 `notes.json` 含 `note.id = "n1"`,Room 已有 `id="n1"` 的笔记
- **THEN** **不** 覆盖,跳过,ImportResult.SKIPPED(跳过 +1)

#### Scenario: tags 同步导入
- **WHEN** zip `tags.json` `{"n1": ["灵感", "日记"]}`
- **THEN** `note_tags` 表插入 2 行 `(noteId="n1", tag="灵感")` + `(noteId="n1", tag="日记")`

#### Scenario: ai_history 同步导入
- **WHEN** zip `ai_history.json` 含 3 条 AiHistory
- **THEN** `ai_history` 表插入 3 行(若 noteId 关联的 note 已存在)

### Requirement: 失败条目收集到 import_report.md

导入过程中任何 try-catch 捕获的异常 MUST 不中断整批,失败条目详细信息 MUST 写入压缩包根目录的 `import_report.md`(重新打包 zip 含 report)。

#### Scenario: 单条 note 失败
- **WHEN** `notes.json` 含 10 条,导入过程中第 3 条触发 Room UNIQUE 约束冲突(因 tag 重复)
- **THEN** 成功 7 + 跳过 2 + 失败 1(第 3 条);`import_report.md` 含失败详情 `note id="n3" title="..."` + 失败原因 + 解决建议

#### Scenario: 报告格式纯文本 Markdown
- **WHEN** 导入完成
- **THEN** `import_report.md`:
  ```
  # writing-with-ai 导入报告
  
  时间:2026-06-19T10:30:00
  总计:10 条笔记
  - 成功:7
  - 跳过(id 重复):2
  - 失败:1
  
  ## 失败详情
  
  ### note id=xxx title="..."
  失败原因:...
  ```

#### Scenario: 全部成功时报告仍生成
- **WHEN** 导入完成,0 失败
- **THEN** `import_report.md` 含总计行 + "失败:0",无失败详情

### Requirement: SettingsDataScreen 提供 export/import UI

`feature/settings/data/SettingsDataScreen.kt` MUST 是 `@Composable fun SettingsDataScreen(viewModel: SettingsDataViewModel = hiltViewModel())`,包含:
- TopAppBar 标题 "数据迁移"
- "导出全部数据" 按钮 → `SettingsDataViewModel.startExport()`
- "导入数据" 按钮 → `SettingsDataViewModel.startImport()`
- 进度 UI(`DataUiState.Exporting` / `Importing` 显示 CircularProgressIndicator)
- 结果 UI(`DataUiState.Done(report)` 显示成功条数 + 失败详情;`Failed(error)` 显示错误)

按钮触发 SAF Intent:
- 导出 → `Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip")`
- 导入 → `Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/zip")`

#### Scenario: 点导出按钮启动 SAF 文件选择器
- **WHEN** 用户在 SettingsDataScreen 点 "导出全部数据" 按钮
- **THEN** 系统弹出 SAF 文件选择器,默认文件名 `writing-with-ai-export-{timestamp}.zip`

#### Scenario: 导出中显示进度
- **WHEN** 用户选位置,SAF 回调触发 `viewModel.exportToJsonZip(uri)`
- **THEN** UI 显示 CircularProgressIndicator + "导出中..."(R.string.settings_data_exporting)

#### Scenario: 导出完成显示结果
- **WHEN** 导出完成
- **THEN** UI 显示 "完成,共 X 条笔记"(R.string.settings_data_done),X = notes.size

#### Scenario: 导出失败显示错误
- **WHEN** 导出过程抛 IOException
- **THEN** UI 显示 "导出/导入失败:..."(R.string.settings_data_failed)

### Requirement: SettingsDataViewModel 用 viewModelScope.launch + Dispatchers.IO

`feature/settings/data/SettingsDataViewModel.kt` MUST 是 `@HiltViewModel class SettingsDataViewModel @Inject constructor(noteExporter, noteImporter, ...)`:
- `private val _uiState = MutableStateFlow<DataUiState>(Idle)` + `val uiState: StateFlow<DataUiState>`
- `fun exportToJsonZip(uri: Uri)` / `fun importFromZip(uri: Uri)` 走 `viewModelScope.launch(Dispatchers.IO) { ... }`
- `sealed interface DataUiState { Idle / Exporting / Importing / Done(report: ImportReport) / Failed(error: String) }`

#### Scenario: 导出走 IO dispatcher
- **WHEN** `viewModel.exportToJsonZip(uri)` 被调
- **THEN** `viewModelScope.launch(Dispatchers.IO) { ... }` 跑导出,不阻塞主线程

#### Scenario: 导入同样走 IO
- **WHEN** `viewModel.importFromZip(uri)` 被调
- **THEN** 走 IO dispatcher,Room 写入用 `withTransaction`

#### Scenario: ViewModel 销毁取消任务
- **WHEN** 用户按 back 退出 SettingsDataScreen,ViewModel onCleared
- **THEN** `viewModelScope` 取消,导出/导入任务中断,无 leak

### Requirement: settings 入口在 QuickNoteListScreen TopAppBar overflow menu

`feature/quicknote/list/QuickNoteListScreen.kt` TopAppBar `actions` MUST 含 overflow menu(MoreVert icon → DropdownMenu),菜单项 "数据迁移" 点击后 `navController.navigate(SettingsData)`。

#### Scenario: overflow menu 含数据迁移
- **WHEN** 用户在 QuickNoteListScreen TopAppBar 点 overflow 图标(3 点)
- **THEN** DropdownMenu 显示 "数据迁移"(R.string.settings_data_title)菜单项

#### Scenario: 点数据迁移跳 settings
- **WHEN** 用户点 "数据迁移" 菜单项
- **THEN** `navController.navigate(SettingsData)` 跳 SettingsDataScreen

#### Scenario: 数据迁移仅在 overflow menu,不在 TopAppBar 显眼位置
- **WHEN** grep `QuickNoteListScreen.kt`
- **THEN** 0 个直接 `IconButton(onClick = { navController.navigate(SettingsData) })`(只能在 overflow menu 内)— TopAppBar 不被数据迁移按钮占据

### Requirement: AppNav 加 SettingsData route

`app/AppNav.kt` MUST 加 `@Serializable data object SettingsData` + `composable<SettingsData> { SettingsDataScreen(onBack = { navController.popBackStack() }) }`。

#### Scenario: SettingsData route 注册
- **WHEN** grep `AppNav.kt` "SettingsData"
- **THEN** 至少 1 个 `data object SettingsData` + 1 个 `composable<SettingsData> {`

#### Scenario: back 行为正确
- **WHEN** 用户从 SettingsDataScreen 按 back
- **THEN** `navController.popBackStack()` 回 QuickNoteListScreen(非退出 App)

### Requirement: SAF 文件选择用 ACTION_CREATE_DOCUMENT / ACTION_OPEN_DOCUMENT

settings UI MUST 走 Android SAF(无需 legacy 权限):
- 导出:`Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip")` + `Intent.EXTRA_TITLE` 默认文件名
- 导入:`Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/zip")`

#### Scenario: 导出 Intent type 是 zip
- **WHEN** `Intent(Intent.ACTION_CREATE_DOCUMENT)`
- **THEN** `type = "application/zip"`,系统文件选择器过滤 zip 格式

#### Scenario: 导入 Intent type 是 zip
- **WHEN** `Intent(Intent.ACTION_OPEN_DOCUMENT)`
- **THEN** `type = "application/zip"`

### Requirement: Android 11+ SAF 路径无需 WRITE_EXTERNAL_STORAGE

`AndroidManifest.xml` MUST NOT 声明 `WRITE_EXTERNAL_STORAGE`(Android 11+ scoped storage 不需要)。仅 API 26-29 设备加 `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` 权限做 fallback。

#### Scenario: API 30+ 无外部权限
- **WHEN** grep `AndroidManifest.xml` `<uses-permission`
- **THEN** 0 个 `WRITE_EXTERNAL_STORAGE`;0 个 `READ_EXTERNAL_STORAGE`(API 30+ 不需要)

#### Scenario: API 26-29 旧设备 fallback
- **WHEN** API < 30 设备,SAF 文件选择器不可用
- **THEN** `WRITE_EXTERNAL_STORAGE` 权限申请弹窗,用户授权后写入

### Requirement: androidx.documentfile 仅 1 个新依赖

`gradle/libs.versions.toml` MUST 加 `documentfile = "1.0.1"` + library entry `androidx-documentfile = { module = "androidx.documentfile:documentfile", version.ref = "documentfile" }`;`app/build.gradle.kts` 加 `implementation(libs.androidx.documentfile)`。

#### Scenario: 依赖 1 个新增
- **WHEN** grep `gradle/libs.versions.toml` `[libraries]`
- **THEN** `androidx-documentfile = ...` 单个 entry

#### Scenario: 第三方 zip 库不引入
- **WHEN** grep `app/build.gradle.kts` `implementation(libs.`
- **THEN** 0 个 zip4j / apache-compress 等第三方 zip 库;只用 `java.util.zip` JDK 内置

### Requirement: i18n 9 个 key

所有 settings 数据迁移相关文案 MUST 走 `R.string.settings_data_*`(中文 + 英文 TODO 占位);`import_report_summary` MUST 是 `String.format` 占位。

| key | 中文 | 用途 |
| --- | --- | --- |
| `settings_data_title` | 数据迁移 | TopAppBar 标题 + overflow menu 项 |
| `settings_data_export` | 导出全部数据 | 导出按钮 |
| `settings_data_import` | 导入数据 | 导入按钮 |
| `settings_data_exporting` | 导出中... | 导出进度 |
| `settings_data_importing` | 导入中... | 导入进度 |
| `settings_data_done` | 完成,共 %1$d 条笔记 | 成功结果 |
| `settings_data_failed` | 导出/导入失败:%1$s | 错误结果 |
| `settings_data_no_data` | 暂无笔记可导出 | 空状态 |
| `import_report_summary` | 导入完成:%1$d 成功 / %2$d 跳过 / %3$d 失败,详情见 import_report.md | 导入报告 |

#### Scenario: 系统语言为英文显示 TODO 占位
- **WHEN** `values-en/strings.xml` 中 `settings_data_export = "TODO(en): settings_data_export"`
- **THEN** SettingsDataScreen 按钮显示 TODO 占位,APK 构建过

#### Scenario: 中文系统语言显示权威中文
- **WHEN** 系统语言为中文
- **THEN** 按钮显示 "导出全部数据";grep 源码无硬编码

### Requirement: 测试覆盖导出 / 导入 / 去重

JUnit5 + Robolectric MUST 覆盖以下 4 个测试类:
- `NoteExporterTest`:导出后 zip File,unzip 后含 `notes.json` / `ai_history.json` / `tags.json` / `meta.json` + `notes/<id>.md` 5 个文件
- `NoteImporterTest`:import zip → Room 写入;`note.id` 已存在跳过
- `ImportReportTest`:失败条目收集,`successCount` / `skippedCount` / `failedCount` 字段正确
- `ZipHelperTest`:write/read zip round-trip 完整

#### Scenario: NoteExporterTest zip 文件结构
- **WHEN** `noteExporter.exportToJsonZip(notes = [Note(id="n1"), Note(id="n2")])`
- **THEN** unzip 后含 `notes.json`(2 元素)+ `ai_history.json` + `tags.json` + `meta.json` + `notes/n1.md` + `notes/n2.md` 6 个文件

#### Scenario: NoteImporterTest 去重
- **WHEN** Room 已有 `id="n1"`,import zip 含 `id="n1"` + `id="n2"`
- **THEN** 跳过 `n1`,导入 `n2`;`ImportReport.successCount = 1, skippedCount = 1, failedCount = 0`

#### Scenario: ZipHelperTest round-trip
- **WHEN** writeZip(entries=["a": "hello", "b.json": "{...}"]) → readZip(path)
- **THEN** `readZip` 返回的 Map 与写入一致

### Requirement: settings 包 self-containment

`feature/settings/data/` MUST 自包含,跨 feature 引用(若有)走 `feature/settings/SettingsEntry.kt` object 暴露,不允许 `feature/quicknote/**` 直接 import settings 内部(避免反向依赖)。

#### Scenario: settings 不被其他 feature 反向 import
- **WHEN** grep `feature/quicknote/**/*.kt`
- **THEN** 0 个 import `feature.settings.data.*`;`feature/aiwriting/**` 同 0

#### Scenario: AppNav 走 settings Entry
- **WHEN** `feature/quicknote/list/QuickNoteListScreen.kt` 跳 SettingsData
- **THEN** 通过 `navController.navigate(SettingsData)`(Nav 路由,非直接 import `SettingsDataScreen` Composable)— settings 内 DetailScreen 不被 detail 屏直接 import

### Requirement: data-export-import 不破坏 Note schema

本 change MUST NOT 修改 `NoteEntity` / `NoteDao` / `Note` 字段;`NoteExporter` / `NoteImporter` 读 M1/M2 既有 schema,导出字段 = 数据库字段。

#### Scenario: Note 字段保持 v2
- **WHEN** `git diff openspec/changes/data-export-import/ core/data/db/`
- **THEN** 0 个字段新增 / 0 个字段类型变更;`AppDatabase` 仍 `version = 2`

#### Scenario: 导出字段 = 数据库字段
- **WHEN** `NoteExporter.exportToJsonZip` 调 `noteRepository.observeAll().first()`
- **THEN** `notes.json` 每个元素的字段集 = `Note` data class 字段集(无缺无多)

### Requirement: Zip Slip prevention on import

`core/data/export/ZipHelper.readZip(file: File, targetDir: File)` MUST defend against Zip Slip path traversal. For each zip entry, the resolver MUST compute the destination `File` as `File(targetDir, entry.name)`, then call `.canonicalPath` and verify the result either equals `targetDir.canonicalPath` or starts with `"${targetDir.canonicalPath}/"`. If the resolved path escapes the target directory, the function MUST throw `ImportRejected(reason = "Zip entry escapes target: $entryName")` and MUST NOT extract any further entries.

#### Scenario: Legitimate entry extracts
- **WHEN** the zip contains `notes.json` and `attachments/abc.png`
- **THEN** both extract into `targetDir/notes.json` and `targetDir/attachments/abc.png`

#### Scenario: Traversal entry rejected
- **WHEN** the zip contains an entry named `../../../etc/passwd` or `..\\..\\evil.exe`
- **THEN** `readZip` throws `ImportRejected` and zero bytes are written outside `targetDir`

#### Scenario: Absolute path entry rejected
- **WHEN** the zip contains an entry named `/etc/passwd`
- **THEN** `readZip` throws `ImportRejected` (absolute paths never extract)

#### Scenario: Symbolic link style entry rejected
- **WHEN** the zip contains an entry whose name resolves through `..` segments to escape `targetDir`
- **THEN** `readZip` throws `ImportRejected` regardless of platform path separator

## ADDED Requirements (last-import-report-save)

### Requirement: SettingsDataScreen exposes "Save import report" button

`SettingsDataViewModel` MUST 暴露 `val lastImportReportZipBytes: ByteArray?`(public getter),数据源是 `NoteImporter.importFromZip()` 闭循环写回的 zip bytes(含 `import_report.md`)。`ByteArray` 在 ViewModel cleared 后 GC,**不**持久化。

`SettingsDataViewModel` MUST 暴露 `fun saveImportReport(uri: Uri)`:SAF `Uri` 写入 `lastImportReportZipBytes`(走 `viewModelScope.launch { withContext(ioDispatcher) { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } } });**当 `lastImportReportZipBytes == null` 时 MUST no-op**(不抛错,不弹失败提示)。失败路径走 catch + Snackbar(独立 `lastSaveReportResult: StateFlow<SaveReportResult>`),**不**覆盖当前 `DataUiState`(避免 Done 态被覆盖,丢用户上下文)。

`SettingsDataScreen` Done 分支在 `isImport == true` 时 MUST 渲染 "保存导入报告" 按钮(`OutlinedButton`,文案 `R.string.settings_data_save_report`)+ SAF `rememberLauncherForActivityResult(CreateDocument("application/zip"))` launcher 触发 VM `saveImportReport(uri)`;按钮 `enabled` = `lastImportReportZipBytes != null`;按钮 disabled 时 MUST 下方显示 `R.string.settings_data_no_report` 提示文案。Done 分支在 `isImport == false`(导出结果)时 MUST **不** 渲染该按钮(导出无报告 zip 可保存)。

`saveReportLauncher.launch(...)` 默认文件名 MUST 是 `import-report-{SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())}.zip`(与 M4-3 既有 `writing-with-ai-export-{ts}.zip` 命名风格对齐)。

`SettingsDataScreen` MUST 监听 `viewModel.lastSaveReportResult`,Success → 显示 Snackbar `R.string.settings_data_report_saved`;Failed(reason) → 显示 Snackbar `R.string.settings_data_save_failed`(新 key)+ 自动 reset。

#### Scenario: 导入完成 Done 分支显示保存按钮

- **WHEN** `DataUiState = Done(report, isImport = true)` + `lastImportReportZipBytes != null`
- **THEN** `SettingsDataScreen` Done 分支除既有 summaryText + failedCount 文案外,**下方** 渲染 "保存导入报告" OutlinedButton(`enabled = true`);点按钮 → `saveReportLauncher.launch("import-report-{ts}.zip")` → 用户选位置后回调 `viewModel.saveImportReport(uri)`

#### Scenario: 导出完成 Done 分支不显示保存按钮

- **WHEN** `DataUiState = Done(report, isImport = false)`(M4-3 导出路径)
- **THEN** `SettingsDataScreen` Done 分支**不** 渲染 "保存导入报告" 按钮(导出无报告 zip);仅显示既有 summaryText + failedCount 文案

#### Scenario: 缓存 null 时按钮置灰 + 提示

- **WHEN** `DataUiState = Done(report, isImport = true)` 但 `viewModel.lastImportReportZipBytes == null`(ViewModel state 被重组 / 进程恢复场景)
- **THEN** "保存导入报告" 按钮 `enabled = false` + 下方显示 "暂无可保存的报告"(`R.string.settings_data_no_report`)提示文案;按钮点击无反应

#### Scenario: 点击保存写入 bytes 到 SAF URI

- **WHEN** 用户点 "保存导入报告" → SAF 选位置 → 回调 `viewModel.saveImportReport(uri)`
- **THEN** `context.contentResolver.openOutputStream(uri).write(lastImportReportZipBytes)` 完成;zip 内含 `import_report.md` + 原始 `notes.json` / `ai_history.json` / `tags.json` / `meta.json` + `notes/` 子目录;`lastSaveReportResult = Success` → 屏显示 Snackbar `R.string.settings_data_report_saved`("报告已保存到所选位置") + 2.5s 自动消失

#### Scenario: 缓存 null 时 saveImportReport 是 no-op

- **WHEN** UI 异常调用 `viewModel.saveImportReport(uri)` 但 `lastImportReportZipBytes == null`(防 ConcurrentModification)
- **THEN** 方法立即 return,不调 `openOutputStream`,不写 bytes,不更新 `lastSaveReportResult`(`null` = 无反馈)

#### Scenario: SAF 写入失败不破坏 Done 状态

- **WHEN** `saveImportReport(uri)` 调用中 `contentResolver.openOutputStream(uri)` 抛 `FileNotFoundException`(SAF URI 失效 / 权限撤销)
- **THEN** catch 分支更新 `lastSaveReportResult = Failed(reason = "...")`;`DataUiState` **不** 切换(仍 `Done(report, isImport=true)`);屏显示 Snackbar `R.string.settings_data_save_failed`;Done 分支既有 summaryText 不变

