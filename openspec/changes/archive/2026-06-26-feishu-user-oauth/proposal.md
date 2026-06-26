## Why

当前飞书同步用 `tenant_access_token`(应用身份),创建的文档在「应用身份」名下,用户登录飞书后看不见。需要切到 OAuth user authorization flow,用 `user_access_token` 调用 `docs_ai/v1`,文档会创建到用户的云空间,正常可见。

## What Changes

- **新增** OAuth Authorization Code flow:应用内嵌 web view 跳到飞书 OAuth 授权页,redirect_uri 用 custom scheme,新增 `OAuthCodeReceiver` Activity 收回调
- **新增** `UserTokenProvider` 替代 `TenantTokenProvider`:用 app_id + app_secret + auth_code 换 user_access_token + refresh_token,自动 refresh
- **改造** `AuthInterceptor`:用 user_access_token
- **改造** `FeishuAuthStore`:删 appSecret 持久化(改一次用),加 refreshToken 持久化
- **改造** `FeishuAuthScreen`:删 appSecret 输入框,新增 "用飞书账号登录" 按钮

## Capabilities

### New Capabilities

- `feishu-user-oauth`:飞书 OAuth user authorization flow(scope=docs:document),user_access_token 自动刷新,文档创建到用户云空间

### Modified Capabilities

- `feishu-auth`:token 类型从 tenant_access_token 改为 user_access_token;新增 refresh_token 持久化;appSecret 不再持久化
- `feishu-api-client`:AuthInterceptor 改用 user_access_token;v2 docs_ai 端点继续可用

## Impact

- **新文件**:
  - `core/feishu/auth/UserTokenProvider.kt`
  - `core/feishu/auth/OAuthCodeReceiver.kt`
  - `core/feishu/auth/OAuthLauncher.kt`
- **改动**:`FeishuAuthStore.kt` / `FeishuAuthViewModel.kt` / `FeishuAuthScreen.kt` / `AuthInterceptor.kt` / `FeishuModule.kt` / `AndroidManifest.xml` / `FeishuApiClientImpl.kt` / `FeishuSyncService.kt`
- **依赖**:`androidx.browser:browser:1.7.0`
- **回退**:OAuth 失败时清空 store + 跳回 DISCONNECTED,用户可重试