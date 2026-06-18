# code-review · ai-abstraction-layer · r1

**Date:** 2026-06-18
**Subject:** `ai-abstraction-layer`(M2 AI 抽象层)
**Review type:** code-review(r1)
**Reviewer:** Claude(自审)— 2 个并行 reviewer(correctness + architecture)结果整合
**Change root:** `openspec/changes/archive/2026-06-18-ai-abstraction-layer/`
**Specs:** `ai-gateway`(9 Requirement)+ `ai-history`(4 Requirement)+ `quick-note`(2 modified)

---

## 总结

| 维度 | 结果 |
| --- | --- |
| Build | ✅ `assembleDebug` / `lintDebug` / `testDebugUnitTest` 15 tests 全绿 |
| 正确性 | ⚠️ 1 HIGH(crash risk)+ 3 MEDIUM |
| 架构/API | ⚠️ 3 HIGH + 4 MEDIUM |
| 整体 | **建议修 4 个 HIGH + 2 个 MEDIUM 再 r2;M2 高质量但 migration 索引名有 crash 风险** |

---

## 🔴 HIGH(必须修)

### H1. Migration 索引名与 Room schema 不匹配 — release 崩溃

**文件:** `core/data/db/AppDatabase.kt:60,64`

**问题:** MIGRATION_1_2 创建的索引名为 `idx_ai_history_noteId` / `idx_ai_history_createdAt`,但 Room v2 schema 期望 `index_ai_history_noteId` / `index_ai_history_createdAt`。Room 在启动时验证 identity hash(包含索引名),不匹配 → `IllegalStateException` crash。

**修复:**
```kotlin
"CREATE INDEX IF NOT EXISTS index_ai_history_noteId ON ai_history (noteId)"
"CREATE INDEX IF NOT EXISTS index_ai_history_createdAt ON ai_history (createdAt)"
```

### H2. 三个 preset provider 注册为同一个 adapter 实例 — `listProviders()` 重复/错误

**文件:** `core/ai/di/AiModule.kt:57-69` + `core/ai/CoreAiGateway.kt:28-36`

**问题:** `provideAiProviders()` 把同一个 `AnthropicCompatibleAdapter`(id=`"anthropic"`)注册在 `"deepseek"`/`"minimax"`/`"mimo"` 三个 key 下。结果:
- `listProviders()` 因为 `providers.values` dedup,只返回 2 个条目(fake + anthropic),不是 4 个
- `ProviderDescriptor.id` 全为 `"anthropic"`,UI 分不清三家
- `DeepseekConfig`/`MinimaxConfig`/`MimoConfig` 三份精心写的配置**完全没有进入 DI 图**

**修复:**
```kotlin
// AiModule.kt — 为每家创建独立 adapter 实例
@Provides @Singleton
fun provideDeepseekAdapter(@Named("ai") client: OkHttpClient): AnthropicCompatibleAdapter =
    AnthropicCompatibleAdapter(DeepseekConfig.config, client)

@Provides @Singleton  
fun provideMinimaxAdapter(@Named("ai") client: OkHttpClient): AnthropicCompatibleAdapter =
    AnthropicCompatibleAdapter(MinimaxConfig.config, client)

@Provides @Singleton
fun provideMimoAdapter(@Named("ai") client: OkHttpClient): AnthropicCompatibleAdapter =
    AnthropicCompatibleAdapter(MimoConfig.config, client)

@Provides @Singleton
fun provideAiProviders(
    fake: FakeAiProvider,
    deepseek: AnthropicCompatibleAdapter,
    minimax: AnthropicCompatibleAdapter,
    mimo: AnthropicCompatibleAdapter,
): Map<String, @JvmSuppressWildcards AiProvider> = mapOf(
    "fake" to fake,
    "deepseek" to deepseek,
    "minimax" to minimax,
    "mimo" to mimo,
)
```

### H3. `CoreAiGateway` 注入但从未使用 `noteRepo`

**文件:** `core/ai/CoreAiGateway.kt:26`

**问题:** 构造函数参数 `private val noteRepo: NoteRepository` 在整个类体中零引用。违反 CLAUDE.md "未使用变量是构建错误"。增加了不必要的 DI 依赖。

**修复:** 删除该参数及 import。M3 用到时再加。

### H4. `listProviders()` hardcode `"fake-model"` — 忽略 `ProviderConfig.supportedModels`

**文件:** `core/ai/CoreAiGateway.kt:33`

**问题:** `models = listOf("fake-model")` 所有 provider 返回同一模型名。`ProviderConfig.supportedModels` 完全被忽略。

**修复(配合 H2 修完):**
```kotlin
// 在 AiProvider 接口加 val supportedModels: List<String>
// 或 CoreAiGateway 维护 Map<String, ProviderConfig>
// 然后用 config.supportedModels 填充 ProviderDescriptor.models
```

---

## 🟡 MEDIUM(应该修)

### M1. `PollishPrompt` 拼写错误 → 应为 `PolishPrompt`

**文件:** `core/ai/prompt/PollishPrompt.kt`(整文件 + 类名)

**修复:** 重命名文件 `PollishPrompt.kt` → `PolishPrompt.kt`,`object PollishPrompt` → `object PolishPrompt`,更新 `AnthropicCompatibleAdapter.kt` 的 import。

### M2. `SseParser` 超时检查是死代码

**文件:** `core/ai/stream/SseParser.kt:49-52`

**问题:** `readUtf8Line()` 是阻塞调用,服务器不发数据时代码停在 line 24,永远走不到 line 50 的 `System.currentTimeMillis() - lastLineAt > 30s` 检查。实际超时由 OkHttp `readTimeout(30s)` 提供。SseParser 内的超时逻辑是死代码。

**修复:** 删除死代码(靠 OkHttp timeout 即可),或改用 `source.timeout()` 显式设置读超时。

### M3. `FakeAiProvider` 的 `totalTokens > 0` 条件拒绝合法零值配置

**文件:** `core/ai/fake/FakeAiProvider.kt:66`

**问题:** `if (cfg.tokenCounts.totalTokens > 0)` — 当 tokenCounts 为 `Usage(inputTokens=100, outputTokens=0, totalTokens=0)` 时,条件为 false,fallback 计算覆盖用户配置。

**修复:** 改用 `cfg.tokenCounts != null`(需先改 `FakeConfig.tokenCounts` 为 nullable `AiStreamEvent.Usage?`)。

### M4. `SseEvent.Error.t` 单字母属性名

**文件:** `core/ai/stream/SseEvent.kt:9`

**修复:** 重命名为 `cause` 或 `throwable`。

---

## 🟢 LOW(polish)

| # | 文件 | 问题 |
| --- | --- | --- |
| L1 | CoreAiGateway.kt:61 | `completed` 变量赋值但从不读取(死代码) |
| L2 | AnthropicCompatibleAdapter.kt:118 | `outputBuilder` 变量 append 但从不读取(死代码) |
| L3 | SseParser.kt:16 | `DONE_MARKER` byte array 未使用(死代码) |
| L4 | SseParser.kt:20 | `StringBuilder` 未预分配容量 |
| L5 | CoreAiGateway.kt:100-119 | `ping()` 只返回 Boolean,丢失错误详情 |
| L6 | CoreAiGateway.kt | 缺少 KDoc |
| L7 | DataModule.kt:34 | DEBUG `fallbackToDestructiveMigration()` 无版本限制 |
| L8 | CoreAiGateway.kt:53 | `"fake-model"` 硬编码 fallback |

---

## ✅ 验证通过项

1. **API 分层清晰** — `AiGateway`(业务入口) / `AiProvider`(SPI) / `CoreAiGateway`(实现)三层边界干净
2. **Sealed type 命名** — `AiStreamEvent` / `AiError` 子类型命名合理,语义清晰
3. **包结构** — 完全符合 CLAUDE.md §"包结构":`core/ai/{api,provider,stream,fake,prompt}`
4. **Hilt DI** — `AiModule` + `DataModule` 正确标注,无循环依赖
5. **Prompt 注入防御** — 用户文本不进 system prompt,走 `user` 消息 content
6. **错误降级** — HTTP status → AiError 映射完整
7. **internal scope** — `SseParser` / prompt templates / provider configs 均为 `internal`,正确
8. **命名规范** — UPPER_SNAKE const / PascalCase class / camelCase fun,无违反
9. **Zero unused import** — 全源集扫描无残留
10. **FakeProvider 端到端** — 3 Turbine tests pass(正常流 / 错误注入 / 空文本)

---

## 修复优先级

1. **H1**(crash risk):改 2 行 SQL → 5 min
2. **H2**(gating M3 真 provider 切换):重构 AiModule + 3 adapter 实例 → 15 min
3. **H3**(dead dep):删 1 行 → 1 min
4. **H4**(future UX):加 `AiProvider.supportedModels` → 10 min
5. **M1**(typo):rename 文件+类+import → 3 min
6. **M2**(dead code):删 SseParser 超时检查 → 2 min
