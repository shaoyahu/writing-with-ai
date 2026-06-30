## Context

当前 custom provider 表单在 `CustomProviderEditViewModel.buildConfig`(`feature/settings/model/CustomProviderEditViewModel.kt:287`)硬绑:

```kotlin
apiFormat = ApiFormat.ANTHROPIC
endpointPath = ""  // → adapter 直用 baseUrl
```

实机验证(2026-06-30):用户按 DeepSeek 官方文档填 base URL `https://api.deepseek.com/anthropic`,协议选 Anthropic 兼容,测试连通 → POST `https://api.deepseek.com/anthropic`(缺 `/v1/messages`) → DeepSeek 端无此路径 → 404。

DeepSeek 官方文档(`api-docs.deepseek.com/guides/anthropic_api`)明确:Anthropic 兼容 base URL = `https://api.deepseek.com/anthropic`,Anthropic SDK 调用时拼 `${base_url}/v1/messages`,完整请求地址 = `https://api.deepseek.com/anthropic/v1/messages`。OpenAI 兼容 base URL = `https://api.deepseek.com`,OpenAI SDK 拼 `${base_url}/chat/completions`。

**结论**:Anthropic / OpenAI SDK 设计就是"base URL + 协议固定 path":
- Anthropic Messages API:固定 path `/v1/messages`
- OpenAI Chat Completions:固定 path `/chat/completions`

用户填 base URL 即可,path 由 SDK 按协议自动拼。本次 change 让 custom provider 表单走相同设计 — `endpointPath` 由协议下拉决定(用户不可见),adapter 按协议拼 `$baseUrl${path}`。

底层 `AnthropicCompatibleAdapter.kt:74-82` 已支持:
```kotlin
val url = if (config.endpointPath.isBlank()) {
    baseUrl
} else {
    val path = when (effectiveApiFormat) {
        ApiFormat.OPENAI -> "/chat/completions"
        ApiFormat.ANTHROPIC -> "/v1/messages"
    }
    "$baseUrl$path"
}
```

只是当前 `buildConfig` 传 `endpointPath = ""`,adapter 走"直用 baseUrl"分支,导致 DeepSeek 这种"base URL + `/v1/messages`"的厂家配置无法工作。

底层 `ProviderConfig`(`core/ai/provider/ProviderConfig.kt:13-24`)已声明 `apiFormat: ApiFormat = ApiFormat.ANTHROPIC`(默认兼容旧 JSON),`endpointPath: String` 也已存在(`MinimaxConfig` / `MimoConfig` 等内置 provider 用)。

## Goals / Non-Goals

**Goals:**
- custom provider 表单暴露「协议」选择(OpenAI 兼容 / Anthropic 兼容)— 决定 body / SSE 协议 + endpointPath
- baseUrl 字段语义是「厂家文档给的 base URL」(不含 path)
- `endpointPath` 由协议下拉自动决定 — Anthropic → `/v1/messages`,OpenAI → `/chat/completions`(用户**不**直接看到)
- 旧 custom provider 配置(已存 DataStore 的 JSON)加载时不报错、不丢字段、不改变行为
- helper 文案按当前所选协议动态展示 base URL 示例(不强调 path,因为 path 由协议固定)
- 测试连通 / 真实 stream 走同一协议

**Non-Goals:**
- 不让用户手动填 `endpointPath`(协议固定,无需用户输入)
- 不为 DeepSeek / Moonshot 等单家 provider 写内置 config(custom 入口已能覆盖)
- 不改 `AnthropicCompatibleAdapter`
- 不改 `ProviderConfig` 已有字段语义
- 不引入新的 protocol 类型(只暴露 OpenAI / Anthropic 二选一)

## Decisions

### 1. 「协议」放 section 1(连接信息),紧贴 baseUrl

- **Why**:协议决定 endpointPath + body / SSE 格式,跟 baseUrl 是同一决策维度。放 baseUrl 输入框下方让用户填 URL 时就看到协议约束,降低误配。
- **考虑过**:放 section 2 跟「认证方式」并列 — 但认证方式是 header 层,协议是 body + path 层,两者正交;放一起模糊协议决策。

### 2. endpointPath 由协议下拉决定,不出现在 UI

- **Why**:Anthropic / OpenAI SDK 设计就是 path 固定(`/v1/messages` / `/chat/completions`),用户填 base URL 即可,SDK 自动拼 path。让用户填 endpointPath 是过度暴露协议细节,易引入误填(用户可能填成 `/messages` 或 `/api/messages` 等)。
- **考虑过**:让 endpointPath 成为可填字段 — 已否定,跟 SDK 设计不符 + 跟用户实际使用模式不符(从厂家文档复制 base URL,不复制 path)。

### 3. baseUrl = 厂家文档的 base URL(不含 path)

- **Why**:DeepSeek / Minimax / Moonshot 等厂家文档都按"base URL"形式给(`ANTHROPIC_BASE_URL=https://api.deepseek.com/anthropic`),path 是 SDK 按协议固定拼的。用户从文档复制 base URL 即可,无需自己拼完整地址。
- **helper 引导**:helper 文案给出 base URL 示例,不是 path 示例 — `Anthropic: https://api.deepseek.com/anthropic`(用户填这个即可,SDK 拼 `/v1/messages`);`OpenAI: https://api.deepseek.com`(SDK 拼 `/chat/completions`)。

### 4. 旧 JSON 按 Anthropic 协议回退,不破坏已存配置

- **Why**:`ProviderConfig.apiFormat` 已有 `= ApiFormat.ANTHROPIC` 默认值(Kotlinx Serialization 支持反序列化时使用 Kotlin 默认值)。旧 JSON 缺 `apiFormat` 字段自动 fallback 到 ANTHROPIC。
- **注意**:旧 custom provider 表单 endpointPath 是 `""`(走"直用 baseUrl"分支),本次 change 改成"按协议拼 path"。**旧 JSON 加载后行为会变** — 之前填的"完整 URL"(如 `https://api.deepseek.com/anthropic/v1/messages`)现在会变成 `https://api.deepseek.com/anthropic/v1/messages/v1/messages` ← 错。但实际上,旧 custom provider 都是按"完整 URL"形式存的,加上 Anthropic `/v1/messages` 路径会重复。需要在迁移时检测旧数据并剥离 `/v1/messages` 后缀,或提示用户重新编辑。

### 5. `pingFromForm` 不传 `apiFormatOverride`,让 ping 跟真实 stream 走同一协议

- **Why**:state 里有真实 apiFormat,viewModel 内部用 state.apiFormat 即可,ping / stream 协议一致性靠"都从同一个 state 读"保证,不需要 override 机制。

### 6. helper 文案按协议切换,展示 base URL 示例 + body / SSE 格式描述

- **Why**:path 由协议固定,无需在 helper 强调;helper 重点描述:
  1. 该协议的 body / SSE 格式(`Anthropic: system + messages 分离 + SSE content_block_delta` / `OpenAI: messages 数组 + SSE data: {...}`)
  2. 该协议的 base URL 示例(让用户对号入座 — DeepSeek / Minimax / Moonshot 等)
- **资源 ID**:
  - `custom_provider_api_format_label` = "协议"(复用 model provider detail screen 已有的 `custom_provider_api_format_*` 资源)
  - `custom_provider_api_format_anthropic` = "Anthropic 兼容"
  - `custom_provider_api_format_openai` = "OpenAI 兼容"
  - `custom_provider_helper_anthropic` = "Anthropic Messages API 协议:body 含 system 顶层字段 + messages 数组;SSE 事件 content_block_delta。baseUrl 填厂家文档的 base URL(不含 path),path `/v1/messages` 由协议自动拼。示例:DeepSeek = `https://api.deepseek.com/anthropic`,Minimax = `https://api.minimaxi.com/anthropic`。"
  - `custom_provider_helper_openai` = "OpenAI Chat Completions 协议:body 含 messages 数组(无 system 顶层);SSE 事件 data: {choices: [...]}。baseUrl 填厂家文档的 base URL(不含 path),path `/chat/completions` 由协议自动拼。示例:DeepSeek = `https://api.deepseek.com`,Moonshot = `https://api.moonshot.cn/v1`。"

## Risks / Trade-offs

- **R1 — 用户仍误填完整 URL** → 用户从 Anthropic SDK 文档复制完整 URL `https://api.deepseek.com/anthropic/v1/messages` 填入 → adapter 拼成 `https://api.deepseek.com/anthropic/v1/messages/v1/messages` → 404。
  - **Mitigation**:helper 文案明确"baseUrl 不含 path,path 由协议自动拼";测试连通按钮给可读 error。

- **R2 — 旧 JSON 行为变化** → 旧 custom provider 按"完整 URL"形式存(如 `https://api.deepseek.com/anthropic/v1/messages`),本次 change 后变成 `https://api.deepseek.com/anthropic/v1/messages/v1/messages` → 404。
  - **Mitigation**:迁移时检测旧数据,剥离末尾的 `/v1/messages` 或 `/chat/completions` 后缀(可在 `CustomProviderEditViewModel.loadExisting` 里做 URL 规范化);或新版本 APK 提示用户重新编辑(但破坏体验)。
  - **当前决策**:本次 change 不做迁移脚本,接受旧用户需要重新编辑一次 baseUrl 的代价(本项目 v1 没有 active 真实用户)。

- **R3 — baseUrl 末尾斜杠** → 用户填 `https://api.deepseek.com/anthropic/`(末尾带 `/`) → 拼成 `https://api.deepseek.com/anthropic//v1/messages` → 双斜杠可能 404。
  - **Mitigation**:`buildConfig` 已 `url.removeSuffix("/")`,统一剥末尾 `/`(已存在逻辑)。

- **R4 — 不同厂家的 base URL 形态差异** → Minimax 给 `https://api.minimaxi.com/anthropic`(已含 `/anthropic`);Moonshot 给 `https://api.moonshot.cn/v1`(含 `/v1` 但不是 Anthropic 协议专用前缀);用户看到一个 baseUrl 字段要"自己识别填什么"。
  - **Mitigation**:helper 文案给出多家示例,用户对号入座。

## Migration Plan

- **不需要自动 migration**:本次 change 让旧 JSON 行为变化(完整 URL → 重复 path),但 v1 没有 active 真实用户,影响范围 0。
- **部署步骤**:rebuild APK → 用户覆盖安装 → 旧 custom provider 配置需要重新编辑 baseUrl(剥离末尾 path)。
- **回滚**:字段 `apiFormat` 已有默认值,旧版本 APK 读新 JSON 也兼容(忽略未知 key,缺省用 Kotlin 默认值)。

## Open Questions

- Q1:迁移时是否做 URL 规范化(`loadExisting` 里检测并剥离末尾 `/v1/messages` 或 `/chat/completions`)?当前不做,留 follow-up — 接受 v1 用户重新编辑。
- Q2:DeepSeek 走 Anthropic 协议时,默认模型 `deepseek-v4-flash` 是否要列入「支持的模型」chip 提示?— 本次不做,留 follow-up;helper 文案目前只描述协议,模型名由用户按厂家文档填。