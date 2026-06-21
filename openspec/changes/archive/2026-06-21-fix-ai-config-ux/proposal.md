# fix-ai-config-ux

## Why

真机 walkthrough(2026-06-20)暴露 settings/aiwriting 子模块 4 个 UX bug,均涉及 AI provider 配置流,影响 v1 内测前的"模型可用性"信任度:

- **bug 4**:API key 保存页面"无反馈 + 成功后未自动退到上一级"。现状:`ModelProviderDetailScreen` 已有 `LaunchedEffect(state.selectedProviderId, state.hasApiKeyForSelected) { ... Toast + onBack() }`(line 64-69),仅在 `selectedProviderId == providerId && hasApiKeyForSelected && apiKey.isBlank()` 时触发。**问题**:
  1. 用户首配置 + 没切 selected → 上面条件成立 → 弹 Toast + onBack,看似 OK
  2. 用户**修改**已配置的 key(选 provider 进 detail,删旧 key 输新 key,保存)→ `selectedProviderId` 未变 + `hasApiKeyForSelected` 一直是 true → `LaunchedEffect` 不重 trigger → **无 toast 无返回**
  3. 失败路径(用户清空再保存 → `enabled = apiKey.isNotBlank()` 阻止,但若 keystore 失败)→ 静默失败
- **bug 5**:AI 模型管理"无法看出哪个模型已配置 apikey"。现状:`ModelManagementScreen.ProviderInfoCard(name, baseUrl, defaultModel, isSelected, hasApiKey, onClick)` 仅当 `state.selectedProviderId == X && state.hasApiKeyForSelected` 时显示 CheckCircle(line 86/95/104)。**问题**:`hasApiKeyForSelected` 只跟踪当前 selected provider,其他 provider 的 key 状态完全未跟踪 → 即使 deepseek 已配,若当前 selected 是 mimo,deepseek 卡片也不显示 CheckCircle → 用户看不出谁已配
- **bug 6**:已配置好的 provider "无法再次点进去修改"。现状:`ProviderInfoCard.onClick = onProviderClick(id)` 跳详情。代码层**可以**再进 detail 页(因为 `onClick` 不条件),但因 bug 5 的视觉缺失,用户**不知道**该 provider 已配置 → 不敢点。点进去后:`ModelProviderDetailScreen.apiKey by remember { mutableStateOf("") }` → 输入框空白,用户误以为"没配过" → 实际是 SecureApiKeyStore.get() 不显示明文(M4-4 设计:防泄漏)。**用户痛点**:无法在 detail 页区分"新配置"vs"修改已有"
- **bug 7**:AI 提示词模板"内容为空时使用默认,但应该默认填到文本域"。现状:`PromptTemplateViewModel.init` 读 `promptTemplateStore.getForOp(op) ?: ""`,空时 drafts[op] = "" → 用户看到空白文本域。**问题**:
  1. 用户不知道默认 prompt 长什么样 → 期望"打开就看到默认内容"
  2. 用户想微调默认 prompt → 没基线可改
  3. 没有显式"保存"按钮 → 现有策略"onChange 立即写"(spec 自带)但用户无感知

四者均属于 settings/aiwriting 子模块的 UX 问题,跨方向调整 0 边界 → 同一 OpenSpec change。

## What changes

### bug 4 修复

- `ModelManagementViewModel`
  - 加 `lastSaveResult: StateFlow<SaveResult>`(`Idle` / `InProgress` / `Success` / `Failed(reason)`)
  - `saveProvider()` 改:先 `InProgress` → 写 store → 写 prefs → `Success` + 切 selected + 改 pingResult=Idle
  - `secureApiKeyStore.save()` 抛异常 → `Failed(异常类型)`(显式失败反馈)
- `ModelProviderDetailScreen`
  - 用 `lastSaveResult` 渲染底部 Snackbar(`Success` → "已保存"+ 自动 `LaunchedEffect(Success) { onBack() }` / `Failed` → "保存失败:$reason")
  - 首配置 + 修改统一逻辑(用 `Success` 单事件通道,不发重复)

### bug 5 修复

- `ModelManagementViewModel`
  - 加 `configuredProviderIds: StateFlow<Set<String>>`(每 provider 周期查 `secureApiKeyStore.has(id)`,3s 内合并)
  - 数据源:`SecureApiKeyStore` 加 `observeConfiguredProviders(): Flow<Set<String>>`(底层读 EncryptedSharedPreferences + DataStore change 监听)
- `ModelManagementScreen.ProviderInfoCard`
  - 新签名 `hasApiKey: Boolean`(从 `state.configuredProviderIds.contains(id)` 拿,不再依赖 `selectedProviderId`)
  - 卡片右侧统一显示:已配置 → `SuggestionChip("已配置", containerColor = primaryContainer)` + CheckCircle icon;未配置 → `SuggestionChip("未配置", containerColor = surfaceVariant)`(灰)
  - 选中态(isSelected)→ 卡片边框用 `primaryContainer` + `border = BorderStroke(2.dp, primary)`(突出当前生效)

### bug 6 修复

- `ModelProviderDetailScreen`
  - 进入屏时 `LaunchedEffect(providerId)` 调 `secureApiKeyStore.has(providerId)`:
    - 已配置 → 顶部 banner `SuggestionChip("已配置 · 点下方覆盖", containerColor = secondaryContainer)` + Save 按钮文案改 "保存(覆盖)"
    - 未配置 → 顶部 banner `SuggestionChip("新配置", containerColor = surfaceVariant)` + Save 按钮文案 "保存"
  - 输入框 `placeholder` 文案区分:已配置 → "输入新 key 覆盖旧的";未配置 → "输入 API Key"
  - `apiKey` 不显示旧 key(防泄漏,M4-4 设计不变);用户必须主动输新值覆盖

### bug 7 修复

- `PromptTemplateViewModel`
  - `init` 读 store 后,**若为空则 drafts[op] = DefaultPrompts.forOp(op)**(默认填入)
  - 加 `pendingSave: StateFlow<Set<WritingOp>>`(track 每个 op 是否有未保存改动)
  - 新增 `save(op)` 方法:写 store + 清 pendingSave
  - `onPromptChange(op, value)` 改:更新 drafts + **标记 pendingSave(op) = true**(不立即写)
  - `resetToDefault(op)` 改:写 store + drafts[op] = ""(走 fallback)+ 清 pendingSave
- `PromptTemplateScreen`
  - Tab 标题右侧加红点 indicator(当 `pendingSave.contains(op)`)
  - 底部 `Row` 放 "保存" `Button`(enabled = currentOp pendingSave)+ "恢复默认" `OutlinedButton`
  - 顶部文案改:"自定义 AI 操作的 system prompt;修改后点保存生效;清空走默认"
- `DefaultPrompts.forOp(op)` 不变(M3 原文);`AiActionViewModel` 调用逻辑不变(`getForOp(op) ?: DefaultPrompts.forOp(op)`)

### 不改

- `SecureApiKeyStore` interface 主体(只加 1 个新方法 `observeConfiguredProviders()`)
- `AiGateway` / `AnthropicCompatibleAdapter` / 数据层 / DB schema
- `ConsentGate` / onboarding 流

## Impact

- 影响的 spec:
  - MODIFIED `openspec/specs/ai-actions/spec.md` `Requirement: ModelManagementViewModel passes real apikey to gateway.ping` → 加 Scenario "saveProvider 失败时 UI 显示 Failed 反馈 + 不切 selected"
  - ADDED `openspec/specs/ai-actions/spec.md` `Requirement: ModelManagementViewModel exposes configuredProviderIds` → 4 Scenario
  - MODIFIED `openspec/specs/secure-prefs/spec.md` `Requirement: SecureApiKeyStore persists apikeys via EncryptedSharedPreferences` → 加 `observeConfiguredProviders(): Flow<Set<String>>` 接口 + 2 Scenario
  - MODIFIED `openspec/specs/custom-prompt-template/spec.md` `Requirement: PromptTemplateScreen provides 3-tab edit UI` → 大改:默认填入 + 显式保存 + pendingSave indicator
  - MODIFIED `openspec/specs/custom-prompt-template/spec.md` `Requirement: PromptTemplateStore persists templates via DataStore` → 加 Scenario "getForOp 返回 fallback 时,UI 默认填 DefaultPrompts.forOp"
- 不影响其他 spec
- 不引入新依赖
- feature 自包含
