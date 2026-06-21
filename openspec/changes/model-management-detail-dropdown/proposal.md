## Why

预置 provider( `deepseek` / `minimax` / `mimo` )详情页 `ModelProviderDetailScreen` 当前把 `baseURL` 整行明文展示、并以只读文本方式呈现"协议/模型"等系统元数据,造成两个 UX 痛点:

1. **baseURL 在 v1 决策里是 provider 内部细节**(roadmap §6.3 已锁死各家端点 + 认证方式),用户不应感知;明文暴露反而暗示"可以改"。
2. **协议类型和模型**是用户在 AI 写作中最常切换的两项,但当前只以 `Text(...)` 显示默认值,无法切换 → 每次必须重新配 apikey 走 ping + 默认模型,无法满足"换模型跑同段文本"的内测反馈需求(2026-06-20 真机 walkthrough)。

本 change 在 **不动协议/数据层** 前提下,把详情页升级为可切换协议的入口,同时把 baseURL 弱化为不可改的灰色文案,符合"provider 内部细节对用户不可见"的 roadmap 原则。

## What Changes

### UI 改造(`ModelProviderDetailScreen.kt`)

- **baseURL 行弱化**:从 active `Text(...)` 改为 disabled 样式(灰字 + 非交互),**保留可见性**以便用户排错,但不可复制 / 修改
- **新增"兼容类型"下拉框**(`ApiFormatDropdown` 复用 `CustomProviderEditScreen.kt:452` 模式,加 `readOnly = true` 参数):
  - 选项:`OpenAI 兼容` / `Anthropic 兼容`(对应 `ApiFormat.OPENAI` / `ApiFormat.ANTHROPIC`)
  - 值**写死**为当前 provider 的 `ProviderConfig.apiFormat`(deepseek → OPENAI;minimax / mimo → ANTHROPIC)
  - 预置 provider **不允许改**(roadmap §6.3 协议锁定);自定义 provider(走 `CustomProviderEditScreen`)不受影响
- **新增"选择模型"下拉框**(新组件 `ModelDropdown`):
  - 选项 = `ProviderConfig.supportedModels`(从 VM 拿,避免在 UI 重复配置)
  - 默认项后缀「(默认)」便于辨识
  - 选项变更 → VM 写 `ProviderPrefsStore.setSelectedModel(providerId, model)`

### ViewModel 改造(`ModelManagementViewModel.kt`)

- `saveProvider(providerId, apiKey)` → `saveProvider(providerId, apiKey, model)`(新增可选 model 参数)
- `getProviderConfig(providerId)` 返回类型扩展:增加 `currentSelectedModel: String?`(从 prefs 读,无值则取 `defaultModel`)
- 新增 `onModelSelected(providerId, model)` → 写 prefs,**不触发 save 事件**(纯本地切换,无网络)
- **切换 provider 时自动重置**:`onModelSelected(providerId)` 不带 model 参数时,model 重置为 `defaultModel`;ProviderDetailScreen 进屏时若 VM 状态没值,自动回填默认

### 持久化(`ProviderPrefsStore.kt`)

- 新增 `selectedModel(providerId): String?` + `setSelectedModel(providerId, model)` 方法(沿用现有 DataStore,Key 前缀 `selected_model_`)
- **不**加 `apiFormat` 持久化字段(预置 provider 协议由 `ProviderConfig.apiFormat` 写死)

### strings

- 新增 keys(中英双语,zh 先写):
  - `model_provider_detail_api_format_label` = "协议类型"
  - `model_provider_detail_api_format_anthropic` = "Anthropic 兼容"
  - `model_provider_detail_api_format_openai` = "OpenAI 兼容"
  - `model_provider_detail_model_label` = "选择模型"
  - `model_provider_detail_model_default_suffix` = "(默认)"
  - `model_provider_detail_base_url_locked_hint` = "端点由 provider 预置,不可修改"

### 不改

- `AnthropicCompatibleAdapter` / `AnthropicCompatibleAdapter` 流式逻辑
- `ProviderConfig` 数据结构
- `SecureApiKeyStore` 接口
- 三家 `*Config.kt`(deepseek/minimax/mimo)
- 自定义 provider 流程(`CustomProviderEditScreen`)
- 任何 spec

## Capabilities

### New Capabilities

无。本 change 仅 UI + 持久化,不动 spec-level 行为。

### Modified Capabilities

无。`ai-actions` spec 中已覆盖的"ProviderPrefsStore.getSelectedProviderId + apikey 透传 AiGateway"语义不变;新增的 `selectedModel` 字段仅替换 spec 中 `modelName=null` 占位的来源,**不引入新 requirement**。

## Impact

| 维度 | 影响 |
| --- | --- |
| 代码 | 1 个 Composable 屏改造 + 1 个 VM 加方法 + 1 个 prefs store 加 2 方法 |
| 数据 | DataStore 新增 `selected_model_<id>` Key(per-provider);无 schema 迁移 |
| 依赖 | 无新增 |
| spec | 0 改动;`/opsx:sync` 阶段确认 |
| UI | 详情页新增 2 个下拉框,baseURL 改 disabled 样式;变更范围控制在 `feature/settings/model/` 包内 |
| 测试 | VM 单测覆盖:`onModelSelected` 写 prefs + 切换 provider 时重置;Composable 已有 Preview 扩展 |
| i18n | zh 先写 + en 同步,无 `__TODO__` |