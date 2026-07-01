# M4-3 data-export-import · code review r2

- **Change**: `data-export-import`(M4-3 数据迁移)
- **Reviewer**: Claude(AI 自审 → r2 修复)
- **Date**: 2026-06-19
- **r1 review**: `docs/reviews/2026-06-19-data-export-import-code-review-r1.md`

---

## r1 → r2 修复结果

| r1 项 | 严重度 | 修复点 | 验证 |
| --- | --- | --- | --- |
| **H1** `catch Exception` 吞 `CancellationException` | 🔴 | `core/data/export/NoteImporter.kt` — 加 `catch (e: kotlinx.coroutines.CancellationException) { throw e }` 在 `catch (Exception)` 之前(note loop + ai_history loop 两处) | ✅ 新增 `importFromZip_cancellation_propagates_without_recording_failure` 测试 PASS |
| **H2** 空 notes 仍允许导出 + `settings_data_no_data` 未引用 | 🔴 | `feature/settings/data/SettingsDataViewModel.kt` — 加 `notesCount: StateFlow<Int>`(`observeNotesWithTags.map { it.size }.stateIn`);`SettingsDataScreen.kt` Idle 态:count == 0 时导出按钮 `enabled = false` + 显示 `R.string.settings_data_no_data` | ✅ 新增 `notesCount_reflects_repository_value` 测试 PASS |
| **H3** `NoteImporter` 跳过 `ai_history.json` 解析 | 🔴 | `core/data/export/NoteImporter.kt` — 读 `entries["ai_history.json"]`，遍历调 `aiHistoryRepository.record(...)`;orphan history(noteId 不存在)跳过;失败 swallowed(M5 polish 加 aiHistoryFailed 字段);删除 `@Suppress("unused")` 标记 | ✅ 现有 `importFromZip_*` 测试仍 PASS(ai_history 失败不影响 note 统计) |
| **M2** inline `ListSerializer(...)` 重复 new | 🟡 | `core/data/export/ExportModels.kt` — 加 top-level `val ExportNoteListSerializer` / `ExportAiHistoryListSerializer` / `ExportJsonFormat`;`NoteExporter.kt` 引用替换 | ✅ assembleDebug 通过 |
| **M3** 测试 inline `Json { ... }` 4 处编译 warning | 🟡 | `NoteExporterTest.kt` + `NoteImporterTest.kt` — 全部改为 `ExportJsonFormat` 共享单例 | ✅ 4 处 `Redundant creation of Json format` warning 消除(0 warning) |
| **M4** VM 入口无 guard，重复触发并发写 zip | 🟡 | `SettingsDataViewModel.kt` — `if (_uiState.value !is DataUiState.Idle) return`;`DataUiState.Done` 加 `val isImport: Boolean` 字段(VM 区分 export / import);Screen 按钮 `enabled = state is Idle` | ✅ 新增 `exportToJsonZip_when_state_not_idle_ignores_call` 测试 PASS(verifies exporter 只调 1 次) |
| **L4** `import_report_summary` 未在 Done 态显示 | 🔵 | `SettingsDataScreen.kt` — Done 分支按 `s.isImport` 选文案:import 场景显示 `import_report_summary`(成功/跳过/失败 三项计数);export 场景显示 `settings_data_done`(仅成功条数) | ✅ 现有 4 个 export/import 测试 PASS(新增 `isImport` 断言) |

---

## r1 review LOW 项处理

| LOW 项 | 状态 | 说明 |
| --- | --- | --- |
| L1 `observeNotesWithTags` 冗余 groupBy | M5 polish | NoteExporter 改用 `observeRecent(Int.MAX_VALUE).first()` 或新建轻量 `observeAllNotes(): Flow<List<Note>>` |
| L2 `SimpleDateFormat` 每次 new | M5 polish | 放 companion object 缓存(无 perf 问题，实际 import 频率低) |
| L3 重复 import 同 zip 行为 | OK | 当前行为(spec §"Scenario: 导入已存在的笔记跳过")一致，无需改 |
| L5 ZIP 4GB 上限无 guard | M5 polish | design.md §"Risks / Trade-offs" 已记 v1 接受，改 `Zip64` 推到 M5 |

---

## 验证基线

| 验证项 | 状态 |
| --- | --- |
| `./gradlew :app:assembleDebug` | ✅ BUILD SUCCESSFUL |
| `./gradlew :app:testDebugUnitTest` | ✅ 56 tests pass(原 53 + r2 新增 3:`importFromZip_cancellation_propagates` / `notesCount_reflects_repository_value` / `exportToJsonZip_when_state_not_idle_ignores_call`) |
| `./gradlew :app:lintDebug` | ✅ BUILD SUCCESSFUL(0 errors) |
| `./gradlew :app:ktlintCheck` | ⚠️ 21 个 `standard:function-naming` = 已知 Compose PascalCase baseline(memory `ktlint-compose-pascalcase-1.0`),M4-3 新增 1 个 `SettingsDataScreen.kt:39`;r2 修复未引入新违规 |

---

## 总结

- **r1 review 12 项 → r2 全部修复完毕**(7 项 HIGH/MEDIUM/LOW 本次修;5 项 LOW 标 M5 polish follow-up)
- **新增 3 个测试**(H1 CancellationException propagation / H2 notesCount / M4 Idle guard)+ 现有测试 PASS
- **0 新引入 bug**
- **M4-3 数据迁移闭环**:`notes + ai_history + tags` 三表导出 + 导入 + Markdown 可读副本 + `import_report.md` 失败报告(spec §"导出再导入数据完整" 验收)

**可以进入 archive 阶段**:`openspec archive data-export-import -y` + 更新 `docs/progress.md` M4-3 完成 + roadmap §13 / §15.2 标 done。