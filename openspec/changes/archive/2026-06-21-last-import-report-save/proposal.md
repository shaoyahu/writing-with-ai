## Why

M4-3 `data-export-import` 落地后，`NoteImporter.importFromZip()` 在闭循环里把 `ImportReport` 序列化成 `import_report.md` 写回 zip 副本，`SettingsDataViewModel` 缓存为 `lastImportReportZipBytes: ByteArray?`(M4-3 r2 L4 follow-up 注释明确"M5 polish 暴露'保存报告'按钮")。但 v1 落地后该缓存**只活在 ViewModel 内存**，无 UI 入口让用户把含 `import_report.md` 的 zip 副本保存到磁盘。

无入口意味着:用户导入一次 zip → VM 缓存 bytes → 退屏 → ViewModel cleared → bytes GC;若用户希望事后查看报告(失败条目 / 跳过计数)，没有持久路径。M5 polish 旧账，本 change 收口。

## What Changes

- `SettingsDataViewModel` 加 `fun saveImportReport(uri: Uri)`(SAF URI 写入 `lastImportReportZipBytes`;`null` 时 no-op)
- `SettingsDataViewModelTest` 加 `saveImportReport` 测试(写 uri → `contentResolver` 流写入断言 + null 时 no-op + Idle guard 与 export/import 互斥)
- `SettingsDataScreen` Done 分支在 `isImport == true` 时新增"保存导入报告"按钮(走 `rememberLauncherForActivityResult(CreateDocument("application/zip"))` 触发 VM `saveImportReport`);按钮置灰条件 = `lastImportReportZipBytes == null`(防覆盖)
- `res/values/strings.xml` + `values-en/strings.xml` 加 2 个 key:`settings_data_save_report`(按钮文案)+ `settings_data_report_saved`(Snackbar 反馈)
- `data-export-import` capability **MODIFIED**:加 1 个 Requirement + 3 个 Scenario(覆盖"导入完成显示保存按钮" / "点击保存走 SAF CreateDocument" / "ViewModel cleared 后按钮置灰")

**非破坏**:VM 仅加新方法 + private state 已有 public getter 暴露;Screen 在原有 Done 分支加按钮分支，不重写既有逻辑。

## Capabilities

### New Capabilities

无。M4-3 已建 `data-export-import` capability，本 change 在其内追加 Requirement。

### Modified Capabilities

- `data-export-import`:新增 Requirement "保存导入报告 zip 走 SAF CreateDocument" + 3 个 Scenario(按钮显示条件 / 点击保存 / ViewModel cleared 后置灰);VM 暴露 `lastImportReportZipBytes: ByteArray?` 已有，本 change 让 UI 真正可消费

## Impact

- 改:`feature/settings/data/SettingsDataViewModel.kt`(加 `saveImportReport` + 暴露 `lastImportReportZipBytes` getter)
- 改:`feature/settings/data/SettingsDataScreen.kt`(Done 分支加按钮 + SAF launcher)
- 改:`feature/settings/data/SettingsDataViewModelTest.kt`(新 test)
- 改:`app/src/main/res/values/strings.xml` + `values-en/strings.xml`(2 个 key 双语)
- 改:`openspec/changes/last-import-report-save/specs/data-export-import/spec.md`(delta spec)
- 0 main 改动 / 0 spec 同步外部文件 / 0 build.gradle 改动 / 0 manifest 改动
- 测试:`SettingsDataViewModelTest` 已有(Robolectric + Fake Context.contentResolver)
- i18n:values-en 占位 `TODO(en):`,M5 polish 阶段翻译