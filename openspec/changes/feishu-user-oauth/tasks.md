## 1. AndroidManifest + OAuthCodeReceiver

- [ ] 1.1 `AndroidManifest.xml` 加 OAuthCodeReceiver Activity,intent-filter scheme=com.yy.writingwithai host=feishu path=/callback
- [ ] 1.2 新建 `core/feishu/auth/OAuthCodeReceiver.kt` — Activity 解析 code query param 写入临时 holder
- [ ] 1.3 `app/build.gradle.kts` 加 `androidx.browser:browser:1.7.0`

## 2. UserTokenProvider

- [ ] 2.1 新建 `core/feishu/auth/UserTokenProvider.kt` — getToken / refresh / invalidate
- [ ] 2.2 POST `https://open.feishu.cn/open-apis/authen/v2/oauth/token`,grant_type=authorization_code / refresh_token,client_id,client_secret,code / refresh_token
- [ ] 2.3 @Volatile CachedUserToken + refreshMutex + 5min 提前刷新

## 3. FeishuAuthStore 调整

- [ ] 3.1 删 appSecret 持久化
- [ ] 3.2 加 refreshToken + accessToken flow
- [ ] 3.3 加 setOAuthCredentials(appId, access, refresh, expiresAt)
- [ ] 3.4 加 getRefreshTokenSnapshot / getAccessTokenSnapshot
- [ ] 3.5 删 getCredentialsSnapshot(appSecret)只保留 appId

## 4. OAuthLauncher

- [ ] 4.1 新建 `core/feishu/auth/OAuthLauncher.kt` — CustomTabs 拉 `https://open.feishu.cn/open-apis/authen/v2/authorize?app_id=&redirect_uri=&scope=docs:document+drive:drive`
- [ ] 4.2 失败 fallback Intent.ACTION_VIEW

## 5. FeishuAuthScreen

- [ ] 5.1 ViewModel 删 appSecret/folderToken input,新增 startOAuth()
- [ ] 5.2 startOAuth 拉 OAuthLauncher
- [ ] 5.3 OAuthCodeReceiver → UserTokenProvider.exchangeCode
- [ ] 5.4 Screen 删 appSecret 输入框 + 新增 "登录飞书" 按钮
- [ ] 5.5 加 redirect_uri 配置说明文案

## 6. AuthInterceptor + Module

- [ ] 6.1 AuthInterceptor 注入 UserTokenProvider 替代 TenantTokenProvider
- [ ] 6.2 FeishuModule 删 TenantTokenProvider binding,加 UserTokenProvider
- [ ] 6.3 FeishuApiClientImpl 各端点不变(token 由 Interceptor 注入)

## 7. 验证

- [ ] 7.1 `openspec validate feishu-user-oauth --strict` 通过
- [ ] 7.2 `./gradlew :app:assembleDebug :app:ktlintCheck :app:testDebugUnitTest` 全绿
- [ ] 7.3 grep 校验:无 TenantTokenProvider 残留,appSecret 不在 store
- [ ] 7.4 真机:登录飞书 → 同步 → URL 在用户云空间可见