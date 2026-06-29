# DeepSeek · OpenAI 兼容 API

> 用于本项目 AI 抽象层 `core/ai/provider/deepseek/` 的实现参考。
>
> 通用协议见 `api-anthropic-compatible.md`(端点形态 / 字段语义 / SSE 事件 / 错误码)。本文只列 **deepseek 特定** 的部分。
>
> 数据来源:[DeepSeek API 总入口](https://api-docs.deepseek.com/)(2026-06 抓取)。**如有出入,以官方文档为准**。

---

## 1. 端点

- **Base URL**:`https://api.deepseek.com`
- **完整 URL**:`POST https://api.deepseek.com/chat/completions`
- **Content-Type**:`application/json`

## 2. 认证

文档明确支持:

```
Authorization: Bearer <API_KEY>
```

> deepseek 在 Anthropic 兼容协议(`/anthropic/v1/messages` + `x-api-key`)也支持,
> 但 v1 决策走 OpenAI 兼容(`/chat/completions` + `Authorization: Bearer`),与 minimax / mimo 协议分离。

## 3. 支持的模型

| 模型 | 说明 |
| --- | --- |
| `deepseek-v4-pro` | 主力模型,带思考 |
| `deepseek-v4-flash` | 快速模型,无思考 |

**v1 UI 默认**:`deepseek-v4-pro`(推荐体验);设置里允许切换到 `deepseek-v4-flash`(速度优先)。

> **已停用参考**:`deepseek-chat` / `deepseek-reasoner` 于 **2026-07-24** 停用。v1 UI 不展示这两个名字;若用户在"自定义模型名"里填了它们,运行时由 deepseek 自动映射(见 §4)。

## 4. 字段差异(对比 OpenAI 标准)

| 字段 / 能力 | 支持情况 | 本项目处理 |
| --- | --- | --- |
| `messages[].role="system"` | ✅ 支持 | system prompt 走 `messages` 第一条 |
| `temperature` | ✅ 支持 | v1 不传,走 provider 默认 |
| `top_p` | ✅ 支持 | v1 不传 |
| `stream` | ✅ 支持(SSE) | v1 强制 `true` |
| `tools` / `tool_choice` | ✅ 支持 | v1 不传 |
| 图片 / 文档 | ✅ 支持(`image_url` content type) | v1 写作场景不传 |
| prompt cache | ✅ 支持(`cache_control`) | v1 不依赖此特性 |
| `metadata.user_id` | ❌ 不支持 | v1 不传 |

### 模型自动映射

- 传入 `claude-opus*` → `deepseek-v4-pro`
- 传入 `claude-haiku*` / `claude-sonnet*` → `deepseek-v4-flash`
- 其它未识别模型名 → `deepseek-v4-flash`

> v1 不依赖此特性:本项目 `ProviderConfig.supportedModels` 只列真实可用的两个;用户**只能**在 UI 下拉框选,不能手填未列出的模型名。设置页的"自定义模型"入口(v2+)再放开。

## 5. 错误码

走 OpenAI 标准(见 `api-anthropic-compatible.md` §6)。**实际 `type` 字符串以 deepseek 响应为准**,但 HTTP 状态码语义与标准一致。`code` 字段值:`invalid_api_key`(401) / `insufficient_balance`(402) / `rate_limit_exceeded`(429)。

## 6. 限流

无 `metadata.user_id` 隔离(OpenAI 标准不要求);v1 用稳定伪 user_id 仅用于本地缓存 key,不发给 deepseek。

## 7. 注意事项

- **prompt cache**:文档明确支持 `cache_control`;v1 不依赖此特性,所有 system / user content 不带 cache marker。
- **官方 SDK 用法**(参考,本项目不采用):

  ```python
  client = OpenAI(api_key=os.environ["DEEPSEEK_API_KEY"], base_url="https://api.deepseek.com")
  resp = client.chat.completions.create(model="deepseek-v4-pro", messages=[...], stream=True)
  ```

## 8. 本项目实现要点

- 走 `core/ai/provider/deepseek/DeepseekConfig.kt`,`ProviderConfig(id="deepseek", baseUrl="https://api.deepseek.com", endpointPath="/chat/completions", authStyle=AUTHORIZATION, defaultModel="deepseek-v4-pro", supportedModels=[v4-flash, v4-pro], apiFormat=OPENAI)`。
- 复用 `AnthropicCompatibleAdapter` 多格式路径:根据 `config.apiFormat == ApiFormat.OPENAI` 切到 OpenAI 协议分支(走 `messages[]` 数组 + `Authorization: Bearer` + OpenAI SSE chunk 格式)。
- 错误映射走 `core/ai/net/ErrorMapper.kt` 通用版本;401/403 触发"apikey 无效"提示,引导用户去设置页。
- 模型名存储:Settings 用 `enum class DeepseekModel(val apiName: String) { V4Flash("deepseek-v4-flash"), V4Pro("deepseek-v4-pro") }`,避免业务层散落字符串。

## 9. 参考

- DeepSeek API 总入口:https://api-docs.deepseek.com/
- OpenAI Chat Completions API 参考:https://platform.openai.com/docs/api-reference/chat
