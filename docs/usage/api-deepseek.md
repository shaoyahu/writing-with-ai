# DeepSeek · Anthropic 兼容 API

> 用于本项目 AI 抽象层 `core/ai/provider/deepseek/` 的实现参考。
>
> 通用协议见 `api-anthropic-compatible.md`(端点形态 / 字段语义 / SSE 事件 / 错误码)。本文只列 **deepseek 特定** 的部分。
>
> 数据来源:[DeepSeek Anthropic API 指南](https://api-docs.deepseek.com/guides/anthropic_api)(2026-06 抓取)。**如有出入,以官方文档为准**。

---

## 1. 端点

- **Base URL**:`https://api.deepseek.com/anthropic`
- **完整 URL**:`POST https://api.deepseek.com/anthropic/v1/messages`
- **Content-Type**:`application/json`

## 2. 认证

文档明确支持:

```
x-api-key: <API_KEY>
```

环境变量名:`ANTHROPIC_API_KEY`。

> `Authorization: Bearer` 在 Anthropic 标准里支持,但 deepseek 文档**未明确说明**。本项目 v1 优先用 `x-api-key` 与官方示例保持一致;若用户实际配置时遇到 401,fallback 到 `Authorization: Bearer` 重试 1 次。

## 3. 支持的模型

| 模型 | 说明 |
| --- | --- |
| `deepseek-v4-pro` | 主力模型,带思考 |
| `deepseek-v4-flash` | 快速模型,无思考 |

**v1 UI 默认**:`deepseek-v4-flash`(性价比高);设置里允许切换到 `deepseek-v4-pro`。

> **已停用参考**:`deepseek-chat` / `deepseek-reasoner` 于 **2026-07-24** 停用。v1 UI 不展示这两个名字;若用户在"自定义模型名"里填了它们,运行时由 deepseek 自动映射(见 §4)。

## 4. 字段差异(对比 Anthropic 标准)

| 字段 / 能力 | 支持情况 | 本项目处理 |
| --- | --- | --- |
| `thinking`(含 `budget_tokens`) | ✅ 支持 | v1 不强制,默认 `disabled` |
| `output_config.effort` | ✅ 支持 | v1 不传 |
| `anthropic-beta` header | ❌ 忽略 | 不发 |
| `anthropic-version` header | ❌ 忽略 | 不发 |
| `top_k` | ❌ 忽略 | 不发 |
| 图片 / 文档 | ❌ 不支持 | v1 写作场景不传 |
| MCP 工具 | ❌ 不支持 | v1 不传 `tools` |
| `tool_choice.disable_parallel_tool_use` | ❌ 忽略 | 不发 |
| `metadata.user_id` | ✅ 支持(用于限流隔离) | v1 用 `SHA-256(install_id)` |

### 模型自动映射

- 传入 `claude-opus*` → `deepseek-v4-pro`
- 传入 `claude-haiku*` / `claude-sonnet*` → `deepseek-v4-flash`
- 其它未识别模型名 → `deepseek-v4-flash`

> v1 不依赖此特性:本项目 `ProviderConfig.supportedModels` 只列真实可用的两个;用户**只能**在 UI 下拉框选,不能手填未列出的模型名。设置页的"自定义模型"入口(v2+)再放开。

## 5. 错误码

走 Anthropic 标准(见 `api-anthropic-compatible.md` §6)。**实际 `type` 字符串以 deepseek 响应为准**,但 HTTP 状态码语义与标准一致。

## 6. 限流

基于 `metadata.user_id` 隔离;v1 用稳定伪 user_id(`SHA-256(install_id)`),保证跨次调用稳定而不暴露真实设备标识。

## 7. 注意事项

- **官方推荐用 Anthropic SDK + 环境变量**:虽然不强制,但减少 boilerplate。本项目不用 SDK(避免引入大依赖),直接 OkHttp + JSON。
- **prompt cache**:文档**未明确说明**是否支持 `cache_control`;v1 不依赖此特性。
- **官方 SDK 用法**(参考,本项目不采用):

  ```python
  client = Anthropic(api_key=os.environ["ANTHROPIC_API_KEY"], base_url="https://api.deepseek.com/anthropic")
  resp = client.messages.create(model="deepseek-v4-pro", max_tokens=1000, ...)
  ```

## 8. 本项目实现要点

- 走 `core/ai/provider/deepseek/DeepseekConfig.kt`,`ProviderConfig(id="deepseek", baseUrl="https://api.deepseek.com/anthropic", authStyle=X_API_KEY, supportedModels=[v4-flash, v4-pro])`。
- **不需要单独写 `stream()` 实现**,由通用 `AnthropicCompatibleAdapter` 处理 —— `DeepseekConfig` 只是数据。
- 错误映射走 `core/ai/net/ErrorMapper.kt` 通用版本;401/403 触发"apikey 无效"提示,引导用户去设置页。
- 模型名存储:Settings 用 `enum class DeepseekModel(val apiName: String) { V4Flash("deepseek-v4-flash"), V4Pro("deepseek-v4-pro") }`,避免业务层散落字符串。

## 9. 参考

- DeepSeek Anthropic API 指南:https://api-docs.deepseek.com/guides/anthropic_api
- DeepSeek API 总入口:https://api-docs.deepseek.com/
- Agent Integrations(Claude Code):https://api-docs.deepseek.com/quick_start/agent_integrations/claude_code
