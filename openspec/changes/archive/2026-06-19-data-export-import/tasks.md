## 1. 依赖与 build 配置

- [x] 1.1 `gradle/libs.versions.toml` 加 `documentfile = "1.0.1"` + library entry `androidx-documentfile = { module = "androidx.documentfile:documentfile", version.ref = "documentfile" }`
- [x] 1.2 `app/build.gradle.kts` 加 `implementation(libs.androidx.documentfile)`

## 2. ExportModels 数据类

- [x] 2.1 新建 `core/data/export/ExportModels.kt`(`@Serializable` data class 集):
  - `ExportNote(id, title, content, createdAt, updatedAt, isPinned, lastAiOp, lastAiAt)`(字段集 = `Note`，含 `@SerialName` 兼容旧版本)
  - `ExportAiHistory(id, noteId, providerId, model, op, inputTokens, outputTokens, totalTokens, durationMs, createdAt, inputSnapshot, outputSnapshot, truncated, error)`
  - `ExportTags(noteIdToTags: Map<String, List<String>>)`
  - `ExportMeta(exportTimestamp: String, appVersion: String, schemaVersion: String)`
  - `ImportReport(successCount: Int, skippedCount: Int, failedCount: Int, failedNotes: List<FailedNote>)`
  - `FailedNote(note: ExportNote, error: String)`

## 3. ZipHelper zip 读写

- [x] 3.1 新建 `core/data/export/ZipHelper.kt`(`@Singleton class ZipHelper`):
  - `fun writeZip(entries: Map<String, ByteArray>): File`(用 `ZipOutputStream` + `FileOutputStream`)
  - `fun readZip(file: File): Map<String, ByteArray>`(用 `ZipInputStream`)
  - JDK 内置 `java.util.zip`,**不** 引入第三方库
- [x] 3.2 JUnit5 测试 `ZipHelperTest`:
  - `writeZip(entries = mapOf("a" to "hello".toByteArray(), "b.json" to "{...}".toByteArray()))`
  - `readZip(zipFile)` 返回 Map 与写入一致
  - 嵌套目录 `notes/n1.md` 写入读取正常

## 4. NoteExporter JSON zip

- [x] 4.1 新建 `core/data/export/NoteExporter.kt`(`@Singleton class NoteExporter @Inject constructor(noteRepository, aiHistoryRepository, noteTagDao, zipHelper)`):
  - `suspend fun exportToJsonZip(notes: List<Note>): File`
  - 内部:读 `notes` + `aiHistoryRepository.getAll()` + `noteTagDao.observeAllCrossRefs().first()` → 转 `ExportNote` / `ExportAiHistory` / `ExportTags` / `ExportMeta`
  - 写入 4 JSON 文件 + `notes/<noteId>.md` Markdown 文件(每条)
- [x] 4.2 Markdown 导出:`# ${title.ifBlank { "Untitled" }}\n\n${content}`，文件名 `${noteId}.md`
- [x] 4.3 JUnit5 + Robolectric 测试 `NoteExporterTest`:
  - 导出 2 条 note → unzip 后含 `notes.json`(2 元素)+ `ai_history.json` + `tags.json` + `meta.json` + `notes/n1.md` + `notes/n2.md` 6 文件

## 5. NoteImporter zip

- [x] 5.1 新建 `core/data/export/NoteImporter.kt`(`@Singleton class NoteImporter @Inject constructor(noteRepository, aiHistoryRepository, noteTagDao, zipHelper)`):
  - `suspend fun importFromZip(file: File): ImportReport`
  - 解析 `notes.json` / `ai_history.json` / `tags.json` / `meta.json` 4 JSON 文件
  - 逐条 note 检查 `noteDao.getById(note.id)`:null → upsert(成功);非 null → skip(跳过);异常 → 收集到 ImportReport.failedNotes
  - tags 按 `tags.json[noteId]` 写 `note_tags` 表
  - ai_history 按 `noteId` 关联已存在的 note 写入(无关联跳过)
- [x] 5.2 失败条目生成 `import_report.md`(纯文本 Markdown)，含成功/跳过/失败条数 + 每条失败 note id + 错误
- [x] 5.3 JUnit5 + Robolectric 测试 `NoteImporterTest`:
  - 导入 zip,Room 已有 `n1` → 跳过 n1，导入 n2,`ImportReport.successCount=1, skippedCount=1`
  - 失败场景:Room UNIQUE 冲突 → `failedCount=1, failedNotes` 含 note id

## 6. AppNav 加 SettingsData route

- [x] 6.1 改 `app/AppNav.kt`:
  - 加 `@Serializable data object SettingsData`
  - 加 `composable<SettingsData> { SettingsDataScreen(onBack = { navController.popBackStack() }) }`

## 7. SettingsDataScreen UI

- [x] 7.1 新建 `feature/settings/data/SettingsDataScreen.kt`(`@Composable fun SettingsDataScreen(viewModel: SettingsDataViewModel = hiltViewModel())`):
  - TopAppBar 标题 "数据迁移"(R.string.settings_data_title)+ back icon
  - "导出全部数据" 按钮 → `viewModel.startExport(activityResultLauncher)`(M3 LaunchedEffect 模式)
  - "导入数据" 按钮 → `viewModel.startImport(activityResultLauncher)`
  - `Box` 内显示当前 `DataUiState`(Idle = 双按钮;Exporting = CircularProgressIndicator;Importing = 同;Done = 成功条数 + 失败详情;Failed = 错误)
- [x] 7.2 注册 SAF `rememberLauncherForActivityResult`:
  - 导出 → `ActivityResultContracts.CreateDocument("application/zip")`,callback `uri: Uri?` → 调 `viewModel.exportToJsonZip(uri)`
  - 导入 → `ActivityResultContracts.OpenDocument()`,callback → `viewModel.importFromZip(uri)`

## 8. SettingsDataViewModel

- [x] 8.1 新建 `feature/settings/data/SettingsDataViewModel.kt`(`@HiltViewModel class SettingsDataViewModel @Inject constructor(noteRepository, noteExporter, noteImporter)`):
  - `sealed interface DataUiState { Idle / Exporting / Importing / Done(report: ImportReport) / Failed(error: String) }`
  - `private val _uiState = MutableStateFlow<DataUiState>(Idle)` + `val uiState: StateFlow<DataUiState>`
  - `fun exportToJsonZip(uri: Uri)`:`viewModelScope.launch(Dispatchers.IO) { ... }` → `noteExporter.exportToJsonZip(notes)` → 写 zip via `contentResolver.openOutputStream(uri)`
  - `fun importFromZip(uri: Uri)`:`viewModelScope.launch(Dispatchers.IO) { ... }` → 读 zip via `contentResolver.openInputStream(uri)` → `noteImporter.importFromZip(file)`
- [x] 8.2 异常 catch → `DataUiState.Failed(error.message ?: "Unknown error")`
- [x] 8.3 JUnit5 测试 `SettingsDataViewModelTest`(MockK Repository + Exporter + Importer + Turbine):
  - `exportToJsonZip(uri)` 跑成功 → state 转移 Idle → Exporting → Done
  - `importFromZip(uri)` 跑失败(IOException)→ state Failed

## 9. QuickNoteListScreen overflow menu

- [x] 9.1 改 `feature/quicknote/list/QuickNoteListScreen.kt` TopAppBar `actions`:
  - 加 `IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, ...) }` + `DropdownMenu(...)` 含 "数据迁移" `DropdownMenuItem`
  - 点击后 `menuOpen = false; navController.navigate(SettingsData)`

## 10. i18n

- [x] 10.1 改 `app/src/main/res/values/strings.xml` 加 9 个 `settings_data_*` / `import_report_summary` 中文:
  - `settings_data_title` = "数据迁移"
  - `settings_data_export` = "导出全部数据"
  - `settings_data_import` = "导入数据"
  - `settings_data_exporting` = "导出中..."
  - `settings_data_importing` = "导入中..."
  - `settings_data_done` = "完成，共 %1$d 条笔记"
  - `settings_data_failed` = "导出/导入失败:%1$s"
  - `settings_data_no_data` = "暂无笔记可导出"
  - `import_report_summary` = "导入完成:%1$d 成功 / %2$d 跳过 / %3$d 失败，详情见 import_report.md"
- [x] 10.2 改 `values-en/strings.xml` 加 9 个对应英文 TODO 占位

## 11. ktlint + Compose PascalCase

- [x] 11.1 跑 `./gradlew :app:ktlintCheck` → 已知 PascalCase follow-up 之外，0 新增;新 Composable 函数 PascalCase(`SettingsDataScreen` / `NoteExporter` / `NoteImporter` / `ZipHelper` 等)

## 12. 整体验收

- [x] 12.1 `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- [x] 12.2 `./gradlew :app:testDebugUnitTest` → M1+M2+M3+M4-1+M4-2+M4-3 测试全绿
- [x] 12.3 `./gradlew :app:lintDebug` → BUILD SUCCESSFUL
- [x] 12.4 `./gradlew :app:ktlintCheck` → 0 新增违规
- [x] 12.5 手工冒烟(Pixel Launcher / AOSP 模拟器):
  - QuickNoteListScreen → TopAppBar overflow 图标 → "数据迁移" → SettingsDataScreen
  - 点 "导出全部数据" → SAF 文件选择器 → 选位置 → 显示 "导出中..." → "完成，共 N 条笔记"
  - 在文件管理器打开导出 zip → 含 `notes.json` + `ai_history.json` + `tags.json` + `meta.json` + `notes/*.md`
  - 删除本地所有笔记(模拟新设备)→ 点 "导入数据" → SAF 选之前导出的 zip → 显示 "导入中..." → "完成，共 N 条成功"
  - 再导一次(模拟重复导入)→ 全部 SKIPPED(因 id 已存在)
  - 故意导入 1 条已修改的 zip(同 id，不同 content)→ 已存在的不覆盖

## 13. OpenSpec 收尾(apply 通过 review 后做)

- [x] 13.1 review 通过后，跑 `openspec archive data-export-import -y`
- [x] 13.2 更新 `docs/progress.md`:M4-3 完成
- [x] 13.3 在 `docs/plans/writing-with-ai-mobile-roadmap.md` §13 标注 M4-3 完成;§15.2 标 `data-export-import` done