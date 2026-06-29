# 真 Provider 联调 Runbook

> **状态**: M5 polish 收口阶段;**代码侧 0 改动**即可联调(2026-06-27 R5 review 后)。
> **目标**:把 APK 装到设备上,配 1 个真 AI provider 的 apikey,跑通 quicknote 详情 → AI 扩写/润色/整理 端到端。
> **预估耗时**: 5 分钟(配 apikey 1 分钟 + 触发 AI 操作 1 分钟 + 查 logcat 排查 3 分钟)。

## TL;DR

M3 `FakeAiProvider` 已完全走 debug-only 路径,release 包不注册 fake;`AiActionViewModel.start()` 已 inline 走 `ProviderPrefsStore.getSelectedProviderId()` → `SecureApiKeyStore.get(providerId)` → `AiGateway.streamWritingOp(apikey)`。**配 1 个 provider 的真 apikey 即可联调**,代码无需改一行。

## 用户操作链(设备端)

| # | 步骤 | 预期结果 | Verify 状态 |
|---|---|---|---|
| 1 | 装 APK(`./gradlew :app:installDebug` 或用 release 渠道) | launcher 出现「小札」图标 | `[pending]` |
| 2 | 首次启动 → onboarding-consent 条款页 | 滚到底部 → 「同意并继续」可点 | `[pending]` |
| 3 | 同意后 → apikey 教育页 | 读完说明 → 「我知道了」可点 | `[pending]` |
| 4 | 进 quicknote 列表 → 写一条笔记 → 进详情 → 选中文本 → 点 ✨ AI 按钮 | 弹出 ActionSheet(扩写/润色/整理/复制) | `[pending]` |
| 5 | 进「我的」→「设置」→「AI 模型管理」 | 看到 4 张卡片(deepseek / minimax / mimo / 自定义) | `[pending]` |
| 6 | 点 DeepSeek 卡片 → 详情页 | OutlinedTextField「API Key」空 + 模型下拉默认 `deepseek-v4-flash` | `[pending]` |
| 7 | 粘贴 DeepSeek Console 申请的 apikey → 「保存」 | Toast「已保存」,列表卡片显示「默认」角标 | `[pending]` |
| 8 | 返回 quicknote 详情 → 重触发 ✨ AI 按钮 → 点「扩写」 | 流式面板显示生成内容(几秒到几十秒) | `[pending]` |

> **Verify 状态三态**:
> - `[pending]` — 待真机跑过(首版 internal testing APK 发布后跑)
> - `[verified]` — 真机跑过,符合预期(标时间戳)
> - `[deferred]` — 真机跑不到 / 设备不足 / 跳过
>
> **维护人**: 真机验证者(用户 / 内测人员)本人改;v1 release preflight 卡"全 `[verified]`"才放行 release 通道。
>
> **MiniMax / MiMo 说明**: 内测阶段仅 DeepSeek 1 家真 provider 端到端验证;MiniMax / MiMo 走 placeholder,真机 verify 留待 v1.1。roadmap §14 已点 MiniMax / MiMo 的地域限制 / 白名单问题。

**关键节点**:第 7 步保存时,`secureApiKeyStore.save(providerId, apikey)` 写 EncryptedSharedPreferences + `providerPrefsStore.setSelectedProviderId(providerId)` 持久化 selected。

## 触发 AI 操作的代码路径

```
QuickNoteDetailScreen FAB ✨
  → AiActionViewModel.start(op)
    → ProviderPrefsStore.getSelectedProviderId()  (DataStore 同步读)
    → SecureApiKeyStore.get(providerId)          (EncryptedSharedPreferences)
    → ProviderPrefsStore.getApiFormat(providerId)  (DataStore)
    → ProviderPrefsStore.getSelectedModel(providerId)  (DataStore, 可空)
    → AiGateway.streamWritingOp(request, apikey)
      → CoreAiGateway.streamWritingOp
        → AnthropicCompatibleAdapter(若 apiFormat=OPENAI 或 ANTHROPIC)
          → OkHttp POST baseURL + auth header (Bearer / x-api-key / custom)
        → SSE 解析 → emit chunk
      → 落 ai_history 表(token 消耗 + 耗时)
```

## 3 家 Provider 协议

| Provider | baseURL | 协议 | authStyle | 默认模型 |
|---|---|---|---|---|
| DeepSeek | `https://api.deepseek.com/chat/completions` | OPENAI | `AUTHORIZATION`(Bearer) | `deepseek-v4-flash` |
| MiniMax | `https://api.minimaxi.com/anthropic/v1/messages` | ANTHROPIC | `AUTHORIZATION`(Bearer) | `MiniMax-M2.7-highspeed` |
| MiMo | `https://api.xiaomimimo.com/anthropic/v1/messages` | ANTHROPIC | `CUSTOM_HEADER`(`api-key`) | `mimo-v2.5-flash` |

详细协议:`docs/usage/api-{deepseek,minimax,mimo,anthropic-compatible}.md`。

## 已知限制 / 排查

### 设备地域限制

- **DeepSeek**:海外 IP 部分节点不可用;需在中国大陆 IP 申请
- **MiniMax**:同上
- **MiMo**:小米内测,需小米账号申请白名单

### apikey 配错 / 余额不足

错误会走 `AiError` sealed 分支 → `AiErrorDisplay` 渲染红框 + 引导跳设置页。常见错误码:

| HTTP status | AiError 类型 | 用户文案 |
|---|---|---|
| 401 | `Unauthorized` | 「apikey 无效,请到设置核对」 |
| 402 / 429 | `QuotaExceeded` | 「余额不足 / 触发限流」 |
| 5xx | `ProviderUnavailable` | 「provider 暂不可用,稍后重试」 |
| timeout | `Timeout` | 「网络超时」 |
| 400 + 协议错 | `BadRequest` | 「请求参数错误(模型名?协议?)」 |

### 排查工具

```bash
# 1. 确认 selected provider 是哪个
adb shell run-as com.yy.writingwithai.debug cat files/datastore/writingwithai_provider_prefs.preferences_pb

# 2. 确认 secure_prefs 写入(文件存在即代表 OK,内容是加密的看不到)
adb shell run-as com.yy.writingwithai.debug ls files/shared_prefs/writingwithai_secure_prefs.xml

# 3. logcat 过滤 AiGateway / AI History
adb logcat -s CoreAiGateway:* AiActionViewModel:* AnthropicCompatibleAdapter:* SseParser:*

# 4. 真机流量抓包(Charles / mitmproxy):apikey 走 Authorization Bearer header,
#    若生产环境担心泄漏可用 test apikey,跑完即删
```

### 「ProviderNotConfigured」排查

AI 操作直接红屏报这个错,说明:
- `ProviderPrefsStore.getSelectedProviderId()` 返 null(从未配过),或
- `SecureApiKeyStore.get(providerId)` 返 null(配过 provider 但没存 apikey)

**修**:进设置 → 模型管理 → 选 provider → 填 apikey → 保存。

## 自定义 Provider

「AI 模型管理」底部「添加自定义 Provider」按钮 → 弹窗填 baseURL + authStyle + 默认模型 + apikey → 保存。所有字段都走用户填写,不写死。

适用场景:用户自部署的 OpenAI 兼容服务(企业内 / 本地 ollama + anthropic 兼容代理)。

## 测试覆盖

| Test | 位置 | 覆盖 |
|---|---|---|
| `FakeAiProviderTest` | `core/ai/fake/` | fake 端到端 3 case |
| `AnthropicCompatibleAdapterApikeyTest` | `app/src/test/.../provider/` | 真 apikey 落 HTTP header 3 case(AUTHORIZATION / X_API_KEY / CUSTOM_HEADER) |
| `SseParserTest` | `core/ai/stream/` | SSE 解析 3 case |
| `CoreAiGatewayTest` | `core/ai/` | gateway 编排 |

待补(M5 polish follow-up):`AiHistoryDaoTest`(Robolectric Room test)。

## 下一步

- 设备端跑通后,把 apikey 申请过程 / 任意一家 provider 协议差异的踩坑记录补充到本文件
- 若 3 家都用通,跑 R6 review 看是否有架构层面问题(目前 R5 review 范围只覆盖 onboarding-consent + animation-system,R6 应覆盖全项目)