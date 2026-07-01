# last-import-report-save · tasks

## 1. spec delta

- [x] 1.1 在 `openspec/changes/last-import-report-save/specs/data-export-import/spec.md` 写 `## ADDED Requirements` + 6 个 Scenario(导入完成显示 / 导出完成不显示 / 缓存 null 置灰 / 点击写入 / null no-op / 失败不破坏 Done)

## 2. SettingsDataViewModel 加方法

- [x] 2.1 `feature/settings/data/SettingsDataViewModel.kt`
  - `var lastImportReportZipBytes: ByteArray? = null` 改 `val`(public getter，移除 `private set`)
  - 新 sealed `SaveReportResult { Idle | Success | Failed(reason) }`
  - 新 `private val _lastSaveReportResult = MutableStateFlow<SaveReportResult>(Idle)` + `val lastSaveReportResult: StateFlow<SaveReportResult>`
  - 新 `fun saveImportReport(uri: Uri)`:`bytes = lastImportReportZipBytes ?: return` → `viewModelScope.launch { try { withContext(ioDispatcher) { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("openOutputStream returned null") }; _lastSaveReportResult.value = SaveReportResult.Success } catch (e: CancellationException) { throw e } catch (e: Exception) { _lastSaveReportResult.value = SaveReportResult.Failed(e.message ?: "unknown") } }`
  - 新 `fun resetSaveReportResult() { _lastSaveReportResult.value = SaveReportResult.Idle }`

## 3. SettingsDataScreen 加按钮 + Snackbar

- [x] 3.1 `feature/settings/data/SettingsDataScreen.kt`
  - 加 `val saveReportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? -> uri?.let { viewModel.saveImportReport(it) } }`
  - 加 `val saveResult by viewModel.lastSaveReportResult.collectAsStateWithLifecycle()`
  - 加 `val snackbarHostState = remember { SnackbarHostState() }`
  - `Scaffold` 加 `snackbarHost = { SnackbarHost(snackbarHostState) }`
  - `LaunchedEffect(saveResult)`:`when (saveResult) { Success -> snackbarHostState.showSnackbar("报告已保存到所选位置"); viewModel.resetSaveReportResult() ... Failed -> snackbarHostState.showSnackbar("保存失败:..."); viewModel.resetSaveReportResult() }`
  - Done 分支:`if (s.isImport) { val canSave = viewModel.lastImportReportZipBytes != null; Spacer(Modifier.height(12.dp)); OutlinedButton(enabled = canSave, onClick = { saveReportLauncher.launch("import-report-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())}.zip") }) { Text(stringResource(R.string.settings_data_save_report)) }; if (!canSave) { Text("暂无可保存的报告", style = labelSmall) } }`

## 4. i18n

- [x] 4.1 `res/values/strings.xml` 加 4 个 key:`settings_data_save_report`(保存导入报告)/ `settings_data_report_saved`(报告已保存到所选位置)/ `settings_data_no_report`(暂无可保存的报告)/ `settings_data_save_failed`(保存失败:%1$s)
- [x] 4.2 `res/values-en/strings.xml` 加 4 个 key:`TODO(en): settings_data_save_report` / `TODO(en): settings_data_report_saved` / `TODO(en): settings_data_no_report` / `TODO(en): settings_data_save_failed`(M5 polish 替换)

## 5. 测试

- [x] 5.1 `feature/settings/data/SettingsDataViewModelTest.kt` 加 3 个 test:
  - `saveImportReport_writesBytesToUri`:构造 VM 后 `importFromZip(uri)` 触发(走现有 fixture)→ `lastImportReportZipBytes` 非空 → `saveImportReport(uri)` → 断言 `contentResolver.openOutputStream` 被调 + 输出流 bytes 等于输入
  - `saveImportReport_nullBytesIsNoOp`:VM 初始 `lastImportReportZipBytes = null`(未触发 import)→ `saveImportReport(uri)` → `contentResolver.openOutputStream` 0 次调用 + `lastSaveReportResult` 仍 `Idle`
  - `saveImportReport_outputStreamFailurePreservesDoneState`:导入成功 → state `Done(isImport=true)` → `saveImportReport(uri)` 模拟 `openOutputStream` 抛 `FileNotFoundException` → `uiState` 仍 `Done` 不切 `Failed` + `lastSaveReportResult = Failed(reason)`
- [x] 5.2 跑 `./gradlew :app:testDebugUnitTest --tests "*SettingsDataViewModelTest"` 全 PASS(现有 5 case + 新 3 case = 8 case)

## 6. 验证

- [x] 6.1 `./gradlew :app:assembleDebug` BUILD SUCCESSFUL
- [x] 6.2 `./gradlew :app:ktlintCheck` 0 violations
- [x] 6.3 `./gradlew :app:lintDebug` 0 errors
- [x] 6.4 跑 `./gradlew :app:testDebugUnitTest` 全 PASS(项目全部测试 + 新 3 个)

## 7. 文档

- [x] 7.1 `docs/progress.md` 加 1 条 2026-06-21 条目