# M4-3 data-export-import · code review r1

- **Change**: `data-export-import`(M4-3 数据迁移)
- **Reviewer**: Claude(AI 自审)
- **Date**: 2026-06-19
- **范围**: 4 个生产新文件(`core/data/export/{ExportModels, ZipHelper, NoteExporter, NoteImporter}.kt` + `core/common/di/DispatcherModule.kt` + `feature/settings/data/{SettingsDataScreen, SettingsDataViewModel}.kt`)+ 4 个测试 + AppNav / QuickNoteListScreen / AndroidManifest / strings.xml 等改动

---

## 概览

| 项 | 数 |
| --- | --- |
| HIGH(强 spec 偏差 / 隐 bug) | 3 |
| MEDIUM(简化 / UX polish) | 4 |
| LOW(代码质量) | 5 |
| 验证 | ✅ `assembleDebug` / `testDebugUnitTest`(53 tests pass,M4-3 新增 13)/ `lintDebug`(0 errors)/ ⚠️ `ktlintCheck` 21 个 `standard:function-naming` = 已知 Compose PascalCase baseline(memory `ktlint-compose-pascalcase-1.0`,M4-3 新增 1 个 `SettingsDataScreen.kt:39`) |
| 已勾 tasks.md | 29/29 |
| 工作区 commit | 未 commit(按 CLAUDE.md "提交控制"约定等用户指令) |

---

## HIGH(3 项)

### H1 · `NoteImporter` catch Exception 误吞 `CancellationException`

`core/data/export/NoteImporter.kt:87-89`:

```kotlin
} catch (e: Exception) {
    failed.add(FailedNote(exportNote.id, exportNote.title, e.message ?: "unknown"))
}
```

`kotlinx.coroutines.CancellationException` extends `IllegalStateException` extends `RuntimeException` extends `Exception` — 当前 catch 块会把协程取消异常当成"导入失败"记录到 `failedNotes`,然后协程退出。

**后果**:用户从 SettingsDataScreen 退出(或系统因低内存 cancel coroutine)→ 看到一条莫名其妙的"导入失败:StandaloneCoroutine was cancelled"记录。

**建议修法**:

```kotlin
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    failed.add(FailedNote(exportNote.id, exportNote.title, e.message ?: "unknown"))
}
```

参考 M1 r1 H6 修(`withContext(NonCancellable)` 包 widget 刷新)+ M3 r1 H3 修(类似 pattern)。

### H2 · 空 notes 仍允许导出,spec 列的 `settings_data_no_data` key 没用

`feature/settings/data/SettingsDataViewModel.kt:46-58` + `feature/settings/data/SettingsDataScreen.kt:79-92`:

spec `data-export-import/spec.md` §"i18n 9 个 key" 列了 `settings_data_no_data = "暂无笔记可导出"`,但代码里这个 string 完全没被引用。Idle 态双按钮始终显示,用户空数据点导出 → `noteRepository.observeNotesWithTags(null, null).first()` 返回空 list → `NoteExporter.exportToJsonZip` 写一个空 zip(只 4 个空 JSON)→ `Done(ImportReport(successCount = 0))` → UI 显示 "完成,共 0 条笔记"。

**后果**:与 spec "暂无笔记可导出" 文案意图不符;用户看到空 zip 困惑。

**建议修法**:
- `SettingsDataViewModel` 暴露 `val notesCount: StateFlow<Int>`(从 `noteRepository.observeNotesWithTags(null, null).map { it.size }.stateIn(...).value`)
- `SettingsDataScreen` Idle 态:若 `notesCount == 0`,导出按钮置灰 + 显示 `R.string.settings_data_no_data` 文案;导入按钮仍可点(允许新设备导入)

### H3 · `NoteImporter` 的 `@Suppress("unused")` 标记 `AiHistoryRepository` 但 spec 仍要求 ai_history 同步

`core/data/export/NoteImporter.kt:36-37`:

```kotlin
@Suppress("unused") // spec 列出但当前 ai_history 导入跟随 note 关联,M5 polish 再展开
private val aiHistoryRepository: AiHistoryRepository,
```

spec `data-export-import/spec.md` §"NoteImporter imports zip via id-dedup" 场景:

> **Scenario: ai_history 同步导入**
> - **WHEN** zip `ai_history.json` 含 3 条 AiHistory
> - **THEN** `ai_history` 表插入 3 行(若 noteId 关联的 note 已存在)

当前实现完全跳过 ai_history 解析 — `NoteImporter` 调 `zipHelper.readZip(input)` 后只解析 `notes.json` / `tags.json`,**完全忽略 `ai_history.json` entry**。即使成功导入 notes,`ai_history` 表也不会写入,等于 zip 里 `ai_history.json` 数据丢失。

**后果**:完整备份还原(roadmap §5.3 "导出:notes + ai_history + tags 打包")对 ai_history 失效。

**建议修法**:在 NoteImporter 里读 `entries["ai_history.json"]`,反序列化成 `List<ExportAiHistory>`,对每条 history:
1. 检查关联 note 是否已存在(`noteRepository.getNote(history.noteId)`)
2. 存在 → 调 `aiHistoryRepository.record(...)`(复用 M2 既有 API,见 `core/data/repo/AiHistoryRepository.kt:25-59`),失败收集到 failedNotes 列表(独立分组,不混 note 失败)
3. 不存在 → 跳过(M5 polish 再处理"orphan history 恢复")

---

## MEDIUM(4 项)

### M1 · `lastImportReportZipBytes` 缓存但 UI 无入口

`feature/settings/data/SettingsDataViewModel.kt:85-87`:

```kotlin
var lastImportReportZipBytes: ByteArray? = null
    private set
```

VM 缓存了导入生成的 zip bytes(含 `import_report.md`),但 `SettingsDataScreen` Done 态没暴露"保存报告"按钮,用户根本用不上。

**建议**:M5 polish 跟 SAF `CreateDocument` 集成,Done 态追加一个 `OutlinedButton(text = "保存报告 zip")` 弹 SAF 让用户保存 lastImportReportZipBytes;**本次可以暂不修,记 M5 follow-up**。

### M2 · `NoteExporter.exportToJsonZip` 重复 inline `ListSerializer(...)`

`core/data/export/NoteExporter.kt:50-58`:

```kotlin
entries["notes.json"] =
    json.encodeToString(
        kotlinx.serialization.builtins.ListSerializer(ExportNote.serializer()),
        notes.map { it.toExport() },
    ).toByteArray(Charsets.UTF_8)
entries["ai_history.json"] =
    json.encodeToString(
        kotlinx.serialization.builtins.ListSerializer(ExportAiHistory.serializer()),
        aiHistories.map { it.toExport() },
    ).toByteArray(Charsets.UTF_8)
```

每次调都 new 一个 `ListSerializer(...)`。

**建议修法**:在 `ExportModels.kt` companion object 缓存:

```kotlin
companion object {
    val NoteListSerializer = ListSerializer(ExportNote.serializer())
    val AiHistoryListSerializer = ListSerializer(ExportAiHistory.serializer())
}
```

调用处 `json.encodeToString(ExportModels.NoteListSerializer, ...)`。**Low 简化**,M5 polish 处理也行。

### M3 · 测试有 4 处编译 warning `Redundant creation of Json format`

`NoteExporterTest.kt:120, 130` + `NoteImporterTest.kt:49, 55`:

```kotlin
Json { ignoreUnknownKeys = true }.decodeFromString(...)
```

每次 inline new `Json`。**Warning,不是 error**。建议复用 `Json` 单例或 companion object 缓存(同 M2 pattern)。

### M4 · `SettingsDataViewModel.exportToJsonZip` 没检查"已 Exporting 状态重复触发"

`feature/settings/data/SettingsDataViewModel.kt:43-57`:

当前实现没检查当前 `_uiState.value`,如果用户连点 2 次"导出全部数据"按钮(Idle → Exporting 转换期间),会启动 2 个并发 `viewModelScope.launch`,各自写 zip,后者覆盖前者。

**后果**:并发写 SAF `OutputStream` → 第二次 openOutputStream 失败 → Failed(error),或者 race。

**建议修法**:VM 入口 guard:

```kotlin
fun exportToJsonZip(uri: Uri) {
    if (_uiState.value !is DataUiState.Idle) return  // 已 Exporting/Importing/Done,忽略重复触发
    // ...
}
```

Screen 那边 Button 在 `state != Idle` 时置灰,UX 更明确。

---

## LOW(5 项)

### L1 · `NoteExporter.exportToJsonZip` 用 `observeNotesWithTags` 但只取 `.note`

`core/data/export/NoteExporter.kt:39`:

```kotlin
val notes = noteRepository.observeNotesWithTags(null, null).first().map { it.note }
```

`observeNotesWithTags` 内部把 notes 跟 tags 合并成 `List<NoteWithTags>`(M1 实现),但这里只取 `.note`,**tag groupBy 是 wasted work**。改用 `noteRepository.observeRecent(Int.MAX_VALUE).first()` 或新建一个轻量 `observeAllNotes(): Flow<List<Note>>`。

**Low 简化**,M5 polish。

### L2 · `NoteImporter.formatReport` 每次 new SimpleDateFormat

`core/data/export/NoteImporter.kt:109-110`:

```kotlin
val timestamp =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).format(Date())
```

每次 import 重新 new,实际 import 频率很低(用户手动操作),**无 perf 问题**。可放 companion object 缓存。

### L3 · `NoteImporter` 重复 import 同一 zip 不会 idempotent

设计上是"按 id 去重,已存在跳过",但 `NoteImporter` 不记录"上次 import 过的 zip hash",所以用户重复选同一 zip 导入多次都只会输出"成功 X,跳过 X"。这是预期行为(spec §"Scenario: 导入已存在的笔记跳过")。

**Low 文档化**(`formatReport` 文案加 "本次跳过 id 重复" 提示,跟当前 "跳过(id 重复)" 一致,OK)。

### L4 · `SettingsDataScreen` 没显示 `R.string.import_report_summary`

`feature/settings/data/SettingsDataScreen.kt:103-119`:

Done 态只显示 `R.string.settings_data_done`(成功条数)+ 可选失败详情,**不显示** `R.string.import_report_summary`(完整摘要:"导入完成:X 成功 / Y 跳过 / Z 失败,详情见 import_report.md")。

spec `data-export-import/spec.md` §"i18n 9 个 key" 列了 `import_report_summary`,但 UI 没用上。

**建议**:Done 态(导入场景)显示 `import_report_summary` 三项计数摘要,失败条目单独行;导出场景不显示。

### L5 · `NoteExporter.exportToJsonZip` 没检查 zip 大小

1000 条笔记 zip 大约 1MB(v1 接受),10 万条约 100MB,**超出 32-bit ZIP 4GB 上限**(Java `ZipOutputStream` 限制)。当前实现无 size guard,用户笔记量极大时会 silent failure。

**Low**:`design.md` §"Risks / Trade-offs" 已记 "[Risk] ZIP 4 GB 上限 → 32-bit ZIP 限制(ZipOutputStream 内部);普通用户笔记 < 1 MB,**v1 接受**,超 4 GB 改 `Zip64`(M5 polish)";**本次接受,记 M5 follow-up**。

---

## 总结

- **HIGH 3 项都需要修**(H1 CancellationException 是隐 bug,H2 spec 强约束,H3 ai_history 数据丢失)
- **MEDIUM 4 项**:M1 / L5 是 M5 polish follow-up(已知);M2 / M3 / M4 是代码质量,本次可一并修
- **LOW 5 项**:L1 / L2 / L4 是 polish,L3 是文档化,L5 是已知 M5 follow-up

**建议 r2 修:H1 + H2 + H3 + M2 + M3 + M4 + L4**(7 项);其余记 M5 polish。

r2 完成后预期验证:`./gradlew :app:testDebugUnitTest` 仍全绿;`./gradlew :app:assembleDebug` OK;`./gradlew :app:lintDebug` OK。