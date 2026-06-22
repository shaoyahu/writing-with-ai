## 1. SecurePrefs · FeishuAuthStore

- [ ] 1.1 新建 `core/feishu/auth/FeishuAuthStore.kt`:封装 EncryptedSharedPreferences 文件 `feishu_oauth_prefs.xml`(与 apikey 文件分开,alias `feishu_oauth_v1`)
- [ ] 1.2 暴露 `appId: Flow<String?>` / `appSecret: Flow<String?>` / `tenantAccessToken: Flow<String?>` / `expiresAt: Flow<Long?>` / `authState: Flow<FeishuAuthState>`
- [ ] 1.3 暴露 setter:`setCredentials(appId, appSecret)` / `persistToken(token, expiresAt)` / `setAuthState(state)` / `clearAll()`
- [ ] 1.4 `FeishuAuthState` 枚举:`DISCONNECTED` / `CONFIGURED` / `CONNECTED` / `TOKEN_FETCHING` / `FAILED`

## 2. 加密文件配置

- [ ] 2.1 `AndroidManifest.xml` `android:dataExtractionRules` 加 `feishu_oauth_prefs.xml` exclude(沿用 allowBackup=false 策略)
- [ ] 2.2 `core/prefs/SecurePrefsFactory.kt`(或类似)扩 `createFeishuAuthStore(context): FeishuAuthStore`

## 3. TenantTokenProvider

- [ ] 3.1 新建 `core/feishu/auth/TenantTokenProvider.kt`:从 store 读 app_id/secret → POST `https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal` → 解析 `TenantTokenResponse { code, msg, tenant_access_token, expire }`
- [ ] 3.2 二级缓存:内存 `AtomicReference<CachedToken?>` + 持久层 store;`cachedToken.isValid() = now() < expiresAt`(TTL - 5 min 提前刷新)
- [ ] 3.3 并发去重:`Mutex.withLock { reentrantFetch() }` + double-check,防止并发 N 协程触发 N 次 token POST
- [ ] 3.4 `invalidate()` 方法:清内存缓存,供 AuthInterceptor 401 后调用
- [ ] 3.5 飞书响应 `code != 0` 时:`code == 99991663` → 抛 `FeishuError.TokenInvalid`(可重试);其他 → 抛 `FeishuError.BadRequest(code, msg)`

## 4. FeishuApiClient + AuthInterceptor

- [ ] 4.1 新建 `core/feishu/api/FeishuApiClient.kt`(interface):`createDocument` / `getDocument` / `getBlocks` / `appendChildren` / `batchDeleteChildren`(具体签名由 `feishu-bidir-sync` 提需求,本 change 提供最小集合)
- [ ] 4.2 新建 `core/feishu/api/FeishuApiClientImpl.kt`(OkHttp 实现):基地址 `https://open.feishu.cn/open-apis`
- [ ] 4.3 `FeishuError` 域错误 sealed class:`NotAuthorized` / `BadRequest(code, msg)` / `Forbidden(scope)` / `NotFound(resource)` / `RateLimited(retryAfter)` / `ServerError(code)` / `AuthExpired` / `NetworkError`
- [ ] 4.4 `AuthInterceptor`:从 `TenantTokenProvider` 取 token → `Authorization: Bearer <token>`;401 / `code == 99991663` → `tokenProvider.invalidate()` + 重取 + 重试一次;二次失败抛 `FeishuError.AuthExpired`
- [ ] 4.5 飞书响应统一解析:HTTP 200 + `code != 0` → 业务错误(`FeishuError.BadRequest(code, msg)` 等);HTTP 401/403 → 走重取路径;HTTP 429 → `RateLimited(retryAfter)`;HTTP 5xx → `ServerError(code)`

## 5. Hilt 注入

- [ ] 5.1 新建 `core/feishu/di/FeishuModule.kt`(注意:`core/feishu/` 下放 di/ 暂例外,与 prefs 同级):`@Provides OkHttpClient`(含 AuthInterceptor 链) / `@Provides TenantTokenProvider` / `@Provides FeishuApiClient`(bind impl)
- [ ] 5.2 OkHttp 实例复用 `core/net/` 已有 client(若存在)或新建

## 6. 设置页 · 飞书授权 UI

- [ ] 6.1 新建 `feature/settings/feishu/FeishuAuthScreen.kt`:根据 `FeishuAuthState` 渲染 — `DISCONNECTED` 提示「请输入 app_id」+ 输入框 + 「保存」;`CONFIGURED` 显示「连接飞书」按钮;`CONNECTED` 显示「已连接」+ 「断开」;`FAILED` 显示错误 + 「重试」
- [ ] 6.2 `FeishuAuthViewModel`:暴露 `authState: StateFlow<FeishuAuthState>`;方法 `saveCredentials(appId, appSecret)` / `connect()` / `disconnect()`
- [ ] 6.3 `app_secret` 显示控件:输入框 + 5s 后自动 mask(复用 `model-management-detail-dropdown` 的 mask 组件)
- [ ] 6.4 设置页 root section「飞书授权」点击跳 `FeishuAuthScreen` 路由
- [ ] 6.5 「断开飞书」UI 二次确认 dialog

## 7. apikey 教育拦截

- [ ] 7.1 `FeishuAuthScreen` `LaunchedEffect`:读 `userPrefsStore.ackApikeyPromptFlow.first()`;若 false → 弹 `ApikeyPromptDialog`(复用 `feature/onboarding/ApikeyPromptDialog`),ack 后再渲染授权 UI

## 8. 文档

- [ ] 8.1 新建 `docs/usage/feishu-app-credentials.md`:用户操作步骤 — 飞书开放平台 → 创建「企业内部应用」→ 拿到 app_id / app_secret → 粘到本工具设置页 → 配权限(`docx:document:create` / `readonly` / `update`)→ 发布或管理员审批

## 9. 单测

- [ ] 9.1 `FeishuAuthStore` 单测:credentials 读写 + clearAll + 加密文件不存在时降级
- [ ] 9.2 `TenantTokenProvider` 单测:内存命中 / store 命中 / 过期重取 / 并发去重(用 Mutex)/ 401 强制 invalidate
- [ ] 9.3 `FeishuApiClient` 单测:401 触发 token 重取 + 重试 + 二次失败抛 `AuthExpired`;429 抛 `RateLimited`;业务 code != 0 映射

## 10. 编译 + ktlint + test

- [ ] 10.1 `./gradlew :app:assembleDebug` 通过
- [ ] 10.2 `./gradlew :app:ktlintCheck` 通过
- [ ] 10.3 `./gradlew :app:testDebugUnitTest` 全绿
- [ ] 10.4 `./gradlew :app:check` 全绿

## 11. review + archive

- [ ] 11.1 发起 code-review(AI 自审 + 用户触发 review)
- [ ] 11.2 review-r1 修复
- [ ] 11.3 `/opsx:archive feishu-oauth-flow`