## Why

飞书云文档接入的前置:app 凭证管理 + token 维护。本工具(自用场景)选 **tenant_access_token(应用身份)** 路线:
- 用户在飞书开放平台自建应用 → 拿到 `app_id` + `app_secret`(一次性配置，粘到本工具设置页)
- 工具 POST `/open-apis/auth/v3/tenant_access_token/internal` 拿 `tenant_access_token`(2h 有效)
- 缓存到本地，过期前自动重取(app_id/secret 不变 → 不需 refresh_token)

**不走 user_access_token 的原因**:user OAuth 流程需要浏览器跳转 + App Link 回调 + 模拟用户身份操作，自用工具完全不需要。本工具代表应用身份，把笔记写到**应用自有文档空间**(docx 文档 owner 是 app)，用户不参与授权。

同步业务在 `feishu-bidir-sync` change，本 change 只交付"能调飞书 API"。

## What Changes

- **新增** 设置页「飞书授权」section:用户填 `app_id` / `app_secret`(粘自飞书开放平台「自建应用」→「凭证与基础信息」)→ 加密存 EncryptedSharedPreferences(独立 alias `feishu_oauth_v1`,**不复用** apikey alias)
- **新增** `FeishuAuthStore`(EncryptedSharedPreferences):存 `app_id` / `app_secret` / `tenant_access_token` / `expires_at` / `auth_state`(enum `DISCONNECTED` / `CONNECTED` / `TOKEN_FETCHING`)
- **新增** `TenantTokenProvider`:从 `FeishuAuthStore` 读 app_id/secret → POST `https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal` → 缓存到 store + 内存(双重，内存 = hot path,store = 持久层)
- **新增** token TTL 管理:`expires_at = now + token.expire - 300s`(提前 5 min 重取避免边界 race);`isValid()` 判定
- **新增** `FeishuApiClient`:OkHttp 拦截器从 `TenantTokenProvider` 取 token 塞 `Authorization: Bearer <token>`;遇 401/99991663(token 失效)→ 强制刷新一次(再次 POST `/auth/v3/tenant_access_token/internal`)→ 重试原请求;二次失败抛 `FeishuError.AuthExpired`
- **新增** 设置页「飞书」section 状态机:`未配置(无 app_id)` / `已配置(有 app_id/secret 未取过 token)` / `已连接(token 有效)` / `连接失败(token 取不到)`
- **新增** 「断开飞书」按钮:清所有 token + `feishu_ref` 表行(不删本地 note);UI 二次确认
- **新增** 同意前置:首次走 OAuth 前必须 `ack_apikey_prompt_v1 == true`(沿用 `onboarding-apikey-prompt` 的 key);不一致则先弹 apikey 教育
- **新增** 错误分类:`FeishuError.NotAuthorized` / `BadRequest(code, msg)` / `Forbidden(scope)` / `NotFound(resource)` / `RateLimited(retryAfter)` / `ServerError(code)` / `AuthExpired` / `NetworkError`
- **新增** 限流:飞书 1000 req/min/tenant_token，本应用单用户场景远低于，记录但不强制节流

## Capabilities

### New Capabilities

- `feishu-auth`:app_id/secret 输入 + 加密存储 + tenant_access_token 自动获取 / 缓存 / 提前刷新 + 断开授权
- `feishu-api-client`:OkHttp 拦截器统一塞 Bearer + 401/99991663 自动 refresh 重试 + 错误分类

### Modified Capabilities

无。`secure-prefs` 已支持 EncryptedSharedPreferences + 多 alias，本次新增 `feishu_oauth_v1` alias 即可，不修改其 REQUIREMENTS。

## Impact

- **代码**:`core/feishu/` 新包:
  - `auth/FeishuAuthStore.kt`(EncryptedSharedPreferences 封装)
  - `auth/TenantTokenProvider.kt`(获取 + 缓存 + 提前刷新)
  - `api/FeishuApiClient.kt` + `FeishuApiClientImpl.kt`(OkHttp 拦截器链)
  - `api/FeishuError.kt`(域错误 sealed class)
  - `api/AuthInterceptor.kt`(塞 Authorization header + 401 触发重取)
  - `di/FeishuModule.kt`(Hilt module)
  - `feature/settings/feishu/FeishuAuthScreen.kt`(UI)
  - `feature/settings/feishu/FeishuAuthViewModel.kt`
- **依赖**:无新增三方库;OkHttp + EncryptedSharedPreferences 已就位;kotlinx.serialization 用于 token JSON 解析
- **范围(scope)**:走应用身份，scope 概念不适用;需 app 拥有 `docx:document:create` / `docx:document:readonly` / `docx:document:update` 权限(飞书后台配)
- **安全**:`app_secret` 显示明文 5s 后自动 mask(复用 `model-management-detail-dropdown` 的 mask 控件);token 加密走 `EncryptedSharedPreferences`;Auto Backup 已 `allowBackup=false`，新增 `feishu_oauth_v1` 默认不在备份范围
- **测试**:`FeishuAuthStore` 单测;`TenantTokenProvider` 单测(缓存命中 / 过期判定 / 自动重取);`FeishuApiClient` 单测(401 触发 token 重取 + 二次失败抛 `AuthExpired`)
- **不在范围**:PKCE 流程(tenant_access_token 不需要);Lark 国际版(只支持 `open.feishu.cn`);user OAuth(本工具自用);同步业务(由 `feishu-bidir-sync` change 覆盖)