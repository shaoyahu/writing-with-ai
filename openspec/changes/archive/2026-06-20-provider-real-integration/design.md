## Context

M4-4 已落地同意门 + `SecureApiKeyStore`(EncryptedSharedPreferences + Tink AES256_GCM)。三个预置 provider 适配器(deepseek / minimax / mimo)在 M2 通过共享 `AnthropicCompatibleAdapter` + 数据驱动 `ProviderConfig` 落地,但 **apikey 入口与 providerId 切换逻辑 0 接入**。当前 `AiActionViewModel` 构造时 hardcode `providerId="fake"`,流式走 `FakeProvider` stub 返回字面文本。

本 change 接入"用户选 provider + 填 apikey → 流式调用真实 AI"。**先做 provider-real-integration 再做 ui-redesign**,理由:UI 重设计若仍调 FakeProvider,视觉 review 缺真实数据锚点;先把真流式接上,UI 重设计能直接看到真实输出对齐视觉。

## Goals / Non-Goals

**Goals:**

- 用户在设置 → 模型管理选 provider(deepseek / minimax / mimo) + 填 apikey,apikey 加密存 `SecureApiKeyStore`
- 用户选的 providerId 存 DataStore Preferences,App 重启后保持
- "测试连通"按钮发 1 个最小请求(1 token 流式空 prompt)验 401 / network / 余额
- `AiActionViewModel.streamWritingOp(...)` 拿 `ProviderPrefsStore.selectedProviderId`,替换 hardcode "fake"
- 未填 apikey / provider 未选时,AI 入口走引导(跳模型管理页),不静默走 FakeProvider

**Non-Goals:**

- 自定义 provider URL(auth header 名 + baseURL,roadmap §6.4 v1 未支持)
- OpenAI / Gemini 等其他协议(v1 仅 Anthropic 兼容)
- apikey 自动过期检测(用户自己负责换)
- 多 provider 并发 / 投票

## Decisions

### D1 — ProviderPrefsStore 用 DataStore Preferences

- **方案 A**(采用):`androidx.datastore.preferences` 扁平 string key `selected_provider_id`,默认值 `"fake"`(让老用户平滑过渡)
- **方案 B**(弃):Room 加 provider 表 —— 过度,单字段不需要 schema 迁移
- **理由**:DataStore Preferences 已落地 M3(custom-prompt-template 复用),无新依赖

### D2 — apikey 输入 / 存 / 读复用 SecureApiKeyStore

- **方案 A**(采用):apikey 走 `SecureApiKeyStore.saveApiKey(providerId, apiKey)` + `revealApiKey(providerId)`,**不**经 Room / logcat / BuildConfig
- **方案 B**(弃):建新 provider 表 —— 重复加密基础设施,违反 CLAUDE.md "apikey 仅 EncryptedSharedPreferences"
- **理由**:M4-4 已落地 + Robolectric test 覆盖 roundtrip,直接复用

### D3 — 连通性测试 = "1 token 流式空 prompt"

- **方案 A**(采用):调 `AiGateway.ping(providerId)` 发 1 个空消息 + `max_tokens=1`,200 OK 视为可用;非 200 / network 失败 / 401 / 402 视为不可用,显示具体错误
- **方案 B**(弃):只调 listModels / metadata API —— 部分 provider 不暴露,行为不一致
- **理由**:统一发最小生成请求,跨 provider 行为一致;若 `AiGateway.ping` 缺失,M2 fallback 接口已就位(本 change 不依赖具体 ping 端点)

### D4 — AiActionViewModel 拿 selectedProviderId(替换 hardcode "fake")

- **方案 A**(采用):`AiActionViewModel` 构造加 `providerPrefsStore: ProviderPrefsStore` 依赖,`streamWritingOp(...)` 用 `providerPrefsStore.selectedProviderId.first()`(suspend)替换 `providerId = "fake"` 字段
- **方案 B**(弃):VM 内走 Hilt 单例读 ProviderPrefsStore —— 难测,违背 ViewModel 注入约定
- **理由**:CLAUDE.md "ViewModel 通过构造注入,不持有 Application 引用"

### D5 — 未选 provider / 未填 apikey 引导

- **方案 A**(采用):`AiActionViewModel.start(op, ...)` 在 `_state.value = Loading` 前查 `selectedProviderId + SecureApiKeyStore.hasApiKey(id)`,任一缺失 → emit `AiError.UserConsentRequired` 等价的新 `AiError.ProviderNotConfigured`(复用现有 UiState 通道)
- **方案 B**(弃):启动时 fallback FakeProvider —— 用户被字面文本骗,不诚实
- **理由**:CLAUDE.md "AI 调用失败必须 fallback 到无 AI 的体验,绝不允许白屏或阻塞核心流程" —— 未配置走"无可用 AI"路径,UI 显示"请先在设置 → 模型管理配置"

### D6 — 删 FakeProvider fallback 路径

- **方案 A**(采用):`AnthropicCompatibleAdapter` 的 `providerId="fake"` 分支保留(测试用),但 `AiActionViewModel` 不再传 "fake" —— 真实数据流不依赖 FakeProvider
- **方案 B**(弃):直接删 FakeProvider —— 测试夹具丢失,M2 单测可能破
- **理由**:保留测试夹具,生产路径切真

## Risks / Trade-offs

- **[真实 provider 联调网络抖动]**:`streamWritingOp` 走真实 SSE 流,弱网下 streaming 偶发断连 → **Mitigation**:CLAUDE.md 已有"流式中断自动重试 1 次"约束(M3 落地);保留部分流时给用户"重试"按钮。
- **[国产网络到 deepseek / mimo 失败]**:deepseek / mimo 国内访问偶发卡顿 → **Mitigation**:在连通性测试 UI 明确显示延迟 / 错误码,引导用户换 provider;不"自动切换"
- **[apikey 在 DataStore Preferences 旁路]**:用户选 providerId 存 Preferences(明文 string),但 apikey 走 EncryptedSharedPreferences —— **Mitigation**:CLAUDE.md 已立约束,本 change 不破坏
- **[M2 ping 接口可能不存在]**:方案 D3 假设 `AiGateway.ping` 存在 → **Mitigation**:若 apply 时发现缺失,在 change 内补一个最小 ping API(spec 自洽)

## Migration Plan

- **灰度**:apk 直接 release;首次启动 `selectedProviderId="fake"` 默认值,流式仍走 FakeProvider stub(老用户无感);用户主动设置后才切真
- **回滚**:`AiActionViewModel` 改回 hardcode "fake"(1 行),DataStore Preferences 不删(下次升级用)
- **CI 验证**:`./gradlew :app:assembleDebug` + `testDebugUnitTest` + 真机 PGU110 填 apikey 测连通

## Open Questions

- apikey 输入是否走"明文显示 5s 自动隐藏"(CLAUDE.md §9 已立)—— **是**,UI 直接套用现有 reveal 逻辑(M4-4 有)
- provider 切换是否支持"重置所有 prompt 模板"—— **不做**,prompt 模板独立 DataStore key