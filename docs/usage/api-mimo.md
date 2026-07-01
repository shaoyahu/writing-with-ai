# mimo · Anthropic 兼容 API

> 用于本项目 AI 抽象层 `core/ai/provider/mimo/` 的实现参考。
>
> 通用协议见 `api-anthropic-compatible.md`(端点形态 / 字段语义 / SSE 事件 / 错误码)。本文只列 **mimo 特定** 的部分。
>
> 数据来源:[mimo Anthropic API 文档](https://mimo.mi.com/docs/zh-CN/api/chat/anthropic-api)(2026-06 抓取)。**如有出入，以官方文档为准**。

---

## 1. 端点

- **Base URL**:`https://api.xiaomimimo.com`
- **完整 URL**:`POST https://api.xiaomimimo.com/anthropic/v1/messages`
- **Content-Type**:`application/json`

## 2. 认证

两种方式(选其一):

```
# 方式 A
Authorization: Bearer <API_KEY>

# 方式 B  ⚠️ header 名是 api-key，不是 x-api-key
api-key: <API_KEY>
```

> **重要**:mimo 的 header 名字是 **`api-key`**,**不是** Anthropic 标准的 `x-api-key`。
> 本项目 `ProviderConfig.authStyle = AuthStyle.CUSTOM_HEADER` + `headerName = "api-key"` 处理。
> 默认优先用 `Authorization: Bearer`;若用户报告 401,fallback 到 `api-key: <KEY>` 重试 1 次。

## 3. 支持的模型

| 模型 | 说明 |
| --- | --- |
| `mimo-v2.5-pro` | 主力模型 |
| `mimo-v2.5-flash` | 快速模型 |
| `mimo-v2.5-mini` | 极小模型 |

**v1 UI 默认**:`mimo-v2.5-pro`(推荐体验);设置里允许切换到 `mimo-v2.5-flash`(速度优先) / `mimo-v2.5-mini`(极致小)。

> ⚠️ **V2 系列 2026-06-30 正式下线**(V2 Flash / V2 TTS 等已于 2026-06-18~25 陆续转 V2.5)。**v1 UI 不展示 V2 模型名**;若用户在 v2+ "自定义模型"里填了 V2 名，运行时由 mimo 自行处理(返回 404 或自动映射，以实际为准)。

## 4. 字段差异(对比 Anthropic 标准)

| 字段 / 能力 | 支持情况 | 本项目处理 |
| --- | --- | --- |
| `thinking: {type: "disabled"}` | ✅ 支持 | v1 写作场景默认 `disabled` |
| `thinking: {type: "enabled", ...}` | 推测支持，文档**未详列** | v1 不开 |
| `tools`(function calling) | ✅ 支持 | v1 写作场景不传 |
| 图像输入 | ✅ 支持(多模态) | v1 不传图 |
| `prompt cache` | ❓ 文档**未明确**;推测不支持 | v1 不依赖 |
| `top_p` 默认 | `0.95` | v1 不传，用默认 |
| `temperature` 默认 | `1.0` | v1 不传，用默认 |
| `stop_sequences` | 支持，默认 `null` | v1 不传 |

## 5. 错误码与限流

官方文档**未详列**错误码与限流详情。本项目 v1 阶段按 Anthropic 标准错误码处理(见 `api-anthropic-compatible.md` §6)，实际错误 `type` 以 mimo 响应为准。

待确认资料(若 mimo 上线后需要精确):
- 错误码清单:`https://mimo.mi.com/docs/zh-CN/api/guidance/error-codes`
- 限流:`https://mimo.mi.com/docs/zh-CN/api/guidance/rate-limit`

## 6. 流式响应

mimo 文档**未单独给出 SSE 事件格式**。**v1 按 Anthropic 标准 SSE 解析**(见 `api-anthropic-compatible.md` §5)，真实调用时若发现字段名差异再调整。

## 7. 注意事项

- **Auth header 名是 `api-key` 不是 `x-api-key`**:本项目 `ProviderConfig` 必须支持自定义 header 名(已设计为 `CUSTOM_HEADER` 模式)。
- **V2 即将下线**:UI 不展示 V2 模型名;不在"已停用"列表里强行展示，避免给用户造成混淆。
- **流式响应文档不完整**:实际联调时若发现 mimo 私有事件类型，补到本文件 §6 引用块。

## 8. 本项目实现要点

- 走 `core/ai/provider/mimo/MimoConfig.kt`:
  ```kotlin
  ProviderConfig(
      id = "mimo",
      baseUrl = "https://api.xiaomimimo.com",
      endpointPath = "/anthropic/v1/messages",
      authStyle = AuthStyle.CUSTOM_HEADER,
      customAuthHeaderName = "api-key",   // ⚠️ 不是 x-api-key
      defaultModel = "mimo-v2.5-pro",   // review-H3:从 v2.5-flash 改 v2.5-pro
      supportedModels = listOf(
          "mimo-v2.5-flash",
          "mimo-v2.5-pro",  // v1 default
          "mimo-v2.5-mini",
      ),
  )
  ```
- **不需要单独写 `stream()` 实现**，由通用 `AnthropicCompatibleAdapter` 处理;`authStyle = CUSTOM_HEADER` 时由 adapter 反射式加 header。
- 流式事件按 Anthropic 标准解析(等真实调用验证)。
- 模型名用 `enum class MimoModel(val apiName: String)`,default = `V2_5_PRO`。

## 9. 参考

- mimo Anthropic API 文档:https://mimo.mi.com/docs/zh-CN/api/chat/anthropic-api
- mimo 文档首页:https://mimo.mi.com/docs/zh-CN/
- mimo 错误码:https://mimo.mi.com/docs/zh-CN/api/guidance/error-codes(待确认)
- mimo 限流:https://mimo.mi.com/docs/zh-CN/api/guidance/rate-limit(待确认)
