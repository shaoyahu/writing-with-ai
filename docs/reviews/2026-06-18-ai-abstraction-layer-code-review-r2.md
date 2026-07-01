# code-review · ai-abstraction-layer · r2

**Date:** 2026-06-18
**Subject:** `ai-abstraction-layer`(M2 AI 抽象层) — r2 review:验 r1 全部修复
**Review type:** code-review(r2,focused on fixes only)
**Basis:** `docs/reviews/2026-06-18-ai-abstraction-layer-code-review-r1.md`

---

## 总结

**r1 全部 HIGH + MEDIUM 修复通过。** 无新引入 bug。

| 评判 | 数量 |
| --- | --- |
| PASS | 8 |
| FAIL | 0 |

| 验收 | 结果 |
| --- | --- |
| `assembleDebug` | ✅ BUILD SUCCESSFUL |
| `testDebugUnitTest` | ✅ 15 tests pass |
| `lintDebug` | ✅ BUILD SUCCESSFUL |
| `ktlintCheck` | ⚠️ 11 个 `function-naming` = 已知 Compose PascalCase,0 新增 |

---

## 逐项验证

### H1 — Migration 索引名 `idx_` → `index_` ✅ PASS

`AppDatabase.kt` MIGRATION_1_2 已改:`index_ai_history_noteId` / `index_ai_history_createdAt`，与 Room 自动生成的 schema 2.json 一致。

### H2 — 三个 preset 各自独立 adapter ✅ PASS

`AiModule.kt` 重写:三家 preset 各由 `@Provides @Named("deepseek|minimax|mimo")` 创建独立 `AnthropicCompatibleAdapter` 实例，配置来自 `DeepseekConfig.config` / `MinimaxConfig.config` / `MimoConfig.config`。`listProviders()` 现在返回 4 个 distinct 条目。

### H3 — 删除 `CoreAiGateway.noteRepo` ✅ PASS

`noteRepo` 参数已移除，import 已清理。

### H4 — `listProviders()` 用 `provider.supportedModels` ✅ PASS

`AiProvider` 接口新增 `val supportedModels: List<String>`,`FakeAiProvider` / `AnthropicCompatibleAdapter` 各自实现。`CoreAiGateway.listProviders()` 用 `.distinctBy { it.id }` + `provider.supportedModels`。

### M1 — `PollishPrompt` → `PolishPrompt` ✅ PASS

文件已重命名，类名已改，`AnthropicCompatibleAdapter` 的 import + 引用全部更新。

### M2 — SseParser 超时检查死代码 ✅ PASS

`SseParser.kt` 重写:移除了 `lastLineAt` / `TimeUnit.SECONDS.toMillis(30)` 超时检查(由 OkHttp readTimeout 处理)。

### M3 — `FakeAiProvider` tokenCounts 判断 ✅ PASS

`FakeConfig.tokenCounts` 改为 `AiStreamEvent.Usage?`(nullable),`FakeAiProvider` 用 `cfg.tokenCounts != null` 判断，不再误拒绝合法的零值 token 配置。

### M4 — `SseEvent.Error.t` → `cause` ✅ PASS

属性已重命名，`AnthropicCompatibleAdapter` 的引用 `sse.cause.message` 已更新。

---

## 额外清理

| 项 | 说明 |
| --- | --- |
| L1 | `CoreAiGateway.completed` 变量已移除 |
| L2 | `AnthropicCompatibleAdapter.outputBuilder` 死代码已移除 |
| L3 | `SseParser.DONE_MARKER` byte array 已移除 |
