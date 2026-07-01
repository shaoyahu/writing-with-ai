# model-management Specification

## Purpose

TBD — synced from OpenSpec change `provider-real-integration`(2026-06-20)。

用户在设置 → 模型管理选 AI provider(deepseek / minimax / mimo)+ 填 apikey + 测连通;选定的 providerId 持久化 DataStore,App 重启后 `AiActionViewModel.streamWritingOp` 自动用真实 provider。

## Requirements

### Requirement: ProviderPrefsStore 持久化 selected provider id

`core/ai/provider/ProviderPrefsStore.kt` MUST 提供:

```kotlin
interface ProviderPrefsStore {
    suspend fun getSelectedProviderId(): String  // 默认 "fake"
    suspend fun setSelectedProviderId(providerId: String)
    fun observeSelectedProviderId(): Flow<String>
}
```

实现走 `androidx.datastore.preferences.core`，单 string key `selected_provider_id`，默认值 `"fake"`。

#### Scenario: 首次启动无 provider 选

- **WHEN** 用户首次启动，DataStore Preferences 为空
- **THEN** `getSelectedProviderId()` 返回 `"fake"`(平滑过渡老用户);`AiActionViewModel` 仍走 FakeProvider stub 直到用户在模型管理改

#### Scenario: 用户在模型管理选 deepseek 后重启 App

- **WHEN** 用户在 `ModelManagementScreen` 选 `deepseek` → `setSelectedProviderId("deepseek")` + 保存 apikey
- **THEN** App 重启后 `getSelectedProviderId()` 返回 `"deepseek"`;`AiActionViewModel.streamWritingOp` 走 deepseek 真实流

### Requirement: ModelManagementScreen 显示 provider 列表 + 当前选中

`feature/settings/model/ModelManagementScreen.kt` MUST 显示:
- 标题 + 当前选中 provider 名(deepseek / minimax / mimo)+ 当前 model 名
- 3 个 provider 的 Card(icon + 名称 + baseURL + 默认 model + "选择"按钮)
- 点 Card → 跳 `SettingsModelProviderDetail(id)` 填 apikey 屏
- "测试连通"按钮(已选 + apikey 已填 → 调用 `AiGateway.ping(selectedProviderId)`，显示"可用 / 失败原因 / 延迟")

#### Scenario: 用户点 deepseek Card

- **WHEN** 用户在 `ModelManagementScreen` 点 "deepseek" Card
- **THEN** navigate 到 `SettingsModelProviderDetail("deepseek")`，显示填 apikey 表单 + baseURL 提示

#### Scenario: 点 "测试连通"

- **WHEN** 用户在主屏点 "测试连通" 且已选 provider + apikey 已填
- **THEN** UI 显示 loading → 调 `AiGateway.ping(providerId)` → 成功显示"可用 · 123ms";失败显示错误码 / 网络错误

### Requirement: ModelProviderDetailScreen 填 apikey + 保存

`feature/settings/model/ModelProviderDetailScreen.kt` MUST:
- 显示 provider 名 + baseURL(只读)+ `OutlinedTextField` 填 apikey(密码 mask)
- "显示"切换按钮(走 `SecureApiKeyStore.revealApiKey` 5s 自动隐藏)
- "保存"按钮 → `SecureApiKeyStore.saveApiKey(providerId, apiKey)` + `ProviderPrefsStore.setSelectedProviderId(providerId)`
- 保存成功 toast + back

#### Scenario: 用户填 apikey + 保存

- **WHEN** 用户在 deepseek 详情页输入 apikey = "sk-abc..." + 点"保存"
- **THEN** `SecureApiKeyStore.saveApiKey("deepseek", "sk-abc...")` 加密存 EncryptedSharedPreferences + `setSelectedProviderId("deepseek")`;back 回主屏，主屏显示当前 provider = deepseek

#### Scenario: apikey 显示 5s 自动隐藏

- **WHEN** 用户点 "显示" 按钮
- **THEN** 显示明文 apikey 5s,5s 后自动 mask;CLAUDE.md §9 隐私基线

### Requirement: AiActionViewModel 拿真实 providerId

`feature/aiwriting/streaming/AiActionViewModel.kt` 构造 MUST 接 `ProviderPrefsStore`,`streamWritingOp(...)` MUST 用 `providerPrefsStore.getSelectedProviderId()`(suspend)替换 hardcode `"fake"`。

#### Scenario: 用户已选 deepseek 后润色

- **WHEN** `AiActionViewModel.start(WritingOp.POLISH, source, noteId)`
- **THEN** 读 `providerPrefsStore.getSelectedProviderId() == "deepseek"` → `streamWritingOp(..., providerId = "deepseek")` → 走 `AnthropicCompatibleAdapter` + deepseek ProviderConfig → 真 SSE 流

#### Scenario: 用户未配置(默认 fake)

- **WHEN** 用户未在模型管理设置过 → `getSelectedProviderId() == "fake"`
- **THEN** 走 FakeProvider stub(保留测试行为);UI 提示"在设置 → 模型管理配置真实 AI"

### Requirement: ProviderNotConfigured 错误通道

`core/ai/api/AiError.kt` MUST 加 `data object ProviderNotConfigured : AiError`，表示 apikey 缺失 / providerId 未选。

`AiActionUiState.Failed` 渲染时 MUST 区分 `ProviderNotConfigured` vs `UserConsentRequired`:
- `UserConsentRequired` → 跳同意页
- `ProviderNotConfigured` → toast "请先在设置 → 模型管理配置"

#### Scenario: 用户在 apikey 缺失时点 AI 操作

- **WHEN** 用户选中文本 → 点 ✨ → 选"润色"，但未配置 provider
- **THEN** `AiActionViewModel.start()` 查 `hasApiKey(selectedProviderId) == false` → emit `AiError.ProviderNotConfigured` → UI toast"请先在设置 → 模型管理配置"

### Requirement: Settings 主屏加"模型管理"入口

`feature/settings/SettingsScreen.kt` MUST 加 ListItem"AI 模型管理"，在"AI 提示词模板"前;onClick navigate `SettingsModelManagement`。

#### Scenario: overflow menu → 设置 → 模型管理

- **WHEN** 用户在 QuickNoteListScreen overflow → 设置 → 主屏
- **THEN** 主屏显示"AI 模型管理"ListItem;点 → `navController.navigate(SettingsModelManagement)`

### Requirement: AppNav 注册 2 个新路由

`AppNav.kt` MUST 注册 `@Serializable data object SettingsModelManagement` + `@Serializable data class SettingsModelProviderDetail(val providerId: String)`,`NavHost` 加 2 个 `composable<>` block,`ModelManagementEntry.ModelManagementRoute` + `ModelProviderDetailRoute`。

#### Scenario: 路由跳转

- **WHEN** `navController.navigate(SettingsModelProviderDetail("deepseek"))`
- **THEN** 进 `ModelProviderDetailRoute("deepseek")`，显示 deepseek 详情屏

### Requirement: feature/settings/model/ 自包含

`feature/settings/model/` MUST 自包含:`grep -rE "feature/quicknote|feature/aiwriting/streaming|feature/onboarding"` → 0 匹配。只允许:
- import `core/ai/api/AiGateway` / `AiError` / `ProviderConfig` / `WritingOp`(类型)
- import `core/prefs/SecureApiKeyStore`(apikey 加密存取)
- import `feature/settings/...` 同 feature 内部

#### Scenario: model 包无跨 feature import

- **WHEN** `grep -rE "feature.(quicknote|aiwriting.streaming|onboarding)" feature/settings/model/`
- **THEN** 0 匹配;`ModelManagementViewModel` 通过 Hilt 注入 `SecureApiKeyStore` + `ProviderPrefsStore` + `AiGateway`，不直接 import 其他 feature

### Requirement: 测试覆盖 ProviderPrefsStore + ViewModel

JUnit5 + Turbine MUST 覆盖:
- `ProviderPrefsStore.getSelectedProviderId` 默认 `"fake"`
- `setSelectedProviderId("deepseek")` + 重启后仍 `deepseek`
- `ModelManagementViewModel.setProvider` 调 `saveApiKey + setSelectedProviderId`
- `ModelManagementViewModel.ping()` 走 `FakeAiGateway.ping()` mock 返回 Success / Failed
- `AiActionViewModel` 测试扩 providerId 切换 case(从 "fake" → "deepseek" 时 stream 调用 provider 改)

#### Scenario: ProviderPrefsStore round-trip

- **WHEN** 调 `providerPrefsStore.setSelectedProviderId("deepseek")` 后 `getSelectedProviderId()`
- **THEN** 返回 `"deepseek"`

#### Scenario: AiActionViewModel 走真实 provider

- **WHEN** `FakeProviderPrefsStore.selectedProviderId = "deepseek"` + `FakeSecureApiKeyStore.hasApiKey("deepseek") = true`
- **THEN** `AiActionViewModel.start(POLISH, ...)` → `FakeAiGateway.streamWritingOp` 收到 `providerId = "deepseek"`

### Requirement: 真机验证 apikey 真实流式

PGU110 真机 MUST 验证:
- 填任一 provider apikey → "测试连通" 返回"可用"
- 选中文本 → ✨ → 扩写 / 润色 / 整理 → 真 SSE 流式输出(非 "FakeAIresponsefortesting" 字面)
- 接受替换正文 + 落 `ai_history`

#### Scenario: 填 deepseek apikey 后润色

- **WHEN** 用户在 deepseek 详情页填真实 apikey + 保存 → 详情页选文本 → 润色
- **THEN** 真实 SSE 流式输出(非 "FakeAIresponsefortesting"),UI 显示 markdown 实时渲染，token 计数正确

#### Scenario: 未填 apikey 点 AI 操作

- **WHEN** 用户未配置 provider，选中文本 → 润色
- **THEN** UI toast"请先在设置 → 模型管理配置"，不报错弹窗，不静默走 FakeProvider