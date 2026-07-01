# data-export-import Specification (delta)

## ADDED Requirements (last-import-report-save)

### Requirement: SettingsDataScreen exposes "Save import report" button

`SettingsDataViewModel` MUST 暴露 `val lastImportReportZipBytes: ByteArray?`(public getter)，数据源是 `NoteImporter.importFromZip()` 闭循环写回的 zip bytes(含 `import_report.md`)。`ByteArray` 在 ViewModel cleared 后 GC,**不**持久化。

`SettingsDataViewModel` MUST 暴露 `fun saveImportReport(uri: Uri)`:SAF `Uri` 写入 `lastImportReportZipBytes`(走 `viewModelScope.launch { withContext(ioDispatcher) { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } } });**当 `lastImportReportZipBytes == null` 时 MUST no-op**(不抛错,不弹失败提示)。失败路径走 catch + Snackbar(独立 `lastSaveReportResult: StateFlow<SaveReportResult>`),**不**覆盖当前 `DataUiState`(避免 Done 态被覆盖,丢用户上下文)。

`SettingsDataScreen` Done 分支在 `isImport == true` 时 MUST 渲染 "保存导入报告" 按钮(`OutlinedButton`，文案 `R.string.settings_data_save_report`)+ SAF `rememberLauncherForActivityResult(CreateDocument("application/zip"))` launcher 触发 VM `saveImportReport(uri)`;按钮 `enabled` = `lastImportReportZipBytes != null`;按钮 disabled 时 MUST 下方显示 `R.string.settings_data_no_report` 提示文案。Done 分支在 `isImport == false`(导出结果)时 MUST **不** 渲染该按钮(导出无报告 zip 可保存)。

`saveReportLauncher.launch(...)` 默认文件名 MUST 是 `import-report-{SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())}.zip`(与 M4-3 既有 `writing-with-ai-export-{ts}.zip` 命名风格对齐)。

`SettingsDataScreen` MUST 监听 `viewModel.lastSaveReportResult`,Success → 显示 Snackbar `R.string.settings_data_report_saved`;Failed(reason) → 显示 Snackbar `R.string.settings_data_save_failed`(新 key)+ 自动 reset。

#### Scenario: 导入完成 Done 分支显示保存按钮

- **WHEN** `DataUiState = Done(report, isImport = true)` + `lastImportReportZipBytes != null`
- **THEN** `SettingsDataScreen` Done 分支除既有 summaryText + failedCount 文案外，**下方** 渲染 "保存导入报告" OutlinedButton(`enabled = true`);点按钮 → `saveReportLauncher.launch("import-report-{ts}.zip")` → 用户选位置后回调 `viewModel.saveImportReport(uri)`

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
- **THEN** 方法立即 return，不调 `openOutputStream`，不写 bytes，不更新 `lastSaveReportResult`(`null` = 无反馈)

#### Scenario: SAF 写入失败不破坏 Done 状态

- **WHEN** `saveImportReport(uri)` 调用中 `contentResolver.openOutputStream(uri)` 抛 `FileNotFoundException`(SAF URI 失效 / 权限撤销)
- **THEN** catch 分支更新 `lastSaveReportResult = Failed(reason = "...")`;`DataUiState` **不** 切换(仍 `Done(report, isImport=true)`);屏显示 Snackbar `R.string.settings_data_save_failed`;Done 分支既有 summaryText 不变