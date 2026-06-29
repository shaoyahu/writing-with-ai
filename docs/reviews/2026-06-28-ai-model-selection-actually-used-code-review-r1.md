# Code Review: ai-model-selection-actually-used

**Reviewed**: 2026-06-28
**Reviewer**: Claude (caveman mode, full)
**Change**: `openspec/changes/ai-model-selection-actually-used/`
**Decision**: REQUEST CHANGES

## Summary

修「选 deepseek pro 实际用 flash」核心路径正确:
- `CoreAiGateway.streamWritingOp` 改走 `provider.defaultModel`(§1.1)
- `ProviderDescriptor` 透出 `defaultModel` 给 UI 卡片显式提示(§3.1-3.4)
- `ModelManagementViewModel` 启动懒加载 + save 流程自举 `setSelectedModelIfMissing` 保证「apikey 已落 → 必有 selectedModel」不变式(§2.4-2.5)
- `AiActionViewModel.start()` 走 `resolveActualModel` 算 actualModel + 透传 modelName(§3.5)

3 HIGH 需修,2 MEDIUM 建议修,3 LOW 锦上添花。

## 验证基线

| Check | Result |
|---|---|
| `./gradlew :app:assembleDebug` | Pass |
| `./gradlew :app:ktlintCheck` | Pass |
| `./gradlew :app:testDebugUnitTest` | 74 suites / 0 failures / 0 errors / 403 cases |

## Findings

### HIGH

**H1 · `defaultModel` 命名仍把 flash/lite 当默认体验**

| 位置 | 值 |
|---|---|
| `core/ai/provider/deepseek/DeepseekConfig.kt:18` | `"deepseek-v4-flash"` |
| `core/ai/provider/mimo/MimoConfig.kt:14` | `"mimo-v2.5-flash"` |
| `core/ai/provider/minimax/MinimaxConfig.kt:13` | `"MiniMax-M2.7-highspeed"` |

新版 gateway fallback 已治本,但 defaultModel 自身仍是 flash/lite 命名。新装用户走 init 块自举时**自动**选 flash = 原 bug 行为重现。UI 卡片显示「实际将调用: deepseek-v4-flash」治标,但「无 apikey 用户」看到的还是 flash。

建议:
- defaultModel 改名(`entrypointModel` / `fallbackModel` / `recommendedModel`),语义直白
- 或加 doc comment 明示「无 apikey 时仅作为网关可达性测试,不作为推荐体验」,避免后人回归「fallback = 默认体验」

**H2 · `ModelManagementViewModel.init` builtin id 列表硬编码 2 次**

```kotlin
// 行 92  observeSelectedModel combine
val builtinIds = listOf("deepseek", "minimax", "mimo")
// 行 113  启动自举扫存量
val builtinIds = listOf("deepseek", "minimax", "mimo")
```

加新 builtin provider 必须改两处,缺一 init 块漏拉。

建议:
- 提到 companion object 顶层 `private val BUILTIN_PROVIDER_IDS = ...`
- 或更好 — 走 `aiGateway.listProviders().map { it.id }` 单源(builtin 来自 CoreAiGateway,custom 来自 CustomProviderStore),删硬编码

**H3 · 跨 change 数据准确性 — MimoConfig supportedModels 列表**

| 文件 | 内容 |
|---|---|
| `core/ai/provider/mimo/MimoConfig.kt:17-22` | `["mimo-v2.5-flash", "mimo-v2.5-pro", "mimo-v2.5-mini"]` |
| `core/ai/provider/minimax/MinimaxConfig.kt:18-26` | 8 个 M2.x / M3 model |

本 change 在两个 config 改了 supportedModels 列表,未触及 `docs/usage/api-mimo.md` / `api-minimax.md`(若有)。UI 卡片「X 个模型」展示与实际 provider 协议必须一致。

建议:同步 review 协议文档,确认列表真实可调。

### MEDIUM

**M1 · `AiActionViewModel.start()` 每次调 `aiGateway.listProviders()`**

`feature/aiwriting/streaming/AiActionViewModel.kt:150-153`

`listProviders()` 内部会触发 customProviderStore.getAll()(潜在 Room query)。AI 调用频率低可接受,但应记录在 design.md 备查,或 cache 到 VM 内(首次 init 拿一次)。

**M2 · `combine { pairs.toMap().filterValues { it != null }.mapValues { it.value!! } }` 双重断言**

`ModelManagementViewModel.kt:101-103`

`mapValues { it.value!! }` 已在 filterValues 过滤 null 后安全;但任何 flow 抛错会导致 combine 整体 cancel,所有 provider selectedModel 订阅丢失,UI 不会自动恢复。

建议:在 `observeSelectedModel(id).map { id to it }` 包 `catch { emit(null) }`,单 provider 错不影响其他。

**M3 · `ProviderDescriptor.defaultModel` 必填无 default value**

`core/ai/api/ProviderDescriptor.kt:13` `val defaultModel: String`

data class 字段 required,未来若加新 caller 忘了传就编译失败。建议 default = `""`,与 `resolveActualModel` blank 兜底对齐。

### LOW

**L1 · `Log.e("ModelMgmtVM", ...)` tag 硬编码**

`ModelManagementViewModel.kt` 多处。提取 `private const val TAG = "ModelMgmtVM"`。

**L2 · 启动懒加载扫存量 N=1 顺序执行**

`ModelManagementViewModel.kt:111-132`

N provider 串行 getSelectedModel + setSelectedModelIfMissing,DataStore.edit 串行化安全,但可用 `coroutineScope { ids.map { async { ... } } }` 并发。NIT。

**L3 · AiActionViewModel `actualModel` 在 start() 失败分支未赋值**

`AiActionViewModel.kt:148` 之前 `return@launch` 路径不会进入 Streaming state 构造。`resolveActualModel` 纯函数不抛。无问题,记录以备查。

## Files Reviewed

| 文件 | 改动 |
|---|---|
| `core/ai/CoreAiGateway.kt` | §1.1 fallback 改 defaultModel + ping 一致 |
| `core/ai/api/ProviderDescriptor.kt` | §3.1 加 defaultModel 字段 |
| `core/ai/api/ModelSelection.kt` | 新增 resolveActualModel |
| `core/ai/provider/ProviderPrefsStore.kt` | §2.1 setSelectedModelIfMissing |
| `core/ai/provider/deepseek/DeepseekConfig.kt` | 已有 defaultModel |
| `core/ai/provider/mimo/MimoConfig.kt` | §3.1 defaultModel + supportedModels 扩 |
| `core/ai/provider/minimax/MinimaxConfig.kt` | 同上 |
| `feature/settings/model/ModelManagementViewModel.kt` | §2.2-2.5, §3.2, §2.6 |
| `feature/settings/model/ModelManagementScreen.kt` | §3.4 「实际将调用」卡片 |
| `feature/settings/model/ModelProviderDetailScreen.kt` | §2.6 + import 排序 |
| `feature/aiwriting/streaming/AiActionViewModel.kt` | §3.5 actualModel 透传 |
| `feature/aiwriting/streaming/AiActionUiState.kt` | §3.5 Streaming + actualModel 字段 |
| `res/values/strings.xml` + `values-en/strings.xml` | §4 3 个 key 双语 |
| `core/ai/provider/FakeProviderPrefsStore.kt` | §5.3 setSelectedModelError + IfMissing |
| `core/ai/CoreAiGatewayR3RegressionTest.kt` | §5.1/5.2 fallback + DefaultModelProvider |
| `feature/settings/model/ModelManagementViewModelTest.kt` | §5.3/5.4 2 用例 |
| `feature/aiwriting/streaming/AiActionViewModelTest.kt` | §6 fixture drift 7-mock |
| `feature/aiwriting/streaming/AiActionViewModelGenerationTest.kt` | §6 fixture drift + listProviders stub |

## Next Steps

1. **H1** — 决策 defaultModel 命名/语义(可选改名,或加 doc 注释)
2. **H2** — 提取 builtin provider id 常量或单源
3. **H3** — 同步 review `docs/usage/api-mimo.md` / `api-minimax.md`
4. **M1-M3** — 合并到后续 polish
5. 真机验证 §6.4(USER-OWNED)

---

# Review r2 · 收口(2026-06-29)

**Reviewer**: Claude (caveman mode, full)
**Decision**: **APPROVE**

## r1 → r2 fix 落地

| Finding | 决策 | 落地位置 |
|---|---|---|
| **H1** `defaultModel` flash/lite | 字面量改 pro/balanced + KDoc 显式 fallback 语义 | `core/ai/provider/deepseek/DeepseekConfig.kt` `defaultModel="deepseek-v4-pro"`;`mimo/MimoConfig.kt` `defaultModel="mimo-v2.5-pro"`;`minimax/MinimaxConfig.kt` `defaultModel="MiniMax-M2.7"`;`core/ai/api/AiProvider.kt` KDoc 重写,显式「`ModelManagementViewModel` 在用户新装 + 配置 apikey 后会用 defaultModel 引导值预写 selected_model,保证「apikey 已落 → 必有 selectedModel」不变式;defaultModel 应当选与该 provider 默认体验一致的模型」 |
| **H2** builtin id 散落 | 提 `BUILTIN_PROVIDER_IDS` companion object 常量,2 处复用 | `feature/settings/model/ModelManagementViewModel.kt` 行 57 `val BUILTIN_PROVIDER_IDS = listOf("deepseek", "minimax", "mimo")`,init 块懒加载 + saveProvider 自举均引用 |
| **H3** 协议文档漂移 | 3 docs 同步 defaultModel + ProviderConfig 代码块 | `docs/usage/api-deepseek.md` §3 + §8 改 `deepseek-v4-pro`;`api-mimo.md` §3 + §8 改 `mimo-v2.5-pro`;`api-minimax.md` §3 + §8 改 `MiniMax-M2.7` |
| **M1** AiActionViewModel provider cache | `defaultModelsByProvider` MutableStateFlow + init block eager populate | `feature/aiwriting/streaming/AiActionViewModel.kt` 行 76-90 init 块 + 行 111 cache + 行 173-185 cache 优先 + fallback inline |
| **M2** combine 抛错 | 每个 observeSelectedModel 加 `.catch { emit(id to null) }` | `feature/settings/model/ModelManagementViewModel.kt` 行 119-133 combine lambda |
| **M3** ProviderDescriptor default | `defaultModel: String = ""` | `core/ai/api/ProviderDescriptor.kt` 行 17 |
| **L1** TAG 散落 | 提 `TAG = "ModelMgmtVM"` 常量,替换 5 处 | `feature/settings/model/ModelManagementViewModel.kt` 行 60 + 5 处 `Log.e/Log.w` |
| **L2** 启动自举串行 | `coroutineScope { allIds.map { async { ... } }.awaitAll() }` 并发 | `feature/settings/model/ModelManagementViewModel.kt` 行 147-185 |
| **Fixture drift** | H1 改 defaultModel 字面量导致 `ModelManagementViewModelTest.saveProvider_firstTime_autoInitsSelectedModelToDefault` 期望值漂移,同步修 | `feature/settings/model/ModelManagementViewModelTest.kt` 行 126-129 期望从 `deepseek-v4-flash` → `deepseek-v4-pro`,加注释说明 review-r2 H1 fix 来源 |

## r2 验证基线

| Check | Result |
|---|---|
| `./gradlew :app:assembleDebug` | Pass |
| `./gradlew :app:ktlintCheck` | Pass |
| `./gradlew :app:testDebugUnitTest` | 74 suites / 0 failures / 0 errors / 403 cases(6 skipped) |

## 落地统计

- 9 项 review finding 全修(H1/H2/H3/M1/M2/M3/L1/L2 + fixture drift 连带)
- 4 个 provider config 字面量改动
- 3 个协议文档同步
- 2 个 VM 加 cache/并发(共 +24 行,+11 行 helper)
- 1 个 KDoc 重写
- 0 个新 file
- 0 个新依赖

## 后续(USER-OWNED)

- 真机端到端 §6.4(用户报告;USER-OWNED 行为)
- `ai-model-selection-actually-used` change 可走 `/opsx:archive`

