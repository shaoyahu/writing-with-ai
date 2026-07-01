# Anthropic 兼容 API · 通用协议

> 适用于**所有**走"Anthropic Messages API 兼容"协议的 provider，包括本项目预置的 **deepseek / minimax / mimo**，以及未来任何支持该协议的厂商。
>
> 协议层公共部分(端点形态、字段语义、SSE 事件、错误码)在此处统一说明;各家**特定差异**(base URL、auth header 名、支持的模型、字段差异)在各自的 `api-<provider>.md` 里展开。
>
> 数据来源:Anthropic 官方 Messages API 文档，以及 deepseek / minimax / mimo 三家公开文档的差异说明。**如有出入，以各家 provider 官方文档为准**(见各 provider 文件"参考"段)。

## 扩展约定

- **每加一个 provider 写一份 `api-<provider>.md`**，放本目录。
- 通用协议(SSE 事件、字段语义、错误码)放本文，不在每份 provider 文档里重复。
- 命名:小写连字符，如 `api-deepseek.md` / `api-minimax.md` / `api-mimo.md`。
- 自定义(用户手动加的私有协议 provider)若涉及新协议，本文件相应**扩节**，并在各 provider 文档加引用。

---

## 1. 端点

```
POST {baseUrl}/v1/messages
```

各家 base URL 不同，见 `api-<provider>.md` 第 1 节。Content-Type 统一为 `application/json`。

---

## 2. 认证

Anthropic 协议本身支持以下方式，**各家支持范围不同**(以 provider 文档为准):

```
# 方式 A(Anthropic 官方默认)
Authorization: Bearer <API_KEY>

# 方式 B(Anthropic SDK 默认)
x-api-key: <API_KEY>

# 方式 C(mimo 特有，**header 名是 api-key 而非 x-api-key**)
api-key: <API_KEY>
```

> 本项目 `ProviderConfig.authStyle` 用枚举表达:`AUTHORIZATION`(方式 A)/ `X_API_KEY`(方式 B)/ `CUSTOM_HEADER` + `headerName`(方式 C 及其他私有 header 名)。

---

## 3. 请求体 Schema

```json
{
  "model": "<MODEL_NAME>",
  "max_tokens": 1024,
  "system": "You are a writing assistant.",
  "messages": [
    {
      "role": "user",
      "content": [
        { "type": "text", "text": "..." }
      ]
    }
  ],
  "temperature": 1.0,
  "top_p": 0.95,
  "stream": false,
  "stop_sequences": null,
  "tools": [...],
  "tool_choice": { "type": "auto" },
  "thinking": { "type": "disabled" },
  "metadata": { "user_id": "<string>" }
}
```

### 字段说明(只列写作场景会用到的)

| 字段 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `model` | ✅ | string | 模型 ID，各家清单见 provider 文档 |
| `max_tokens` | ✅ | int | 输出 token 上限;**Anthropic 协议要求必填**(mimo 文档也强调) |
| `messages` | ✅ | array | 对话历史;写作场景 v1 传单条 user 消息即可 |
| `system` | ❌ | string \| array | 系统提示词;v1 写作场景**必填**(放模板) |
| `temperature` | ❌ | number | 默认 1.0，各家范围可能不同 |
| `top_p` | ❌ | number | 默认 0.95(各家略不同) |
| `stream` | ❌ | boolean | v1 写作场景**默认 true** |
| `stop_sequences` | ❌ | string[] | 停止序列;v1 不传 |
| `tools` | ❌ | array | function calling 定义;**v1 写作场景不用** |
| `tool_choice` | ❌ | object | 工具选择策略;v1 不传 |
| `thinking` | ❌ | object | 深度思考控制;v1 默认 `{"type": "disabled"}` |
| `metadata` | ❌ | object | 限流隔离等;v1 用 `metadata.user_id = SHA-256(install_id)` |

### messages[].content 形态

可以是字符串，也可以是 block 数组:

```json
"content": "纯文本字符串"
```

或:

```json
"content": [
  { "type": "text", "text": "..." },
  { "type": "image", "source": {...} }
]
```

v1 写作场景只用 `text` block。`tool_use` / `tool_result` / 多模态 block 不在 v1 范围。

---

## 4. 响应体 Schema(非流式)

```json
{
  "id": "msg_xxx",
  "type": "message",
  "role": "assistant",
  "model": "...",
  "stop_reason": "end_turn",
  "content": [
    { "type": "text", "text": "..." }
  ],
  "usage": {
    "input_tokens": 12,
    "output_tokens": 34
  }
}
```

| 字段 | 说明 |
| --- | --- |
| `id` | 消息唯一 ID，落 `ai_history.id` |
| `type` | 固定 `"message"` |
| `role` | 固定 `"assistant"` |
| `model` | 实际使用的模型(可能与请求不同，如 deepseek 自动映射) |
| `stop_reason` | `end_turn` / `max_tokens` / `tool_use`;v1 业务侧只关心 `end_turn` 和 `max_tokens` |
| `content[]` | 内容块列表;v1 只解析 `type=text` |
| `usage` | token 用量;`input_tokens` / `output_tokens`，部分 provider 还返回 `cache_*_tokens` |

---

## 5. 流式响应(SSE)

每行格式:

```
event: <event_type>
data: <json>
\n
```

事件类型(各家一致):

| 事件 | 含义 | 业务侧处理 |
| --- | --- | --- |
| `message_start` | 消息开始，`data.message` 含 `id` / `model` / `usage.input_tokens` | 触发 `AiStreamEvent.Started`;记录 `id` |
| `ping` | 心跳 | 忽略 |
| `content_block_start` | 内容块开始，`data.content_block` 含 `type` | v1 只关心 `type=text` |
| `content_block_delta` | 内容块增量，`data.delta.type` 是 `text_delta` / `thinking_delta` / `signature_delta` | `text_delta` → 累加到预览文本;`thinking_delta` / `signature_delta` → 丢弃 |
| `content_block_stop` | 内容块结束 | 忽略 |
| `message_delta` | 消息级增量，`data.delta` 含 `stop_reason`,`data.usage` 含 `output_tokens` | 收 `output_tokens` 用于 `Usage` 事件 |
| `message_stop` | 消息结束 | 触发 `AiStreamEvent.Done` |

### 业务侧统一映射

```
SSE event                       →  Flow<AiStreamEvent>
─────────────────────────────────────────────────────────
message_start                   →  Started
content_block_delta (text_delta) → Delta(text)
message_delta (含 usage)        →  Usage(input, output, total)
任意非 2xx / connection drop   →  Failed(recoverable=true)
message_stop                    →  Done
```

---

## 6. 错误码

各家实际 `type` 字符串可能略有差异，但 HTTP 状态码语义一致:

| HTTP 状态 | type | 含义 | 本项目 UI 行为 |
| --- | --- | --- | --- |
| 400 | `invalid_request_error` | 参数非法 | 显示 provider 返回的 message 摘要;按钮可点 |
| 401 | `authentication_error` | API Key 无效 | "apikey 无效，请去设置页校验";按钮置灰，引导跳转 |
| 403 | `permission_error` | 无权访问 | "无权访问该模型";跳设置 |
| 404 | `not_found_error` | 模型不存在 | "模型不存在，请检查设置中的模型名" |
| 413 | `request_too_large` | 请求体过大 | "输入过长，请减少文本量" |
| 429 | `rate_limit_error` | 触发限流 | 显示 `Retry-After`(若有);30s 内自动重试 1 次 |
| 500 | `api_error` | 服务端错误 | "服务异常，稍后重试";自动重试 1 次 |
| 529 | `overloaded_error` | 上游过载 | "服务繁忙，稍后重试";自动重试 1 次 |

**绝不让原始错误码 / 堆栈直接抛到 UI** —— `core/ai/net/ErrorMapper.kt` 统一转成本项目 `AiError` 枚举 + 用户可读文案。

---

## 7. 业务实现映射(本项目)

- `AiGateway.streamWritingOp(op, sourceText, providerId, modelName)` 内部:
  1. 查 `ProviderConfig`(baseUrl / authStyle / headerName)
  2. 拼 `messages`(单条 user，内容为 `sourceText`;`system` 来自 `core/ai/prompt/<op>.kt`)
  3. 调 `AnthropicCompatibleAdapter.stream(req, creds)` —— **三家共用一个 adapter**
  4. 解析 SSE → `Flow<AiStreamEvent>`
  5. 收尾时落 `ai_history` 表(provider / model / op / inputTokens / outputTokens / durationMs / inputSnapshot / outputSnapshot)
- `usage.input_tokens` 出现在 `message_start` 事件中;`output_tokens` 出现在 `message_delta` 事件中;v1 把 `total = input + output`(不依赖 provider 返回的 `total_tokens`)。
- 写作三类操作(`expand` / `polish` / `organize`)只用文本 content block;若 provider 返回 `tool_use` block 或 `image` block,**视为协议异常**，落 `Failed(recoverable=false)`。

---

## 8. 参考

- Anthropic 官方 Messages API:https://docs.anthropic.com/en/api/messages
- 各家 provider 特定说明见 `api-deepseek.md` / `api-minimax.md` / `api-mimo.md`
