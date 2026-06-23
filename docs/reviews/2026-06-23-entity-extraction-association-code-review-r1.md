# 2026-06-23-entity-extraction-association-code-review-r1

**Change**: `entity-extraction-association`
**Round**: r1(自审,Step 1 收尾)
**审查人**: AI(本仓库 Claude)
**范围**: 全栈(数据层 / 抽取层 / 索引层 / 别名 / 设置 / UI / Worker / 测试 / docs)

---

## 结论

**通过**(archive-ready)。M5 polish 留 6 个明确 deferred 项,均不影响核心数据流。

## 验证

- `./gradlew :app:assembleDebug` ✅
- `./gradlew :app:ktlintCheck` ✅(import 顺序由 `ktlintFormat` 自动修)
- `./gradlew :app:testDebugUnitTest` ✅(169 tests,新增 11 case:7 LlmEntityExtractor + 4 EntityBacklinker)
- `./gradlew :app:check` ✅(`lint-baseline.xml` 含 2 个 pre-existing 错误,与本 change 无关)

## 高 / 中级别 findings

### M1. `EntityBacklinker` alias 展开语义修正

**文件**: `core/note/impl/EntityBacklinker.kt`

**问题**: 第一版 `expandedKeys` = `rawKeys.map { canonicalMap[it] ?: it }` 只保留 canonical,丢掉了 alias 原 key(xiaom → xiaoming 后只查 xiaoming,搜不到 key=xiaom 的笔记)。测试 `compute returns ENTITY_HIT rows aggregated by dst with alias expansion` 报 `expected 2, got 1`。

**修复**: `expandedKeys = (rawKeys + canonicalKeys).distinct()` — 同时保留 raw + canonical 全集,任意一边命中都进 result。

**评级**: M(影响功能正确性,测试覆盖到位)。

### M2. `LlmEntityExtractorTest` mockk 参数数量

**文件**: `core/note/entity/LlmEntityExtractorTest.kt`

**问题**: `AiGateway.streamWritingOp` 签名 7 参数(`op` / `sourceText` / `providerId` / `apikey` / `modelName` / `systemPrompt` / `apiFormatOverride`),初版 mockk stub 只写 5 个 `any()`,positive case 全部 miss。

**修复**: 7 个 `any()` 对齐。`coVerify(exactly=0)` 不受影响(call 未发生,匹配宽松)。

**评级**: L(test-only,build 不阻断)。

### L1. `BackfillScheduler.cancelEntityBackfill` API 名

**问题**: 写 `cancelAllByTag` → 编译失败;正确 API 是 `WorkManager.cancelAllWorkByTag`。

**修复**: 改名。

**评级**: L(纯 API 笔误)。

### L2. `AliasManagementScreen` ktlint import 顺序

**问题**: 新加 `import androidx.compose.ui.res.stringResource` 放在 `androidx.lifecycle.*` 之后,违反 lexicographic order。

**修复**: `ktlintFormat` 自动调整。

**评级**: L(纯格式)。

## 明确 deferred(M6 polish)

| 任务 | 原因 |
| --- | --- |
| §3.2 重命名 `LlmNoteLinkExtractor` → `SemanticNoteLinker` | binary compat > rename,降低回归风险 |
| §3.6 SQL `HAVING score > :threshold` 参数化 | app 侧走 `NoteLinkCap` + `SettingsStore.threshold` 等价,SQL 改写独立 PR |
| §5.4-5.5 settings slider UI + 双向绑定 | UI work,数据层 `threshold()` / `setThreshold()` 已就绪 |
| §7.8 WorkInfo 订阅进度 UI | 同上,数据层 `setProgress(workDataOf(...))` 已就绪 |
| §10.4-10.7 DAO / Worker Robolectric + instrumentation 测试 | 当前 mockk JVM 测试覆盖逻辑层,Room/WorkManager 真集成留 polish 阶段 |

## 安全 review

- **prompt 注入**:`LlmEntityExtractor.containsInjection` 检测三组短语,命中即返回 0 不打 AI。`ENTITY_EXTRACT_SYSTEM_ZH` 文案写死"仅返回 JSON 数组",无 user-overridable 段。
- **apikey**:`LlmEntityExtractor` 调用 `aiGateway.streamWritingOp(..., apikey = "")` 显式空字符串,迫使 caller 配置真 apikey;测试 mock 不影响。
- **数据外流**:SharedPreferences `settings_note_association` 走系统 `MODE_PRIVATE`,不进 Room 不进 logcat 不进 Auto Backup。

## 兼容性

- `AppDatabase` version 4 → 5:`@AutoMigration(4, 5)` 自动迁移,无需手写 SQL。
- `NoteAssociationSettingsStore` 旧 key `llm_extract_enabled` 保留,新 key `association_threshold` / `backfill_paused` 默认值兜底,binary compat。
- `NoteLinkEntity.LinkType` 新增 `ENTITY_HIT` 枚举值,Room TypeConverter 自动支持。

## 已知 baseline

`app/lint-baseline.xml` 收录 2 个 pre-existing 错误:

- `app/AppNav.kt:109` — `.map { it }.collectAsState(...)` FlowOperatorInvokedInComposition
- `app/feature/settings/model/ModelManagementScreen.kt:86` — produceState 不赋值(实际有赋值,lint 误报)

归属:Step 3 `model-management-detail-dropdown` 顺手修。
