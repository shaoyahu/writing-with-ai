## MODIFIED Requirements

### Requirement: AiError is localized in UI

UI 渲染 `AiError` 时 MUST 走 `AiErrorLocalizedMapper.localize(error: AiError): Int`(返回 @StringRes),`stringResource(AiErrorLocalizedMapper.localize(err))` 取得双语文案;**禁止**直接展示 `AiError.summary()` 英文术语。致命错误(Auth / InsufficientBalance / ProviderNotConfigured)UI MUST 提供 "去设置" 或 "复制错误码" 按钮引导用户。

#### Scenario: ProviderNotConfigured hint shows snackbar with settings action
- **WHEN** `SecureApiKeyStore.configuredProviderIds` 为空集 + 用户点击 AI 按钮
- **THEN** Snackbar 显示 `R.string.ai_error_provider_not_configured` + action 按钮 "去设置";点击 action 跳 `AppNav.ModelManagementRoute`
- **AND** apikey 配置存在时 Snackbar 不显示

#### Scenario: Auth error 401/403 shows apikey invalid snackbar
- **WHEN** provider 返回 401/403 + 真实 apikey
- **THEN** Snackbar 显示 `R.string.ai_error_auth` + action "去设置";点击跳 `ModelProviderDetailScreen(providerId)`

#### Scenario: InsufficientBalance 402 shows balance snackbar
- **WHEN** provider 返回 402
- **THEN** Snackbar 显示 `R.string.ai_error_insufficient_balance` + action "复制错误码";点击复制错误码到剪贴板

#### Scenario: RateLimited 429 shows retry-after snackbar
- **WHEN** provider 返回 429 + `Retry-After` 头
- **THEN** Snackbar 显示 `R.string.ai_error_rate_limited` 模板字符串 "${retryAfterSeconds}s 后重试";数值由 `AiError.RateLimited.retryAfterSeconds` 字段提供

#### Scenario: ServerError 5xx shows upstream error snackbar
- **WHEN** provider 返回 5xx
- **THEN** Snackbar 显示 `R.string.ai_error_server_error` 模板 "${code}"

### Requirement: Provider config fields validated against real endpoint

3 家预置 provider(deepseek / minimax / mimo)的 `ProviderConfig` 字段 MUST 与 `docs/usage/api-<provider>.md` 当前(2026-06-27 时间锚点)官方 API 文档对齐;真机调用 1 次成功流式调用作为校准证据;校准证据记录在 `docs/progress.md` 与 change 归档报告。

#### Scenario: Deepseek config fields verified
- **WHEN** 真机用 deepseek 真实 apikey 跑通 SSE 流式调用 1 次
- **THEN** `DeepseekConfig` 的 `baseUrl` / `endpointPath` / `authStyle` / `defaultModel` / `supportedModels` / `apiFormat` 与 `docs/usage/api-deepseek.md` 一致;真机响应包含 ≥1 个 `Delta` 事件

#### Scenario: Minimax config fields verified
- **WHEN** 真机用 minimax 真实 apikey 跑通 SSE 流式调用 1 次
- **THEN** `MinimaxConfig` 字段与 `docs/usage/api-minimax.md` 一致;真机响应包含 ≥1 个 `Delta` 事件

#### Scenario: Mimo config fields verified
- **WHEN** 真机用 mimo 真实 apikey 跑通 SSE 流式调用 1 次
- **THEN** `MimoConfig` 字段与 `docs/usage/api-mimo.md` 一致;真机响应包含 ≥1 个 `Delta` 事件

#### Scenario: Custom Anthropic-compatible provider smoke
- **WHEN** 用户在 `CustomProviderEditScreen` 填 baseUrl + apikey + 选 Anthropic 格式
- **THEN** 真机跑通 SSE 流式调用 1 次，响应包含 ≥1 个 `Delta` 事件;`docs/usage/api-anthropic-compatible.md` 描述的字段映射与实际响应一致