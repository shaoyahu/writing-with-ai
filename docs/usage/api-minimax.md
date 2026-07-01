# MiniMax · Anthropic 兼容 API

> 用于本项目 AI 抽象层 `core/ai/provider/minimax/` 的实现参考。
>
> 通用协议见 `api-anthropic-compatible.md`(端点形态 / 字段语义 / SSE 事件 / 错误码)。本文只列 **minimax 特定** 的部分。
>
> 数据来源:[MiniMax Anthropic API 文档](https://platform.minimaxi.com/docs/api-reference/text-chat-anthropic)(2026-06 抓取)。**如有出入，以官方文档为准**。

---

## 1. 端点

- **Base URL**:`https://api.minimaxi.com`
- **完整 URL**:`POST https://api.minimaxi.com/anthropic/v1/messages`
- **Content-Type**:`application/json`

## 2. 认证

两种方式(优先级:`Authorization` > `x-api-key`):

```
# 方式 A
Authorization: Bearer <API_KEY>

# 方式 B
x-api-key: <API_KEY>
```

> 本项目 v1 用 `Authorization: Bearer`(优先)，与 Anthropic 官方默认一致;若用户报告 401,fallback 到 `x-api-key` 重试 1 次。

## 3. 支持的模型

| 模型 | 说明 |
| --- | --- |
| `MiniMax-M3` | **多模态旗舰**;支持 thinking、tools、image、video |
| `MiniMax-M2.7` / `MiniMax-M2.7-highspeed` | 通用;`highspeed` 是快版本 |
| `MiniMax-M2.5` / `MiniMax-M2.5-highspeed` | 通用 |
| `MiniMax-M2.1` / `MiniMax-M2.1-highspeed` | 通用 |
| `MiniMax-M2` | 旧版基线 |

**v1 UI 默认**:`MiniMax-M2.7`(推荐体验);设置里允许切换到 `MiniMax-M2.7-highspeed`(速度优先) 等其他档位。**`M3` 仅在用户明确选"支持多模态/思考"时显示**，且 v1 写作场景实际不传图/视频。

## 4. 字段差异(对比 Anthropic 标准)

### 4.1 MiniMax 特有字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `service_tier` | string | ❌ | `standard`(默认)或 `priority`(1.5 倍价格);v1 不传，默认 `standard` |

### 4.2 字段能力矩阵

| 字段 / 能力 | 支持情况 | 本项目处理 |
| --- | --- | --- |
| `thinking` | ⚠️ **仅 M3 支持**;`type: "disabled"`(默认)或 `"adaptive"` | v1 选 M3 时默认 `disabled`;其它模型不传该字段 |
| `cache_control: {type: "ephemeral"}` | ✅ 在 `system` content block 中支持;`usage` 返回 `cache_creation_input_tokens` / `cache_read_input_tokens` | v1 不主动开 cache;v2+ 评估 |
| `tools` / `tool_choice: {type: "auto"}` | ✅ 支持 | v1 写作场景不传 |
| 图片输入 | ⚠️ **仅 M3 支持**;最大 10MB;JPEG/PNG/GIF/WEBP | v1 不传图 |
| 视频输入 | ⚠️ **仅 M3 支持**;最大 50MB(URL)/ 512MB(Files API);MP4/AVI/MOV/MKV | v1 不传视频 |
| `RequestContentBlock` 类型 | `text` / `image` / `video` / `tool_use` / `tool_result` / `thinking` / `mid_conv_system` | v1 只发 `text` |

### 4.3 top_p 默认

- `M3` 默认 `0.95`
- `M2.x` 默认 `0.9`

> v1 不传 `top_p`，用各家默认。

## 5. 错误码(MiniMax 实际返回)

| HTTP 状态 | type | 含义 |
| --- | --- | --- |
| 400 | `invalid_request_error` | 参数非法 |
| 401 | `authentication_error` | API Key 无效 |
| 403 | `permission_error` | 无权访问 |
| 404 | `not_found_error` | 模型不存在 |
| 413 | `request_too_large` | 请求体超 64MB |
| 429 | `rate_limit_error` | 触发 RPM/TPM/连接数限流 |
| 500 | `api_error` | 服务端错误 |
| 529 | `overloaded_error` | 上游过载 |

与 `api-anthropic-compatible.md` §6 通用表完全一致。

## 6. 限流

按 **RPM / TPM / 连接数** 限制;具体阈值在 minimax 控制台可见。429 响应里带 `Retry-After`(本项目 v1 读这个 header 退避重试，30s 上限)。

## 7. 注意事项

- **`service_tier`**:v1 默认 `standard`;若用户在 v2+ 选了 `priority`，设置里额外提示价格差异(1.5x)。
- **多模态**:v1 写作场景只用文本，**不暴露 image/video 接口给业务层**;`core/ai` 抽象层的 `AiRequest` v1 不含图片字段。
- **prompt cache**:M3 支持，v1 不强依赖。
- **未列出字段一律不传**(避免 deepseek 那种"忽略但不报错"的隐藏语义)。

## 8. 本项目实现要点

- 走 `core/ai/provider/minimax/MinimaxConfig.kt`:
  ```kotlin
  ProviderConfig(
      id = "minimax",
      baseUrl = "https://api.minimaxi.com",
      endpointPath = "/anthropic/v1/messages",
      authStyle = AuthStyle.AUTHORIZATION,
      defaultModel = "MiniMax-M2.7",   // review-H3:从 M2.7-highspeed 改 M2.7
      supportedModels = listOf(
          "MiniMax-M2.7-highspeed",
          "MiniMax-M2.7",  // v1 default
          "MiniMax-M2.5-highspeed",
          "MiniMax-M2.5",
          "MiniMax-M2.1-highspeed",
          "MiniMax-M2.1",
          "MiniMax-M2",
          "MiniMax-M3",
      ),
  )
  ```
- **不需要单独写 `stream()` 实现**，由通用 `AnthropicCompatibleAdapter` 处理。
- 错误映射走通用 `ErrorMapper`;529(overloaded)显示"服务繁忙，稍后重试"提示，自动重试 1 次。
- 模型名用 `enum class MinimaxModel(val apiName: String)`,default = `M2_7`。

## 9. 参考

- MiniMax Anthropic API 文档:https://platform.minimaxi.com/docs/api-reference/text-chat-anthropic
- MiniMax 平台首页:https://platform.minimaxi.com/
