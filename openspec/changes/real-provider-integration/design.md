## Context

3 家预置 AI provider(deepseek / minimax / mimo)+ `AnthropicCompatibleAdapter` + `SecureApiKeyStore` + ai-history 已在 M2/M6 落盘 + R6/R7 review 修了 5 轮。`FakeAiProvider` 单独保留给单测 / UI 验收用。但**真机/真 endpoint 端到端验证**从未做过:`release-preflight-automation` change 跑的是 fake provider,`AnthropicCompatibleAdapterR3RegressionTest` 用 `MockWebServer` 模拟响应。3 家 `ProviderConfig.defaultModel` 字段是 demo 占位，真跑必 404。

CLAUDE.md 硬规则已满足，本 change 不动:
- "apikey 仅本机加密存储":`SecureApiKeyStore` 用 `EncryptedSharedPreferences`，已稳
- "v1 备份策略":`android:allowBackup="false"` 全局关闭
- "字符串一律走 strings.xml":10 个新 key 双语
- "feature 必须自包含":`AiErrorLocalizedMapper` 放 `core/ai/api/`(基础),UI 引用走 `feature/*/`

## Goals / Non-Goals

**Goals:**
- 真机/真 endpoint 跑通 deepseek / minimax / mimo 三家 SSE 流式调用(各家准备 1 个真实 apikey)
- 校准 3 家 `ProviderConfig` 字段与官方文档逐项对齐(baseUrl / endpointPath / defaultModel / supportedModels / apiFormat)
- `AiError` 在 UI 层通过 `AiErrorLocalizedMapper` 映射成双语 stringRes,401/402/429/5xx 各自有引导动作
- `QuickNoteDetailScreen` AI 按钮在 `SecureApiKeyStore.configuredProviderIds` 为空时显示 hint，点击跳设置页
- 同步 4 份 `docs/usage/api-*.md`，与新 config 一致
- 沉淀 `scripts/real-provider-smoke.sh` 烟测脚本，可在 CI 上跑

**Non-Goals:**
- 不动 `FakeAiProvider`(单测 / UI 验收保留)
- 不动 `SecureApiKeyStore` public API(已稳)
- 不动 `AnthropicCompatibleAdapter` 主体(只修 review 标记的字段)
- 不改 ai-gateway 主流程(只补 error mapping 子流程)
- 不做 UI 视觉改版
- 不引入新依赖

## Decisions

### D1: 3 家 config 字段校准基于官方文档，不基于"我以为"

**选文档驱动**:每家字段更新前先查 `docs/usage/api-<provider>.md` 与当前官方 API docs(2026-06-27 时间锚点),baseUrl / endpoint / 模型名逐字段核验，不靠记忆。

**否决经验**:M6 polish 用 demo 模型名就是因为"我以为是这样"。

### D2: `AiErrorLocalizedMapper` 是纯函数(error → @StringRes Int)

**选纯函数**:Composable / VM 直接 `stringResource(AiErrorLocalizedMapper.localize(err))`，无 Context 注入，无 side effect。

**否决 suspend**:错误文案映射不需要 IO,suspend 没意义。

### D3: 错误分级 — 致命 vs 可重试

| AiError | 严重度 | UI 引导 |
|---|---|---|
| `ProviderNotConfigured` | 配置缺失 | Snackbar "请先在设置 → 模型管理配置 apikey" + 按钮 "去设置" |
| `Auth(code, _)` | 401/403 | Snackbar "apikey 无效或过期，请检查设置 → 模型管理" + 按钮 "去设置" |
| `InsufficientBalance(_)` | 402 | Snackbar "账户余额不足，请充值" + 按钮 "复制错误码" |
| `RateLimited(retryAfterSeconds)` | 429 | Snackbar "${retryAfterSeconds}s 后重试" |
| `ServerError(code)` | 5xx | Snackbar "上游服务异常($code)，请稍后重试" |
| `Network(_, _)` | IO | Snackbar "网络异常，请检查连接" |
| `Timeout(_)` | 超时 | Snackbar "请求超时，请重试" |
| `Unknown(_, _)` | 兜底 | Snackbar "未知错误: <detail>" |

致命集(不可重试):Auth / InsufficientBalance / ProviderNotConfigured
可重试集(显示重试按钮):RateLimited / ServerError / Network / Timeout

### D4: 缺 apikey 引导走 Snackbar + 跳转按钮，不弹对话框

**选 Snackbar + action**:轻度提示，不打断当前操作流;用户可继续编辑，只是这次 AI 调用没成功。

**否决 Dialog**:用户没配 apikey 不是错误，是状态;对话框过重。

### D5: 真机烟测脚本 `real-provider-smoke.sh` 不写进 CI

**选手动触发**:真实 apikey 不能进 CI，只在开发者本机手动 `./scripts/real-provider-smoke.sh <provider-id> <apikey>` 跑;脚本退出码 0 表示 SSE 流成功。

**否决 CI 集成**:CI 永远拿不到真 apikey，跑也是 mock，没意义。

### D6: config 字段校准在 docs 同步前，先 docs 后 code

**选 docs-first**:`docs/usage/api-deepseek.md` 等先 update 模型清单与 endpoint，然后照文档改 `DeepseekConfig.kt` 字段;避免 code 改了 docs 没跟上(spec §"新增 provider 必须在 docs/plans/...同步更新" 已知规约)。

### D7: 不动 `FakeAiProvider`，允许 release-preflight 仍走 fake

**选保留**:fake 是单测 / UI 验收的关键基础设施，release-preflight 跑 fake 是合理的 smoke 路径(不消耗外部配额)。

## Risks / Trade-offs

- **[R1] 真实 apikey 不能进 repo / logcat / docs** → `real-provider-smoke.sh` 接收 apikey 作参数，不 echo，不写 log;错误响应经过 `sanitizeErrorDetail` 脱敏
- **[R2] 3 家 provider 改 config 后旧用户配置可能失效** → 这是 v1 内测阶段，允许 break;`docs/progress.md` 记录"v1 内测期间 provider config 字段可能调整"
- **[R3] 真实 provider 错误格式与 MockWebServer 不同，可能暴露新 bug** → 单测已覆盖关键路径;真机遇到新 case，补单测 + 归档 review
- **[R4] docs/usage/api-*.md 与代码 config 漂移** → 本 change 强制同步;spec §"修改 provider 文档时同步检查 api-anthropic-compatible.md 是否仍准确" 已规约
- **[R5] rate-limit 在 CI 频繁跑可能 ban 真账号** → smoke 脚本只手动跑，不在 CI;`Retry-After` 头解析已在 adapter 落地