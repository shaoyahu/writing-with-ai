## 1. config 字段校准

- [x] 1.1 查 `docs/usage/api-deepseek.md` + 当前官方 docs,确认 `DeepseekConfig` 的 baseUrl / endpointPath / defaultModel / supportedModels / apiFormat 字段(2026-06-27 时间锚点);如有偏差先 docs 后 code
- [x] 1.2 同步修改 `app/src/main/java/com/yy/writingwithai/core/ai/provider/deepseek/DeepseekConfig.kt`,校对后跑 `AnthropicCompatibleAdapterR3RegressionTest` 确认旧单测仍过
- [x] 1.3 查 `docs/usage/api-minimax.md` + 当前官方 docs,校准 `MinimaxConfig` 字段
- [x] 1.4 查 `docs/usage/api-mimo.md` + 当前官方 docs,校准 `MimoConfig` 字段

## 2. docs 同步

- [x] 2.1 `docs/usage/api-deepseek.md` 更新模型清单(若有变化)+ endpoint + auth 方式;与 `DeepseekConfig.kt` 字段逐项一致
- [x] 2.2 `docs/usage/api-minimax.md` 同步更新
- [x] 2.3 `docs/usage/api-mimo.md` 同步更新
- [x] 2.4 `docs/usage/api-anthropic-compatible.md` 检查公共协议描述是否仍准确(2026-06-27 时间锚点);若有漂移标 TODO

## 3. AiErrorLocalizedMapper

- [x] 3.1 新建 `app/src/main/java/com/yy/writingwithai/core/ai/api/AiErrorLocalizedMapper.kt`:`fun localize(error: AiError): Int`(纯函数,返回 @StringRes Int)
- [x] 3.2 映射规则走 D3 表:`ProviderNotConfigured` / `Auth` / `InsufficientBalance` / `RateLimited` / `ServerError` / `Network` / `Timeout` / `Unknown` → 各自 stringRes
- [x] 3.3 `res/values/strings.xml` 新增 10 个 key:`ai_error_provider_not_configured` / `ai_error_auth` / `ai_error_insufficient_balance` / `ai_error_rate_limited` / `ai_error_server_error` / `ai_error_network` / `ai_error_timeout` / `ai_error_unknown` / `ai_error_retry_after_seconds` / `ai_error_server_code`
- [x] 3.4 `res/values-en/strings.xml` 同步 10 个 key 英文翻译
- [x] 3.5 验证双语 key 集合双侧完全一致(`diff` 命令)

## 4. QuickNote 详情页 AI 按钮引导

- [x] 4.1 `feature/quicknote/detail/QuickNoteDetailScreen.kt`:在 AI 按钮 onClick 内读 `SecureApiKeyStore.configuredProviderIds`(走 `LaunchedEffect` collect)
- [x] 4.2 配置为空时:不调 `AiActionViewModel.start(...)`,直接显示 Snackbar `R.string.ai_error_provider_not_configured` + action "去设置";点击跳 `AppNav.ModelManagementRoute`
- [x] 4.3 配置非空时:走原 `AiActionViewModel.start(...)` 流程,失败后 Snackbar 用 `AiErrorLocalizedMapper.localize(err)` 渲染错误文案 + 致命错误 action 按钮引导

## 5. 真机烟测脚本

- [x] 5.1 新建 `scripts/real-provider-smoke.sh`(bash + curl),支持 `PROVIDER` / `API_KEY` env var
- [x] 5.2 脚本按 provider 选对应 baseUrl + endpoint + auth header,发 `POST` 流式请求,首字节 2xx exit 0,其它按 HTTP code 分桶 exit 4-9
- [x] 5.3 脚本不 echo apikey,失败响应只打印 HTTP code + provider/model 标识
- [x] 5.4 `scripts/README.md` 加 1 段说明,manual trigger,不进 CI

## 6. JVM 单测

- [x] 6.1 新建 `app/src/test/java/com/yy/writingwithai/core/ai/api/AiErrorLocalizedMapperTest.kt`
- [x] 6.2 测 1:`9 dedicated variants map to their specific stringRes`(覆盖 9 个专属 variant + 互不相同断言)
- [x] 6.3 测 2:`ContentModeration Deserialization ApikeyPromptNotAcked Unknown fall through to ai_error_unknown`(4 个 variant 走兜底)
- [x] 6.4 测 3:`RateLimited and ServerError variants localize regardless of param values`(参数值不影响 stringRes)
- [x] 6.5 测 4:`localize is deterministic pure function`(同 input 多次同 output)
- [x] 6.6 测 5:`all 13 AiError variants are covered by mapper`(13 个 variant 全覆盖,防新增 variant 编译器不报错)

## 7. 真机端到端验证(USER-OWNED — 不代)

- [ ] 7.1 准备 deepseek / minimax / mimo 各 1 个真实 apikey(本地 env var,不入 repo)
- [ ] 7.2 真机装 APK → 设置 → 模型管理 → 填 deepseek apikey → ping → 详情页 → 选 EXPAND op → 真 SSE 流式 → 检查 token 计数 + ai-history 表写入
- [ ] 7.3 重复 7.2 for minimax / mimo
- [ ] 7.4 `scripts/real-provider-smoke.sh PROVIDER=deepseek API_KEY=...` 跑通 exit 0;同样 minimax / mimo
- [ ] 7.5 故意配错 apikey → 触发 `Auth` → 验证 Snackbar 文案 + "去设置" action
- [ ] 7.6 清空 apikey → AI 按钮 → 验证缺 apikey 引导 Snackbar + 跳设置页
- [ ] 7.7 飞行模式 → 触发 `Network` → 验证错误文案 + 不灰屏(降级走原文)

## 8. 收口

- [x] 8.1 `./gradlew :app:assembleDebug` 全绿(2s)
- [x] 8.2 `./gradlew :app:ktlintCheck` 全绿(0 violations,ktlintFormat 自动修 7 处 trailing comma)
- [x] 8.3 `./gradlew :app:testDebugUnitTest` 全绿(13s,含新增 5 个 AiErrorLocalizedMapperTest 用例)
- [x] 8.4 `docs/progress.md` 顶部追加本 change 收口条目(§1-§6 + §8 AI 部分;§7 真机校准证据等用户跑完补)
- [ ] 8.5 把真机验证结果写到本 change 的 `verification-report.md`(3 家跑通的 endpoint / 时间 / token 数)— USER-OWNED,等 §7