## Why

debug 包当前在「无真实 AI provider apikey」时会**静默 fallback 到 `FakeAiProvider`**,导致用户(包括开发者自测)在没配真 AI key 时看到的 AI 输出是 fake 固定文本("Fake AI response for testing")而非真 LLM 结果。用户 2026-07-08 拍板:**debug 包也走真 AI provider** —— debug 构建不应是「fake 兜底」的合法豁免,行为基准 = release 一致(无 apikey → 弹「请先配置 AI 模型」错误)。FakeAiProvider 仅保留给 JVM 单测用,通过 `FakeConfigHolder.set(...)` 注入固定响应。

## What Changes

- **删 debug 兜底 fake 分支**(多处必须删,逐项列出):
  - `core/note/entity/LlmEntityExtractor.kt:56` 删 `?: if (BuildConfig.DEBUG) "fake" else return@withContext 0` — 改成无 provider 直接 return 0
  - `core/note/entity/LlmEntityExtractor.kt:57` 删 `if (providerId == "fake") "" else ...` — 移除 fake id 特殊路径
  - `core/ai/di/AiModule.kt:54,85` 删 `if (BuildConfig.DEBUG) FakeAiProvider() else null` + `if (fake != null) put("fake", fake)` — main DI 图里不再注册 fake provider
  - `feature/quicknote/detail/QuickNoteDetailViewModel.kt:653` 删 `providers.isNotEmpty() || BuildConfig.DEBUG` — 改成只检查 `providers.isNotEmpty()`,无 provider 走 `ApiKeyMissing` 状态(已有 UI 错误对话框走通)
  - `core/ai/CoreAiGateway.kt:231` 删 fake 特殊处理(走真实 provider map 通用路径)
  - `feature/settings/model/ModelManagementViewModel.kt:82,395,399` 删 fake id 特殊判断 — 走 "请先配置" 引导
  - `feature/settings/model/ModelManagementScreen.kt:153-178` 删 UI 过滤 fake 的兜底(已不展示,但代码残留需清掉)
  - `feature/aiwriting/streaming/AiActionViewModel.kt:71` 删 `PROVIDER_ID_FAKE` 常量引用(全链路不再认 fake)
- **保留**:`app/src/test/` 下 JVM 单测对 `FakeAiProvider` + `FakeConfigHolder` 的引用,**不动**(单测需要它)
- **保留**:`BuildConfig.DEBUG` 用于 logcat gate / 内部调试日志(`NoteRepository.kt:172,185,238` 等)— 跟 fake 无关,本次不动
- **provider 默认值**:`ProviderPrefsStore.selectedProviderId` 默认 `null`(7-03 已修),本次保持
- **CLAUDE.md 新增硬规则**:debug 包走真 AI provider,2026-07-08 拍板(已完成,本 change 引用之)

## Capabilities

### New Capabilities

无

### Modified Capabilities

- `ai-gateway`:新增 Requirement `AI 调用不允许走 fake 兜底` —— 禁止 `BuildConfig.DEBUG` 作为「无 apikey 时回退 fake」的合法理由;debug 与 release 行为一致(无 provider apikey → 弹 ApiKeyMissing 错误),FakeAiProvider 仅 JVM 单测用
- `ai-decompose-implementation`:新增 Requirement `Entity 抽取必须使用真 AI provider` —— 删 `LlmEntityExtractor` 里的 `BuildConfig.DEBUG` 兜底分支;无 provider 直接返 0,UI 走 ApiKeyMissing 错误对话框
- `ai-actions`:新增 Requirement `AI op 调用必须使用真 AI provider` —— `AiActionViewModel` 扩写/润色/整理/摘要/翻译等 op 调用必须走真 provider,无 apikey 走标准 "请先配置" Snackbar + 跳设置;不再有 `PROVIDER_ID_FAKE` 常量

## Impact

- **代码改动文件**(预估 7 个 main 文件 + 0 test 文件):
  - `app/src/main/java/com/yy/writingwithai/core/note/entity/LlmEntityExtractor.kt`
  - `app/src/main/java/com/yy/writingwithai/core/ai/di/AiModule.kt`
  - `app/src/main/java/com/yy/writingwithai/core/ai/CoreAiGateway.kt`
  - `app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/QuickNoteDetailViewModel.kt`
  - `app/src/main/java/com/yy/writingwithai/feature/settings/model/ModelManagementViewModel.kt`
  - `app/src/main/java/com/yy/writingwithai/feature/settings/model/ModelManagementScreen.kt`
  - `app/src/main/java/com/yy/writingwithai/feature/aiwriting/streaming/AiActionViewModel.kt`
- **不动**:`FakeAiProvider.kt` / `FakeConfig.kt` / `FakeConfigHolder.kt`(保留给 JVM 单测);`app/src/test/**` 全部单测
- **新文件**:无
- **复用**:`DecomposeState.ApiKeyMissing` 状态机已有,UI 错误对话框已有(`ApiKeyMissing` 路径走 `R.string.ai_error_provider_not_configured` + 「去设置」按钮)
- **依赖**:无新增依赖,无依赖删除
- **测试**:`./gradlew :app:testDebugUnitTest` 仍全过(JVM 单测 FakeProvider 保留);真机/模拟器 e2e 验证需要配置真 provider apikey
- **i18n**:无 string key 变更(「请先配置 AI 模型」文案 `ai_error_provider_not_configured` 已存在)
- **CLAUDE.md**:「AI 集成约定」段已新增第 11 条 bullet(2026-07-08 拍板)