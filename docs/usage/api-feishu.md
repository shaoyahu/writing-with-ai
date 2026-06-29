# 飞书 · user_access_token OAuth v2

> 本项目飞书集成(`core/feishu/auth/UserTokenProvider`)用的官方授权协议参考。
>
> **绝对不要瞎猜字段** — 任何 OAuth 字段疑问(authorize / token / refresh)先查下面三篇官方文档。

## 官方文档(必读)

| 阶段 | 文档 |
|---|---|
| **1. 获取 authorization code** | https://open.feishu.cn/document/authentication-management/access-token/obtain-oauth-code |
| **2. 换取 user_access_token** | https://open.feishu.cn/document/authentication-management/access-token/get-user-access-token |
| **3. 刷新 user_access_token** | https://open.feishu.cn/document/authentication-management/access-token/refresh-user-access-token |

> 数据来源:飞书开放平台官方文档(2026-06 抓取)。**如有出入,以官方文档为准**。

---

## 1. Authorize 端点

- **URL**:`https://accounts.feishu.cn/open-apis/authen/v1/authorize`
- **Method**:GET
- **Query 参数**(全 URL-encode):

| 参数 | 必填 | 说明 |
|---|---|---|
| `client_id` | 是 | 飞书开放后台 → 应用凭证 → App ID |
| `response_type` | 是 | 固定 `code` |
| `redirect_uri` | 是 | **必须**跟 token 交换时传的完全一致,且必须在飞书后台"安全设置 → 重定向 URL"登记 |
| `scope` | 否 | 空格分隔,最多 50 个。本项目用 `docx:document drive:drive offline_access` |
| `state` | 否 | CSRF 防护,回调原样返回。本项目用 UUID,5 分钟 TTL |

> 注意:authorize URL 是 **v1**,token 端点是 **v2**,**不要混**。

## 2. 换取 user_access_token

- **URL**:`https://open.feishu.cn/open-apis/authen/v2/oauth/token`
- **Method**:POST
- **Authorization header**:**不需要**
- **不需要** app_access_token 中间步骤(那套 OIDC v1 已废弃)

### 请求体(JSON)

| 字段 | 必填 | 说明 |
|---|---|---|
| `grant_type` | 是 | 固定 `authorization_code` |
| `client_id` | 是 | 飞书 App ID |
| `client_secret` | 是 | 飞书 App Secret |
| `code` | 是 | 授权码(5 分钟 TTL,**一次性**) |
| `redirect_uri` | **是** | **必须**跟 authorize 时一致,否则 `20071 redirect_uri 不匹配` |
| `code_verifier` | 否 | PKCE 流程(本项目未用) |
| `scope` | 否 | 缩减权限范围 |

> ⚠️ **历史踩坑**:`redirect_uri` 在 v2 端点**必填**。漏了飞书返回 `20063 请求体缺少必要字段`(msg 字段为空,无法定位缺哪个 — 必须按 spec 全部字段都送)。

### 响应体(JSON,字段顶层无 data 包装)

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | int | `0` = 成功,非 0 = 失败 |
| `access_token` | string | user_access_token(2h 有效) |
| `expires_in` | int | 有效期(秒) |
| `refresh_token` | string | 刷新凭证(需 `offline_access` scope) |
| `refresh_token_expires_in` | int | refresh_token 有效期 |
| `token_type` | string | 固定 `Bearer` |
| `scope` | string | 实际授予的 scope |
| `error` | string | 错误类型(失败时) |
| `error_description` | string | 错误详情(失败时) |

## 3. 刷新 user_access_token

- **URL**:同 §2,`https://open.feishu.cn/open-apis/authen/v2/oauth/token`
- **Method**:POST
- **Authorization header**:**不需要**

### 请求体

| 字段 | 必填 | 说明 |
|---|---|---|
| `grant_type` | 是 | 固定 `refresh_token` |
| `client_id` | 是 | |
| `client_secret` | 是 | |
| `refresh_token` | 是 | 上次授权或刷新时拿到的 refresh_token |
| `scope` | 否 | 缩减权限范围 |

> **不需要 redirect_uri**(refresh 跟 code 无关)。

### 响应体

同 §2。

## 4. 错误码

| code | 含义 |
|---|---|
| 0 | 成功 |
| 20001 | 必要参数缺失 |
| 20002 | client_secret 无效 |
| 20003 | 授权码无效或已使用 |
| 20004 | 授权码已过期(>5 分钟) |
| 20009 | 租户未安装应用 |
| 20010 | 用户无应用使用权限 |
| 20037 | refresh_token 过期(需重新授权) |
| 20063 | **请求体缺少必要字段**(msg 空,务必按 spec 全部字段都送) |
| 20064 | refresh_token 已使用或已撤销 |
| 20067 | scope 列表有重复 |
| 20068 | scope 包含未授权权限 |
| 20071 | redirect_uri 不匹配 |
| 20073 | refresh_token 已消费 |
| 20074 | refresh_token 未在应用启用 |

## 5. 本项目实现要点

- 走 `core/feishu/auth/UserTokenProvider.kt`:
  - 跟 OIDC v1 完全不同(那套已废弃)。
  - `exchangeCode(appId, appSecret, code, redirectUri)` — **4 个参数,redirectUri 必传**。
  - 响应字段在顶层,**无 data 包装**(v2 协议)。
  - 解析失败 fallback 60s(原 7000s 静默 2h 太危险)。
  - 完整脱敏 body + 错误响应 body 日志(`postJson` 的 `sanitizeBodyForLog`)。
- authorize URL 走 `OAuthLauncher.buildAuthorizeUrl`,redirect_uri 跟 token 交换保持一致。
- `OAuthLauncher.REDIRECT_URI = "https://xiaozha.nananxue.cn/callback"`(HTTPS 托管页,跳 custom scheme → `OAuthCodeReceiver`)。
- 用户需在飞书开放后台"安全设置 → 重定向 URL"**精确**填这一项(https / 路径 / 无尾斜杠,完全一致)。

## 6. 调试清单

- `20063` 报"请求体缺少必要字段" → 检查 body 字段是否齐全(尤其是 `redirect_uri`)
- `20071` redirect_uri 不匹配 → 检查 authorize 时传的 redirect_uri 跟后台登记的是否完全一致
- `20002` client_secret 无效 → 检查 `appSecret` 是否有空格 / 复制截断
- `20003` 授权码无效或已使用 → 同一个 code 不能用两次,重新走 OAuth
- `20004` 授权码已过期 → code > 5 分钟,重新走 OAuth
- 响应 `code != 0` 但 `msg` 空 → 看 `error` / `error_description` 字段(可能含具体原因)
