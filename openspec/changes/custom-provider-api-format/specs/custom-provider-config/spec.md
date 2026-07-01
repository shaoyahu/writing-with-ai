## ADDED Requirements

### Requirement: Custom provider 表单必须允许用户选择 API 协议

Custom Provider 编辑表单 MUST 暴露「协议」下拉选项，枚举值为 `OpenAI 兼容` 和 `Anthropic 兼容`，作为表单的显式输入字段。该字段决定 `ProviderConfig.apiFormat` + `ProviderConfig.endpointPath`，不再由表单层硬编码。

#### Scenario: 表单显示协议下拉
- **WHEN** 用户进入「编辑自定义模型」页
- **THEN** 「连接信息」section 内，「完整 URL」输入框下方 MUST 显示「协议」下拉，默认选中 `Anthropic 兼容`(与旧行为一致)

#### Scenario: 用户切换协议
- **WHEN** 用户在下拉中选中 `OpenAI 兼容`
- **THEN** 表单 state `apiFormat` 更新为 `ApiFormat.OPENAI`,helper 文案切换为 OpenAI 描述 + OpenAI 协议的 base URL 示例(`https://api.deepseek.com` / `https://api.moonshot.cn/v1` 等)

#### Scenario: 协议选择决定 adapter 调用
- **WHEN** 用户点击「测试连通」且 `apiFormat = OPENAI`
- **THEN** adapter 走 OpenAI Chat Completions 协议(body `{"model": "...", "messages": [...], "stream": true}`),URL 拼到 `${baseUrl}/chat/completions`

### Requirement: 旧 custom provider 配置必须按 Anthropic 协议回退

`ProviderConfig.apiFormat` MUST 提供默认值 `ApiFormat.ANTHROPIC`，旧 DataStore JSON 缺该字段时自动 fallback,Kotlinx Serialization 反序列化时缺省使用 Kotlin 默认值，行为与硬绑旧版完全一致。

#### Scenario: 旧 JSON 缺 apiFormat 字段加载成功
- **WHEN** DataStore JSON 中某条 config 缺 `apiFormat` 字段
- **THEN** 反序列化 MUST 成功，`apiFormat = ApiFormat.ANTHROPIC`(默认值)

#### Scenario: 新 JSON 含 OPENAI apiFormat 往返一致
- **WHEN** 表单保存一条 `apiFormat = OPENAI` 的 custom provider
- **THEN** 重新加载后 MUST 还原为相同的 `apiFormat`

### Requirement: Custom provider 表单 baseUrl 字段语义是「厂家文档的 base URL」，endpointPath 由协议下拉自动决定

Custom Provider 表单的 baseUrl 输入框 MUST 接收用户从厂家文档复制的 base URL(可能含 `/anthropic` 子路径、可能含 `/v1`、可能纯域名)。viewModel 根据 state 中的 `apiFormat` 自动决定 `endpointPath`:
- `ANTHROPIC` → `endpointPath = "/v1/messages"`
- `OPENAI` → `endpointPath = "/chat/completions"`

adapter 调用 URL MUST 是 `"$baseUrl${endpointPath}"`(`$baseUrl$path` 拼接)。用户**不**直接看到 `endpointPath` 字段。

#### Scenario: 选 Anthropic + baseUrl = DeepSeek base → 拼 `/v1/messages`
- **WHEN** 用户选 `Anthropic 兼容`，填 baseUrl = `https://api.deepseek.com/anthropic`
- **THEN** adapter POST URL = `https://api.deepseek.com/anthropic/v1/messages`

#### Scenario: 选 OpenAI + baseUrl = DeepSeek base → 拼 `/chat/completions`
- **WHEN** 用户选 `OpenAI 兼容`，填 baseUrl = `https://api.deepseek.com`
- **THEN** adapter POST URL = `https://api.deepseek.com/chat/completions`

#### Scenario: baseUrl 末尾带斜杠被剥离
- **WHEN** 用户填 baseUrl = `https://api.deepseek.com/anthropic/`(末尾带 `/`)
- **THEN** adapter POST URL = `https://api.deepseek.com/anthropic/v1/messages`(`/` 被剥，避免双斜杠)

### Requirement: ping 跟真实 stream 走同一协议

表单内「测试连通」按钮 MUST 走与正式 stream 相同的 `apiFormat`，通过 viewModel state 内的 `apiFormat` 字段保证一致性，不允许 ping 跟 stream 用不同协议。

#### Scenario: ping 用 OpenAI 协议
- **WHEN** 用户选 `OpenAI 兼容`，填 baseUrl + apikey + 模型，点击「测试连通」
- **THEN** OkHttp POST body MUST 是 OpenAI Chat Completions 格式(`{"model": "...", "messages": [...], "stream": true}`),URL = `${baseUrl}/chat/completions`

#### Scenario: ping 用 Anthropic 协议
- **WHEN** 用户选 `Anthropic 兼容`，填 baseUrl + apikey + 模型，点击「测试连通」
- **THEN** OkHttp POST body MUST 是 Anthropic Messages API 格式(`{"model": "...", "system": "...", "messages": [...], "stream": true}`),URL = `${baseUrl}/v1/messages`

### Requirement: 协议切换后 helper 文案动态更新，展示 base URL 示例 + body / SSE 格式描述

helper 文字 MUST 按当前 `apiFormat` 动态切换 OpenAI / Anthropic 描述，文案分别走独立 string resource(`custom_provider_helper_openai` / `custom_provider_helper_anthropic`)，描述 body 字段名 + SSE 事件名 + 该协议对应的 base URL 示例(让用户对号入座 — DeepSeek / Minimax / Moonshot 等)。**不**出现 `/v1/messages` / `/chat/completions` 字面 path 提示(path 由协议自动拼，无需用户关注)。

#### Scenario: 选 OpenAI 时显示 OpenAI 描述 + base URL 示例
- **WHEN** 表单 state `apiFormat = OPENAI`
- **THEN** baseUrl 输入框下方的 helper 文案 MUST 描述 OpenAI Chat Completions 协议(body `messages` 数组 + SSE `data: {choices: [...]}`)+ OpenAI 协议 base URL 示例(`https://api.deepseek.com`、`https://api.moonshot.cn/v1` 等)

#### Scenario: 选 Anthropic 时显示 Anthropic 描述 + base URL 示例
- **WHEN** 表单 state `apiFormat = ANTHROPIC`
- **THEN** helper 文案 MUST 描述 Anthropic Messages API 协议(body `system` + `messages` 分离 + SSE `content_block_delta`)+ Anthropic 协议 base URL 示例(`https://api.deepseek.com/anthropic`、`https://api.minimaxi.com/anthropic` 等)