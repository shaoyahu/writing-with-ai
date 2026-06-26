## Context

当前飞书同步用 `tenant_access_token`,创建的文档在应用身份名下,用户看不见。需要切到 OAuth user authorization。

## Goals / Non-Goals

**Goals**:用户身份 OAuth、access_token 自动 refresh、文档创建到用户云空间、点"登录飞书"按钮即可授权
**Non-Goals**:in-app WebView(用系统浏览器)、多账号、PKCE

## Decisions

### 1. 系统浏览器 + Custom Tabs + deep link
不用 in-app WebView(飞书登录态不持久),用 Custom Tabs 复用浏览器 Cookie。

### 2. redirect_uri = `com.yy.writingwithai://feishu/callback`
Custom scheme。需要用户在飞书后台"安全设置 → redirect URL"配。

### 3. UserTokenProvider 双层缓存
hot path:`@Volatile CachedUserToken` 内存;cold path:refresh_token 持久化 → POST `/authen/v2/oauth/token`;auto refresh:expiresAt - 5min;并发去重:refreshMutex。

### 4. v2 docs_ai + user token
`createDocumentV2` / `updateDocumentV2` / `fetchDocumentV2` / `appendBlockV2` 用 user_access_token → 文档创建到用户云空间。

## Risks

[Risk] redirect_uri 没在飞书后台配 → 报错 invalid_redirect_uri
→ Mitigation:FeishuAuthScreen 加说明文案

[Risk] TenantTokenProvider → UserTokenProvider breaking
→ Mitigation:FeishuAuthStore 加版本号,首次启动清空旧 token,强制走 OAuth

[Risk] Custom Tabs 没装系统浏览器
→ Mitigation:fallback Intent.ACTION_VIEW + chooser

## Migration Plan

M1 AndroidManifest + Receiver Activity → M2 UserTokenProvider → M3 FeishuAuthStore → M4 OAuthLauncher → M5 Screen → M6 Interceptor + Module → M7 verify

## Open Questions

- Q1:redirect_uri 打包后可改吗?默认 `com.yy.writingwithai://feishu/callback`
- Q2:refresh_token 过期(30 天)→ 直接跳回 OAuth
- Q3:PKCE — v1 不加