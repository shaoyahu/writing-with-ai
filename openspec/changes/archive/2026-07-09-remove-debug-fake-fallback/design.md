## Context

debug 包当前在「无真实 AI provider apikey」时静默 fallback 到 `FakeAiProvider`,返回固定文本 "Fake AI response for testing"。用户 2026-07-08 反馈并拍板:**debug 包也走真 AI provider** —— debug 与 release 行为一致(无 apikey → 弹「请先配置 AI 模型」错误,不允许 fake 兜底)。

现状(7 处兜底分支必须删):
- `core/note/entity/LlmEntityExtractor.kt:56` `?: if (DEBUG) "fake" else return 0`
- `core/note/entity/LlmEntityExtractor.kt:57` `if (providerId == "fake") ""`
- `core/ai/di/AiModule.kt:54` `if (DEBUG) FakeAiProvider() else null`
- `core/ai/di/AiModule.kt:85` `if (fake != null) put("fake", fake)`
- `feature/quicknote/detail/QuickNoteDetailViewModel.kt:653` `providers.isNotEmpty() || DEBUG`
- `core/ai/CoreAiGateway.kt:231` fake 特殊处理
- `feature/aiwriting/streaming/AiActionViewModel.kt:71` PROVIDER_ID_FAKE 常量
- `feature/settings/model/ModelManagementViewModel.kt:82,395,399` fake id 判断
- `feature/settings/model/ModelManagementScreen.kt:153-178` UI 过滤 fake

约束:
- `app/src/test/` JVM 单测**仍需** `FakeAiProvider` + `FakeConfigHolder`(`FakeConfig.set(text = ...)` 注入固定响应做单测)— **不动**
- `BuildConfig.DEBUG` 用于 logcat gate / 内部调试日志(`NoteRepository.kt:172,185,238` 等)— **不动**(跟 fake 无关)
- `ProviderPrefsStore.selectedProviderId` 默认 `null`(2026-07-03 fix-review-r3 改的)— 保持
- CLAUDE.md「AI 集成约定」段 2026-07-08 新增第 11 条 bullet 是本 change 的契约来源

## Goals / Non-Goals

**Goals:**
- 全 AI 调用路径在 debug 与 release 行为一致
- 无任何真实 provider apikey → 弹「请先配置 AI 模型」错误,UI 走 `DecomposeState.ApiKeyMissing` / `AiError.ProviderNotConfigured` / Snackbar「去设置」跳转
- `FakeAiProvider` 类保留,JVM 单测可用,main/androidTest 不注册、不调用
- 编译 + ktlint + JVM 单测全过

**Non-Goals:**
- 不删 `FakeAiProvider.kt` / `FakeConfig.kt` / `FakeConfigHolder.kt` 类本身(JVM 单测需要)
- 不动 `BuildConfig.DEBUG` 用于 logcat gate 的位置
- 不动 `ProviderPrefsStore.selectedProviderId` 默认 `null` 行为(已修)
- 不重构 AI 调用架构(只是删兜底分支,不改 provider 选择策略)
- 不实现新的"无 provider"引导 UI(已有 `DecomposeState.ApiKeyMissing` + Snackbar 走通)

## Decisions

### D1:删除 LlmEntityExtractor 的 BuildConfig.DEBUG 兜底

**当前**(LlmEntityExtractor.kt:54-57):
```kotlin
val providers = secureApiKeyStore.observeConfiguredProviders().first()
val providerId = providers.firstOrNull()
    ?: if (com.yy.writingwithai.BuildConfig.DEBUG) "fake" else return@withContext 0
val apikey = if (providerId == "fake") "" else secureApiKeyStore.get(providerId) ?: return@withContext 0
```

**改后**:
```kotlin
val providers = secureApiKeyStore.observeConfiguredProviders().first()
val providerId = providers.firstOrNull() ?: return@withContext 0
val apikey = secureApiKeyStore.get(providerId) ?: return@withContext 0
```

**理由**:`providers.firstOrNull()` 已经在 `secureApiKeyStore.observeConfiguredProviders()` 里过滤了真实 provider;若为空,调用方(`QuickNoteDetailViewModel.decompose`)已检测 + 走 ApiKeyMissing 错误分支。extractor 自身不再需要知道 "fake" 概念。

### D2:AiModule 不再注册 FakeAiProvider

**当前**(AiModule.kt:48-85):
```kotlin
fun provideFakeAiProvider(): FakeAiProvider? =
    if (com.yy.writingwithai.BuildConfig.DEBUG) FakeAiProvider() else null

@Provides @Singleton
fun provideAiProviderMap(fake: FakeAiProvider?): Map<String, AiProvider> {
    val map = buildMap {
        // ... 真实 provider map
    }
    if (fake != null) put("fake", fake)
    return map
}
```

**改后**:
```kotlin
fun provideAiProviderMap(): Map<String, AiProvider> = buildMap {
    // 仅真实 provider,无 fake
}
```

**理由**:`FakeAiProvider` 类仍保留在 `app/src/main/java/com/yy/writingwithai/core/ai/fake/`(给 JVM 单测用),但 main 的 Hilt DI 图不再注入它。`FakeAiProvider` 自身仍可被 JVM 单测 `FakeConfigHolder.set(...)` 路径访问 — 单测不走 Hilt,直接 `new FakeAiProvider()` 或通过 DI 拿到实例,本 change 不阻塞此路径。

### D3:QuickNoteDetailViewModel.decompose 删 DEBUG 兜底

**当前**(QuickNoteDetailViewModel.kt:652-654):
```kotlin
val providers = secureApiKeyStore.observeConfiguredProviders().first()
val hasProvider = providers.isNotEmpty() || com.yy.writingwithai.BuildConfig.DEBUG
if (!hasProvider) {
    _decomposeState.value = DecomposeState.ApiKeyMissing
    return@launch
}
```

**改后**:
```kotlin
val providers = secureApiKeyStore.observeConfiguredProviders().first()
if (providers.isEmpty()) {
    _decomposeState.value = DecomposeState.ApiKeyMissing
    return@launch
}
```

**理由**:`DecomposeState.ApiKeyMissing` 已有完整 UI 错误对话框走通(`R.string.ai_error_provider_not_configured` + 「去设置」按钮)。删 DEBUG 兜底后,debug 包也会走这条路径,行为对齐 release。

### D4:CoreAiGateway 删除 fake 特殊处理

**当前**(CoreAiGateway.kt:231):
```kotlin
if (provider.id == FakeAiProvider.PROVIDER_ID) {
    // 走 fake 路径
}
```

**改后**:删除整段 if,统一走真实 provider map 通用路径。`providerMap` 不再含 "fake" key,所以 `providerMap[providerId]` 找不到 → 走通用 "无 provider" 错误处理。

### D5:AiActionViewModel.PROVIDER_ID_FAKE 常量删除

**当前**(AiActionViewModel.kt:71):
```kotlin
const val PROVIDER_ID_FAKE = "fake"
```

**改后**:删除常量;调用点(line 252,254,258,303,340 的 `BuildConfig.DEBUG` log 保留 — 跟 fake 无关,不动)。

**理由**:`providerId == "fake"` 判断不再需要 — provider map 不含 fake,`secureApiKeyStore.get("fake")` 会返 null,正常 fall through。

### D6:ModelManagementViewModel / Screen 清理 fake 残留

**当前**(ModelManagementViewModel.kt:82,395,399):
```kotlin
val hasKey = selected != null && selected != FakeAiProvider.PROVIDER_ID && secureApiKeyStore.has(selected)
providerPrefsStore.setSelectedProviderId(FakeAiProvider.PROVIDER_ID)
_state.update { it.copy(selectedProviderId = FakeAiProvider.PROVIDER_ID) }
```

**改后**:删除 `selected != FakeAiProvider.PROVIDER_ID` 守卫(不再有 fake 选项可排除);删除 setSelectedProviderId(fake) 调用路径(由 `secureApiKeyStore` 空集自动走 "请先配置" 引导)。

**Screen**(ModelManagementScreen.kt:153-178):删除 `descriptors.filter { it.id != "fake" }` + `selectedProviderId != "fake"` 判断 — provider map 不含 fake,这些守卫是冗余。

### D7:CLAUDE.md 已记录,本 change 仅引用

CLAUDE.md「AI 集成约定」段 2026-07-08 新增第 11 条 bullet 已写好,内容:
- `BuildConfig.DEBUG` 不再是 fake 兜底合法豁免
- debug 与 release 行为一致(无 apikey → 弹「请先配置 AI 模型」)
- FakeAiProvider 仅 JVM 单测用

本 change 不修改 CLAUDE.md(已完成)。

## Risks / Trade-offs

- **[Risk]** JVM 单测如果通过 Hilt 拿 `FakeAiProvider` 注入到 `AiProvider` map,删 AiModule 注册后单测会失败 → **Mitigation**:`app/src/test/` 单测大多直接 `new FakeAiProvider()` 或用 `@TestInstallIn` 替换模块,**不走** main 的 AiModule `provideAiProviderMap`;改完后跑 `./gradlew :app:testDebugUnitTest` 验证,失败的单测用 `@TestInstallIn` 注入 fake
- **[Risk]** 删 fake 兜底后,首次装包无 apikey → 拆解弹错误对话框,对老用户是行为变化(原本可能没注意走 fake 默默通过) → **Mitigation**:`DecomposeState.ApiKeyMissing` 错误对话框已经有完整 UX(错误文案 + 「去设置」按钮),不构成 UX 退化;反而更明确告知"需要配置"
- **[Risk]** 真 provider 调通后,LLM 返回结构不稳定(JSON 格式漂移 / 字段缺失)→ 已有 `parseJsonEntities` 兼容 + `extractAndPersist` 返 0 兜底,与 fake 路径行为独立,不受本 change 影响
- **[Risk]** `BuildConfig.DEBUG` 全量 grep 误伤其他调试日志位置(`NoteRepository.kt:172,185,238` 等)→ **Mitigation**:tasks 明确列出"不动 logcat gate / 调试日志",grep 验证只看 "BuildConfig.DEBUG + fake" 组合,不看单 DEBUG 引用
- **[Trade-off]** 单测不再能"无 apikey 跑通整条 AI 链路"(因为 Hilt 不再注入 fake),但这是正确方向 — 单测就该 mock 掉 provider map,而不是让 fake 静默通过

## Migration Plan

1. **代码改造**(tasks.md 顺序执行,7 个 main 文件):
   - `LlmEntityExtractor.kt:54-57` 删 DEBUG 兜底
   - `AiModule.kt:48-85` 删 `provideFakeAiProvider` + fake 注入 map
   - `QuickNoteDetailViewModel.kt:653` 删 `|| DEBUG`
   - `CoreAiGateway.kt:231` 删 fake 特殊处理
   - `AiActionViewModel.kt:71` 删 `PROVIDER_ID_FAKE` 常量
   - `ModelManagementViewModel.kt:82,395,399` 删 fake id 判断
   - `ModelManagementScreen.kt:153-178` 删 fake 过滤守卫
2. **编译 + ktlint 验证**:`./gradlew :app:assembleDebug :app:ktlintCheck :app:testDebugUnitTest` 全过
3. **真机 e2e 验证**(USER-OWNED):
   - 装 debug APK → 不配 apikey → 详情屏拆解 → 期望弹「请先配置 AI 模型」错误对话框
   - 配 deepseek apikey → ping → 拆解 → 期望真 LLM 抽实体 + UI 高亮
   - 扩写 / 润色 同上
4. **grep 收尾**:`grep -rE "(BuildConfig.DEBUG.*fake|\"fake\".*BuildConfig.DEBUG)" app/src/main/` → 0 匹配

## Open Questions

无 — 用户 2026-07-08 拍板清晰,CLAUDE.md 已记录硬规则。`FakeAiProvider` 类保留 / `ProviderPrefsStore` 默认 null 等非目标项已明确。