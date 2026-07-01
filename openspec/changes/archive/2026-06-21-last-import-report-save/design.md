## Context

M4-3 `data-export-import` 落地后，`NoteImporter.importFromZip()` 在闭循环里把 `ImportReport` 序列化成 `import_report.md` 写回 zip 副本。`SettingsDataViewModel` 已缓存为 `lastImportReportZipBytes: ByteArray?`(M4-3 r2 review L4 follow-up 注释明确"M5 polish 暴露'保存报告'按钮")，但 v1 落地时该缓存**只活在 ViewModel 内存**——无 UI 入口让用户把含 `import_report.md` 的 zip 副本保存到磁盘。

退屏 → ViewModel `onCleared` → `viewModelScope` 取消 → ByteArray GC，报告永久丢失。用户事后想看失败条目 / 跳过计数，无持久路径。

本 change 范围收口 M4-3 L4 follow-up，加 SAF CreateDocument 入口。

## Goals / Non-Goals

**Goals:**
- `SettingsDataViewModel` 加 `fun saveImportReport(uri: Uri)`(SAF URI 写入 `lastImportReportZipBytes`;`null` 时 no-op)
- 暴露 `val lastImportReportZipBytes: ByteArray?` public getter(Screen 据此置灰按钮)
- `SettingsDataScreen` Done(isImport=true) 分支加"保存导入报告"按钮 + SAF `CreateDocument("application/zip")` launcher
- Done(isImport=false) 分支不显示按钮(导出结果无报告 zip 可保存;与 progress.md 第 119 行 `lastImportReportZipBytes` 语义对齐)
- 加 2 个 i18n key(`settings_data_save_report` + `settings_data_report_saved`)
- 测试:`SettingsDataViewModelTest` 加 `saveImportReport` 用例(写 URI 字节流断言 + null no-op)

**Non-Goals:**
- 重新设计 `ImportReport` schema / 加 `aiHistoryFailed` 字段(留 `import-report-schema-v2` change)
- 重写 zip 写入路径(沿用 M4-3 已落地闭循环)
- 自动备份 / 定时保存(留 v2+)
- 缓存持久化(留 v2+;本 change 用内存缓存，ViewModel cleared 即丢)
- 同步打开 zip 内的 `import_report.md`(SAF 写入后用户自行 unzip / 用文件管理器打开)

## Decisions

### 1. VM API 形态:独立 `saveImportReport(uri)`，不混入 export/import 路径

```kotlin
fun saveImportReport(uri: Uri) {
    val bytes = lastImportReportZipBytes ?: return  // null no-op
    viewModelScope.launch {
        try {
            withContext(ioDispatcher) {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(bytes)
                } ?: error("openOutputStream returned null")
            }
            // 反馈 UI:不进入 DataUiState，屏上 Snackbar 显示 R.string.settings_data_report_saved
        } catch (e: Exception) {
            // 同 exportToJsonZip:DataUiState.Failed;但避免覆盖 Done 状态 → 用单独的 lastSaveReportResult
        }
    }
}
```

**Why:** 独立方法隔离副作用，不动 export/import 的 Idle guard。失败路径不破坏当前 Done 状态(用户已看到导入结果，再弹"保存失败"是次要反馈)。

**替代方案:** 复用 `DataUiState.Failed` — 会让 Done 态被覆盖，用户丢上下文。**选独立 lastSaveReportResult**。

### 2. Screen 按钮:Done 分支条件渲染 + 缓存非空才可点

```kotlin
is DataUiState.Done -> {
    // 既有 summaryText + failedCount 分支不变
    if (s.isImport) {
        val canSave = viewModel.lastImportReportZipBytes != null
        Spacer(Modifier.height(12.dp))
        Button(
            enabled = canSave,
            onClick = { saveReportLauncher.launch("import-report-$ts.zip") }
        ) { Text(stringResource(R.string.settings_data_save_report)) }
        if (!canSave) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.settings_data_no_report))
        }
    }
}
```

**Why:** Done 分支里 isImport 区分 export/import 已经在 L4 修里建好，本 change 沿用同一分支不重写。`canSave == false` 场景 = VM cleared 后 bytes GC(进程未死但 state 已重置)，按钮置灰 + 提示"暂无可保存报告"。

**替代方案:** 用 separate screen / dialog 二次确认 — 用户已经在 Done 态看完结果，加弹窗是干扰。**选 inline Button**。

### 3. SAF 文件名:`import-report-{timestamp}.zip`

```kotlin
saveReportLauncher.launch("import-report-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())}.zip")
```

**Why:** 与 M4-3 export 文件名 `writing-with-ai-export-$ts.zip` 命名风格对齐，前缀区分"原始导出"vs"导入报告"(M4-3 设计 §1 已有)。

**替代方案:** 默认文件名 `report.zip` — 太泛。**选 `import-report-{timestamp}.zip`**。

### 4. i18n:2 个新 key 走 R.string

| key | 中文 | 英文(TODO 占位) | 用途 |
| --- | --- | --- | --- |
| `settings_data_save_report` | 保存导入报告 | TODO(en): settings_data_save_report | 按钮文案 |
| `settings_data_report_saved` | 报告已保存到所选位置 | TODO(en): settings_data_report_saved | Snackbar 反馈 |

**Why:** 复用 M4-3 既有 `settings_data_*` 命名空间;`report_saved` 后缀区分 export/import 反馈。

### 5. 测试:Robolectric + Fake Context.contentResolver

```kotlin
@Test
fun saveImportReport_writesBytesToUri() = runTest { ... }

@Test
fun saveImportReport_nullBytesIsNoOp() = runTest { ... }

@Test
fun saveImportReport_outputStreamFailurePreservesDoneState() = runTest { ... }
```

**Why:** M4-3 测试已用 Robolectric Fake Context，本 change 沿用同一 fixture;只覆盖新增方法的 3 个分支(写入成功 / null no-op / 失败不破坏状态)。

### 6. 不缓存到 Process 内存外的位置(无 DataStore / Room)

**Why:** lastImportReportZipBytes 仅作"用户当前 Done 屏可见"的瞬时缓存，ViewModel cleared 后无价值。不引入 `DataStore<ByteArray>` 或 Room BLOB 表(M5 polish 留)。

**替代方案:** 落 Room BLOB 表 — 跨进程死恢复后仍可恢复;但需要 schema 升级 + 新 DAO + 清理逻辑，**v1 不值**，留 v2+。

## Risks / Trade-offs

- **[Risk] 进程死后缓存丢失** → 用户在 Done 态退出 App → 报告丢。Mitigation:用户应在 Done 态立即保存;UI 加 `settings_data_report_hint` 提示("建议立即保存报告，退屏后无法恢复")。
- **[Risk] 字节流写入失败吞错** → contentResolver.openOutputStream 返回 null(SAF 罕见)→ 走 catch 路径，屏显示 Snackbar 失败。Mitigation:`lastSaveReportResult: StateFlow<SaveReportResult?>` 暴露给 UI。
- **[Risk] VM cleared 后 GC 期间 UI 还显示可点** → Composable 持 `viewModel.lastImportReportZipBytes` 引用触发 recomposition;ByteArray 引用被 Composable 持一时不会 GC，直到 BackHandler 触发清栈。Mitigation:`canSave` 计算属性每次 recomposition 重新判，引用变化即置灰。
- **[Risk] Done 态多次保存会覆盖同一 zip 文件** → 用户多次点保存按钮会触发多次 SAF CreateDocument;SAF 系统层面会提示覆盖。Mitigation:无(SAF 行为，可接受)。

## Migration Plan

无 schema 变更，纯新增。无回滚成本:`git revert` 删 2 个改动文件 + 删 2 个 i18n key + revert Screen Done 分支。

## Open Questions

- 无。M4-3 既有 decisions 已完整覆盖本 change 范围(spec/done 路径已存在，本 change 只是暴露入口)。