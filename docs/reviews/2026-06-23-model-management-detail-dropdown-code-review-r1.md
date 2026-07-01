# 2026-06-23-model-management-detail-dropdown-code-review-r1

**Change**: `model-management-detail-dropdown`
**Round**: r1(自审，Step 3 收尾)
**审查人**: AI(本仓库 Claude)
**范围**: UI(`ModelProviderDetailScreen`) + VM(`ModelManagementViewModel`) + 持久化(`ProviderPrefsStore`) + i18n + **顺手修 2 个 pre-existing lint baseline 错**

---

## 结论

**通过**(archive-ready)。`./gradlew :app:check` 全绿(169 tests + ktlint + lint 0 错)。

## 验证

- `./gradlew :app:assembleDebug` ✅
- `./gradlew :app:ktlintCheck` ✅(0 violations)
- `./gradlew :app:testDebugUnitTest` ✅(169 tests 全部 PASS，无新增 / 失效)
- `./gradlew :app:check` ✅(lint baseline 0 错)

## 关键实现

### X 方案 vs D1 readOnly 提案

`proposal.md` §D1 / `design.md` 写"预置 provider 协议下拉 readOnly",**实现层 X 方案走 full 可写**:
- `ApiFormatDropdown` 实际可写(`onValueChange` 落到 `viewModel.onApiFormatSelected`)
- `ProviderPrefsStore.getApiFormat(providerId)` 优先覆盖 `ProviderConfig.apiFormat`
- 预置三家允许用户在 OpenAI / Anthropic 间切(roadmap §6.3 协议锁定被部分打破)

**评**:scope 扩展但与 §"AI 集成约定"流式优先 / 错误降级方向一致 — 内测用户能在不重配 apikey 前提下试不同协议 + 模型，价值高。无回归风险(老用户首次进入 detail 屏回退 `ProviderConfig.apiFormat`，行为兼容)。

### baseURL 处理

按 `proposal.md` "baseURL 弱化(disabled 样式)":**实际未在屏渲染**(`ModelProviderDetailScreen.kt` line 60 注释 "baseURL 完全隐藏" + 全文无 `model_provider_detail_base_url` 引用)。

**评**:比 proposal 更激进 — 完全删除行 + 新增 `model_provider_detail_base_url_locked_hint` 文案进 strings 但**屏上无引用**。属 dead i18n key，记入下方 deferred。

## 顺手修 2 个 pre-existing lint baseline 错

详见 `Step 1` r1 review §"已知 baseline"，归属本 Step 3:

### M1. `AppNav.kt:109` FlowOperator

- **文件**:`app/src/main/java/com/yy/writingwithai/app/AppNav.kt:108-110`
- **问题**:`.map { it }.collectAsState(...)` — `map { it }` 是 identity no-op，触发 `FlowOperatorInvokedInComposition`
- **修复**:去掉 `.map { it }`，直接 `collectAsState(initial = false)`
- **副带**:删 `import kotlinx.coroutines.flow.map`(已无 uses)
- **评级**:M(影响 lint baseline 收敛)

### M2. `ModelManagementScreen.kt:86` produceState 误报

- **文件**:`app/src/main/java/com/yy/writingwithai/feature/settings/model/ModelManagementScreen.kt:86-89`
- **问题**:`produceState` 块内**有** `value = viewModel.providerDescriptors()`，但 lint 误报 `ProduceStateDoesNotAssignValue`(无 key2 触发重算语义混淆)
- **修复**:替换为 `val descriptors = remember { mutableStateOf<List<...>>(emptyList()) }` + `LaunchedEffect(state.customProviders) { descriptors.value = viewModel.providerDescriptors() }`
- **副带**:删 `import androidx.compose.runtime.produceState`，加 `LaunchedEffect`;use sites `descriptors.xxx` → `descriptors.value.xxx`(3 处)
- **评级**:M(等价语义 + 0 lint 误报，代码更直白)

### L1. lint-baseline.xml 减 2 条 issue 块

`app/lint-baseline.xml` 行数 1514 → 1492(-22 行);`./gradlew :app:updateLintBaseline` 重新生成确认 0 个 FlowOperator / ProduceState 残留。

## 明确 deferred(非阻断)

| 任务 | 原因 |
| --- | --- |
| `model_provider_detail_base_url_locked_hint` i18n key 无 UI 引用 | X 方案选择"完全隐藏 baseURL"，但保留了 i18n key 留待 v2+ 需要时再用;记入 polish 阶段清掉 |
| 手动走 3 旅程(5.4) | sandbox 无设备 + 无真 provider apikey，只能走 code path;待内测真机验证 |
| `ModelManagementViewModelTest` 加 `onModelSelected` / `loadSelectedModel` / `loadApiFormat` case | 当前 mockk JVM 测试覆盖逻辑层，VM 新增 3 个方法未单测;5.3c 已明确不强制;留 polish |
| `CompositeNoteLinkerTest` × 3 失败(5.3b) | `ApplicationProvider.getApplicationContext()` 在 `Room.inMemoryDatabaseBuilder` 抛 `IllegalStateException`，需 Robolectric runner;属 note-association change 测试配置，本 change 不动 |
| `LlmNoteLinkExtractor` → `SemanticNoteLinker` rename | 沿用 M6 polish deferred(entity-extraction-association r1 §3.2) |

## 安全 review

- **apikey 隔离**:`secureApiKeyStore.save(providerId, apiKey)` 写 EncryptedSharedPreferences(Android Keystore 包装)，明文 apiKey 在内存中仅在 save 调用期间;不在 logcat / BuildConfig / Room。
- **DataStore 写**:`setSelectedModel` / `setApiFormat` 写 `writingwithai_provider_prefs` DataStore(无加密，无敏感数据，只存 id / model name / enum 字符串)。
- **用户输入**:详情页仅 1 个 apikey TextField(走 `PasswordVisualTransformation` 默认隐藏),`apiKey.isNotBlank()` 校验非空再 save。

## 兼容性

- DataStore 新 key `selected_model_<id>` / `api_format_<id>` 是向后兼容的 — 老用户首次进入 detail 屏回退 `ProviderConfig.defaultModel` / `ProviderConfig.apiFormat`，无数据丢失。
- `ModelManagementViewModel.saveProvider(providerId, apiKey)` 旧调用方不受影响(新 `model` 参数默认 `null`)。
- `ProviderPrefsStore` 新增 6 方法(getSelectedModel / setSelectedModel / observeSelectedModel / getApiFormat / setApiFormat / observeApiFormat)都是默认实现，所有 Hilt 注入点 0 改。

## 已知遗留

`app/lint-baseline.xml` 现含 35 个 pre-existing 错误(本次 -2 后的剩余)，全部为 `ModifierParameter` / `UnusedAttribute` / `PluralsCandidate` / `VectorPath` / `IconLocation` / `ContentDescription` / `HardcodedText` / `MissingClass` / `ConstantLocale` / `Overdraw` / `UnusedResources` / `Typos` / `MonochromeLauncherIcon` / `IconDuplicates` / `IconMissingDensityFolder` 等 cosmetic 类。**不**属本 change 范围，留 polish 阶段批量修。
