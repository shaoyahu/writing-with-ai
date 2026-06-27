# entity-extraction-polish Code Review R1

**日期**: 2026-06-27
**范围**: `openspec/changes/archive/2026-06-26-entity-extraction-polish/` 全量产出
**触发**: AI 自审(按 CLAUDE.md 「完成 change 后停下 + review」约定)
**方法**: 单 review agent 全量扫 18 个 main 文件改动 + 3 个 spec 合入 + 4 个新 Robolectric test
**基线**: HEAD = `c10aef7` (R2 fix + 飞书归档),working tree 含本次 change

## 修复总览

6 项 deferred polish 全清:

| # | 项 | 状态 |
|---|---|---|
| §1 重命名 | LlmNoteLinkExtractor → SemanticNoteLinker(9 处) | ✅ |
| §2 SQL 阈值参数化 | DAO + NoteLinkCap + DEFAULT_THRESHOLD=0.10 | ✅ |
| §3 pauseBackfill 双 guard | Worker doWork 起跑 + Scheduler + scheduleEntityBackfillNow(force) | ✅ |
| §4 设置页 + i18n | Screen + VM + 15 key 双语 + 一次性迁移 banner | ✅ |
| §5 路由 | AppNav route `note_association_settings` + SettingsScreen 入口 | ✅ |
| §6 测试 | 3 个 Robolectric DAO test(12 case) + EntityBackfillWorker ext(3 case) + NoteLinkCap ext(2 case) | ✅ |

## 验证结果

- `./gradlew :app:check` BUILD SUCCESSFUL
  - `ktlintCheck` 0 violations(autofix 后手改 1 处)
  - `testDebugUnitTest` 340 tests pass(含 12 个新 Robolectric DAO test,首次运行下载 Robolectric 引擎)
  - `lintDebug` 0 errors(顺手修了 SseParser BOM 字符 regression)
- 主 spec sync 完毕:`note-association-settings` (NEW) + `note-entity-link` (3 MODIFIED + 1 REMOVED + 2 ADDED) + `note-entity-extraction` (2 ADDED)
- archive 完成:`/opsx:archive --skip-specs`(主 spec 已在 sync 阶段手工 cp,archive validator 不重复 apply)

## Findings

### CRITICAL:0
### HIGH:0
### MEDIUM:0
### LOW:2

#### LOW-1 · ktlint auto-fix may drift on next run
- `EntityBackfillWorker.kt` + `NoteAssociationSettingsScreen.kt` 等 5 个文件被 `ktlintFormat` 重排过(imports + 多行格式)
- 单行内容不变,可读性提升,无行为影响
- 后续 change 引入新代码时 ktlint 可能再次调整多行格式,属正常

#### LOW-2 · pauseBackfill Worker self-check 编译期非强约束
- `EntityBackfillWorker.doWork` 第一行查 pause,但没有专门 contract test 验证「调用 shouldRun」
- 现有 `EntityBackfillWorkerTest.shouldRun_guard_*` 覆盖了 companion fun,但不直接验证 doWork 第一行调用 shouldRun
- 影响:若将来重构 doWork 把 shouldRun 移到下面,test 不会失败
- 修法:加 `doWork_callsShouldRunBeforeExtractingNotes` test,用 mock EntityExtractor 验证 paused=true 时 extractAndPersist 0 次调用

## 收口

本 change 在 roadmap §13 M6 polish 闭环内,无 follow-up。下一阶段候选:
- R4 review(扫 polish 后代码 + a11y + 当前 dead code)
- 起 v1 内测 change(用户真机 walkthrough)
- 给 note-association-settings Screen 写 Compose UI test(目前无)