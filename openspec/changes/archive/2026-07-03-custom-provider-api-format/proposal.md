## Why

Custom Provider 表单当前硬绑 Anthropic 协议(`buildConfig` 强制 `apiFormat = ANTHROPIC` + `endpointPath = ""` → 直用 baseUrl)，导致用户接入**任意兼容 Anthropic / OpenAI 协议的三方 provider** 时需要按 Anthropic SDK 的方式拼 `${base_url}/v1/messages`(`https://api.deepseek.com/anthropic` → POST `https://api.deepseek.com/anthropic/v1/messages`)。表单只暴露"完整 URL"语义 → 用户填 `https://api.deepseek.com/anthropic` 缺 `/v1/messages` 后缀，POST 路径不完整 → 404。

实机验证(2026-06-30):用户按 DeepSeek 官方文档填 base URL `https://api.deepseek.com/anthropic`，协议选 Anthropic 兼容，测试连通 → POST `https://api.deepseek.com/anthropic`(缺 `/v1/messages`) → DeepSeek 端无此路径 → 404。

需求:**baseUrl = 厂家文档给的 base URL**(如 `https://api.deepseek.com/anthropic` 或 `https://api.minimaxi.com/anthropic` 或 `https://api.deepseek.com`);**path 由协议固定** — Anthropic 兼容协议固定 path = `/v1/messages`(Anthropic SDK 设计),OpenAI 兼容协议固定 path = `/chat/completions`(OpenAI SDK 设计)。表单让用户选协议，**adapter 按协议拼 path**(`$baseUrl${path}`)，用户**不**直接看到 path 字段 — 简化配置 + 跟 Anthropic / OpenAI SDK 行为一致。

## What Changes

- `CustomProviderEditUiState` 新增字段:`apiFormat: ApiFormat`(默认 `ANTHROPIC`，与旧行为一致)
- `CustomProviderEditScreen` 「连接信息」section 新增「协议」下拉:`Anthropic 兼容` / `OpenAI 兼容`，选中后 helper 文案动态切换 — helper 只描述协议特征 + 给出**该协议对应的 base URL 示例**(Anthropic: `https://api.deepseek.com/anthropic`;OpenAI: `https://api.deepseek.com`)，不强调 path(由协议固定)
- `CustomProviderEditViewModel.buildConfig` 不再硬绑 `ANTHROPIC`，改用 state 中的 `apiFormat`;`endpointPath` 由 `apiFormat` 自动决定(Anthropic → `/v1/messages`,OpenAI → `/chat/completions`)，不再传 `""`
- helper 文案拆为两个 string resource:
  - `custom_provider_helper_anthropic`:Anthropic Messages API 协议说明 + base URL 示例(`https://api.deepseek.com/anthropic` / `https://api.minimaxi.com/anthropic` 等)
  - `custom_provider_helper_openai`:OpenAI Chat Completions 协议说明 + base URL 示例(`https://api.deepseek.com` / `https://api.moonshot.cn/v1` 等)
- `pingFromForm` 不传 `apiFormatOverride`,adapter 内部走 `effectiveApiFormat = config.apiFormat`(已存在逻辑)
- **不**新增 `endpointPath` UI 字段 — path 由协议下拉自动决定，用户看不到

**BREAKING**:无(旧 config JSON 缺 `apiFormat` → Kotlinx Serialization 走 `ProviderConfig.apiFormat = ApiFormat.ANTHROPIC` 默认值，行为与硬绑旧版完全一致)。

## Capabilities

### New Capabilities

- `custom-provider-config`:用户自建 Provider 的配置 UI / 协议选择 / baseUrl + 自动 path 拼接能力

### Modified Capabilities

- (无):`ai-gateway` spec 已隐含 `apiFormat` 切换 + `endpointPath` 拼接能力(见 `MinimaxConfig.kt` / `MimoConfig.kt` / `AnthropicCompatibleAdapter.kt`)，本次 change 仅在 UI 层让 custom 表单走与内置 provider 一致的协议表达。无 requirement 级别变化，不改 delta。

## Impact

- UI:`feature/settings/model/CustomProviderEditScreen.kt`(新增 `ApiFormatDropdown`)、`CustomProviderEditViewModel.kt`(`UiState` + `buildConfig`)
- 持久化:`core/ai/provider/ProviderConfig.kt` 不变(`apiFormat` 已有 `= ApiFormat.ANTHROPIC` 默认值);`CustomProviderStore.kt` 不变
- 文案:`app/src/main/res/values/strings.xml`(helper 拆两个 + helper 给出 base URL 示例而非 path)
- 测试:`CustomProviderEditViewModelTest` 新建文件，加 case:
  - `buildConfig` 选 ANTHROPIC → 返回 `ProviderConfig.apiFormat = ANTHROPIC, endpointPath = "/v1/messages"`
  - `buildConfig` 选 OPENAI → 返回 `apiFormat = OPENAI, endpointPath = "/chat/completions"`
  - `loadExisting` 加载 OPENAI 配置 → state `apiFormat = OPENAI`
  - `save` 走的 config 含 OPENAI + path(apiFormat + endpointPath 通过 buildConfig 透传到 store.save)
- 协议层:`AnthropicCompatibleAdapter.kt` 无改动(`if (config.endpointPath.isBlank()) baseUrl else "$baseUrl$path"` 已支持)
- Roadmap:`docs/plans/writing-with-ai-mobile-roadmap.md` §15.1 不需更新