## Why

M4-4 已落地同意门与 `SecureApiKeyStore`(EncryptedSharedPreferences + Tink AES256_GCM)，但 **真实 AI provider 仍只走 FakeProvider**:`providerId="fake"` 硬编码在 `AiActionViewModel` 构造里，`AnthropicCompatibleAdapter` 用 `FakeProvider` 的 stub 流(返回 `FakeAIresponsefortesting` 字面文本)。

三个预置 provider(deepseek / minimax / mimo)**全部走 Anthropic Messages API 兼容**，数据驱动 `ProviderConfig` 已就位(M2 落地);只缺:
1. **UI**:让用户选 provider + 填 apikey
2. **Provider 选择持久化**:用户选的 providerId 存 DataStore,App 重启后保持
3. **连通性测试**:填 apikey 后能测一发(发空 / 1 token 请求验 401 / network)
4. **AI 流切到真实 provider**:`AiActionViewModel` 拿用户选的 providerId(不再是 hardcode "fake")

`ui-redesign-m5-glass` 拆出去后，本 change 单独承接"模型管理 + 真实 provider 接入"——review 关注点聚焦在数据流 / 加密 / providerId 切换，不与 UI 视觉 review 互相干扰。

## What Changes

- **新 capability** `model-management`:`feature/settings/model/` 包 + 入口 / 屏 / VM / 测试
- **Settings 主屏** overflow menu 加"模型管理"入口(在"数据迁移"前)
- **2 个 Nav route**:`SettingsModelManagement`(主屏)+ `SettingsModelProviderDetail(id)`(选 provider 后填 apikey 屏)
- **Provider 配置持久化**:`ProviderPrefsStore`(DataStore Preferences，单 key `selected_provider_id`)
- **连通性测试**:`AiGateway.ping(providerId, model)` 现有或新增 M2 落地接口，UI 调它显示"可用 / 失败原因"
- **AiActionViewModel 拿真实 provider**:`streamWritingOp(...)` 用 `providerPrefsStore.selectedProviderId` 替换硬编码 "fake"
- **删 `FakeProvider` 兜底**:实测真实 provider 工作后，降级到 FakeProvider 仅在 apikey 缺失时短暂 fallback(给 onboarding 友好)

**非 BREAKING**:apikey 加密存储 / provider 选择持久化 / 测试覆盖全部走已有抽象层;未填 apikey 的用户在引导下完成。

## Capabilities

### New Capabilities

- `model-management`:provider 列表 + apikey 输入 + 连通性测试 + providerId 切换持久化 + UI 入口

### Modified Capabilities

- `quick-note`:`AiActionViewModel` 拿 `ProviderPrefsStore.selectedProviderId`(替换 hardcode "fake");设置 → 模型管理入口
- `app-shell`:`AppNav` 加 2 个 Nav route + Settings 主屏跳转
- `settings`:Settings 主屏 overflow menu 加"模型管理"项

## Impact

- **新代码**:
  - `app/src/main/java/com/yy/writingwithai/feature/settings/model/`(新包) —— `ModelManagementEntry.kt` + `ModelManagementScreen.kt` + `ModelManagementViewModel.kt`
  - `app/src/main/java/com/yy/writingwithai/core/ai/provider/` —— `ProviderPrefsStore.kt`(DataStore 包装)
  - `feature/aiwriting/streaming/AiActionViewModel.kt` —— 构造接 `ProviderPrefsStore`,`streamWritingOp` 走真实 providerId
- **依赖**:`core/ai/api/AiGateway.ping` 需存在(M2 落地);如不存在，本 change 加最小 ping API
- **复用**:`SecureApiKeyStore`(M4-4)+ `ProviderConfig`(M2)+ `AnthropicCompatibleAdapter`(M2)+ `ConsentGate`(M4-4)— 0 改动
- **测试**:新增 `ProviderPrefsStoreTest`(DataStore round-trip)+ `ModelManagementViewModelTest`(apikey 加密存 / 连通性测试 mock)+ `AiActionViewModel` 测试扩 providerId 切换 case
- **真机**:PGU110 上填三家任一 provider apikey → 选文本 → 扩写/润色/整理 → 真流式输出 → 接受替换正文 → 落 `ai_history`