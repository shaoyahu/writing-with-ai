## Why

3 家预置 AI provider(deepseek / minimax / mimo)的 `AnthropicCompatibleAdapter` + `ProviderConfig` + `SecureApiKeyStore` + 模型管理 UI + ai-history 表均已落地(R6/R7 review + M6 polish 修了 5 轮)，但在真机端到端验证 3 家真实 SSE 流式调用上还有缺口:

1. **3 家 config 字段未与官方文档逐项对齐** — `DeepseekConfig.defaultModel = "deepseek-v4-flash"` 等是 demo 占位，M6 polish 没真跑过，跑真实 provider 大概率 404 model_not_found
2. **错误文案未本地化 + 未分级** — `AiError.summary()` 是英文术语，401/402/429/5xx 在 UI 直接展示用户看不懂;也没引导用户到设置页
3. **首次使用真实 AI 缺引导** — onboarding 已批准(consent store 有)但用户没配 apikey 时，详情页 AI 按钮直接灰显，无解释
4. **`docs/usage/api-deepseek.md` 等 4 份 provider 文档未与代码 config 同步** — spec.md "协议公共部分放 api-anthropic-compatible.md" 已知漂移
5. **真机/真 endpoint 跑通案例未沉淀** — 之前 `release-preflight-automation` 跑的是 fake provider，真实 provider 失败路径(retry / 超时 / token 计数)只走单测覆盖

## What Changes

- 真机验证 3 家预置 provider(deepseek / minimax / mimo) + 1 个自定义 Anthropic 兼容 provider，记录真实 baseUrl / endpoint / 模型 / 错误响应
- 校准 3 家 `ProviderConfig` 字段(baseUrl / endpointPath / authStyle / defaultModel / supportedModels / apiFormat)，与官方文档对齐
- 新增 `AiErrorLocalizedMapper`(`core/ai/api/` 下)，把 `AiError` 枚举映射到 `stringRes` 双语错误文案
- 新增"未配 apikey 引导"提示:`QuickNoteDetailScreen` AI 按钮在 `SecureApiKeyStore.configuredProviderIds` 为空时显示 `R.string.ai_action_provider_not_configured_hint`，点击跳设置页
- 同步 `docs/usage/api-deepseek.md` / `api-minimax.md` / `api-mimo.md` / `api-anthropic-compatible.md`，与新 config 字段一致
- 新增真机测试脚本 `/scripts/real-provider-smoke.sh`(`curl` + 真实 apikey → 三家逐一验)，失败 exit 1
- 1 个新增 JVM 单测覆盖 `AiErrorLocalizedMapper` 5 个 case
- 不动 `FakeAiProvider`(单测 / UI 验收仍用)
- 不动 `SecureApiKeyStore` public API(已稳)
- 不动 ai-gateway spec 主流程(只补 error mapping 子流程)

## Capabilities

### Modified Capabilities

- `ai-gateway`:新增 1 个 Requirement "AiError is localized in UI"(覆盖 `AiError.summary()` 直接展示 → stringRes 映射)
- `ai-gateway`:新增 1 个 Scenario "Provider config fields validated against real endpoint"(覆盖 3 家 config 字段校准)

## Impact

- 新增 1 个文件 `core/ai/api/AiErrorLocalizedMapper.kt`
- 修改 3 个文件:`core/ai/provider/{deepseek,minimax,mimo}/*Config.kt` 字段校准
- 修改 1 个文件:`feature/quicknote/detail/QuickNoteDetailScreen.kt` AI 按钮缺 apikey 提示
- 修改 4 份文档:`docs/usage/api-{anthropic-compatible,deepseek,minimax,mimo}.md`
- 新增 1 个 scripts 文件:`scripts/real-provider-smoke.sh`
- 新增 1 个单测:`core/ai/api/AiErrorLocalizedMapperTest.kt`
- 新增 ~10 个 string key(双语，5 类错误 × 2 lang)
- 不引入新依赖(纯 Kotlin + OkHttp)
- 真机验证路径:准备 deepseek / minimax / mimo 真实 apikey → 实机装 APK → 设置 → 模型管理 → 填 apikey → ping → 详情页 → 选 EXPAND op → 真 SSE 流式 → 检查 chip / token 计数 / 错误回退