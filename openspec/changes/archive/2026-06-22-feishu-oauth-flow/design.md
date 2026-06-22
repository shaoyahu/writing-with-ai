## Context

飞书云文档接入的前置:app 凭证管理 + tenant token 维护。本工具(自用)走 **tenant_access_token(应用身份)**,不走 user OAuth。

约束:
- 客户端持有 `tenant_access_token`,不模拟用户身份
- 用户在飞书开放平台自建「企业内部应用」(一次性配置,无需发布)
- token 加密存储,不复用 apikey alias
- token TTL = 2h,本地提前 5 min 刷新
- 首次走飞书操作前必须经过 apikey 教育拦截(`onboarding-apikey-prompt` 的 `ack_apikey_prompt_v1`)

## Goals / Non-Goals

**Goals:**
- 用户在设置页填 `app_id` / `app_secret` → 加密存
- POST `/open-apis/auth/v3/tenant_access_token/internal` 取 token(2h TTL)
- token 缓存(内存 + 持久层双重),过期前 5 min 自动重取
- OkHttp 拦截器自动塞 Bearer + 401 自动重试
- 设置页状态机:`未配置 / 已配置 / 已连接 / 连接失败`
- 「断开飞书」清所有 token + ref 行

**Non-Goals:**
- user OAuth(不需要,本工具自用)
- PKCE 流程(tenant_access_token 不需要)
- Lark 国际版(`open.larksuite.com` 暂不支持)
- 同步业务(由 `feishu-bidir-sync` change 覆盖)
- 自建后端中转 token(纯本地)

## Decisions

### D1 · EncryptedSharedPreferences 独立 alias

```kotlin
val feishuPrefs = EncryptedSharedPreferences.create(
    context, "feishu_oauth_v1", masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

不复用 `apikey` 的 masterKey / alias,完全独立。Auto Backup 已 `allowBackup=false`,本 alias 默认不进备份。

### D2 · TenantTokenProvider 二级缓存

```kotlin
class TenantTokenProvider @Inject constructor(
    private val store: FeishuAuthStore,
    private val httpClient: OkHttpClient
) {
    // 内存 hot path,加 Mutex 防并发重复取 token
    private val cachedToken = AtomicReference<CachedToken?>(null)
    private val fetchMutex = Mutex()

    suspend fun getToken(): String {
        cachedToken.get()?.let { if (it.isValid()) return it.token }
        return fetchMutex.withLock { reentrantFetch() }
    }

    private suspend fun reentrantFetch(): String {
        cachedToken.get()?.let { if (it.isValid()) return it.token } // double-check
        val (appId, appSecret) = store.getCredentials() ?: throw FeishuError.NotAuthorized
        val response = httpClient.post("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal") {
            jsonBody(mapOf("app_id" to appId, "app_secret" to appSecret))
        }
        val body = response.body<TenantTokenResponse>()
        if (body.code != 0) throw mapError(body)
        val expiresAt = now() + body.expire * 1000 - REFRESH_LEAD_MS
        val token = CachedToken(body.tenant_access_token, expiresAt)
        cachedToken.set(token)
        store.persistToken(token)
        return token.token
    }

    companion object {
        private const val REFRESH_LEAD_MS = 5 * 60 * 1000L // 5 min
    }
}
```

- **内存 hot path**:`cachedToken` AtomicReference,命中即返回(避免每次都查 store)
- **持久层 cold path**:`FeishuAuthStore` 存 `tenant_access_token` + `expires_at`,进程重启后第一请求从 store 读,内存为空则触发拉取
- **并发去重**:`fetchMutex` 保证只有一个 in-flight 请求,其他协程挂起等结果(用 `withLock { reentrantFetch() }` 实现"先 double-check 再 fetch")
- **提前刷新**:`isValid()` = `now() < expiresAt`,TTL - 5 min → 留 5 min 缓冲避开边界 race

### D3 · AuthInterceptor + 401 强制重取

```kotlin
class AuthInterceptor @Inject constructor(
    private val tokenProvider: TenantTokenProvider
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // 不重试自己(防止 401 → 重取 → 再 401 死循环)
        if (request.header(HEADER_NO_AUTH) != null) return chain.proceed(request)

        val token = runBlocking { tokenProvider.getToken() }
        val authed = request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        val response = chain.proceed(authed)
        if (!response.isUnauthorized()) return response

        // 401 → 强制重取 + 重试一次
        response.close()
        runBlocking { tokenProvider.invalidate() }
        val newToken = runBlocking { tokenProvider.getToken() }
        val retry = request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
        return chain.proceed(retry)
    }
}
```

- `tokenProvider.invalidate()` 清内存缓存,下一请求重新 POST
- 二次失败(原 401 刷新后仍 401)→ 直接返回,上层映射 `FeishuError.AuthExpired`

### D4 · 飞书响应 code 字段处理

飞书 API 错误响应 body 形如:
```json
{"code": 99991663, "msg": "token invalid", "data": {}}
```

- HTTP 200 + `code != 0` → 业务错误,映射 `FeishuError.BadRequest(code, msg)` / `Forbidden(scope)` 等
- HTTP 401/403 → 走 D3 重取路径
- HTTP 429 → `FeishuError.RateLimited(retryAfter)`(从 `Retry-After` header 读)
- HTTP 5xx → `FeishuError.ServerError(code)`
- `code == 99991663`(token 失效) → 触发 D3 重取

### D5 · 设置页状态机

| 状态 | 触发 | UI |
|---|---|---|
| `DISCONNECTED` | 无 app_id | 「请输入 app_id」 |
| `CONFIGURED` | 有 app_id/secret,但 token 未取过或已过期 | 「连接飞书」按钮 |
| `CONNECTED` | token 有效 | 「已连接」+ 「断开」按钮 |
| `FAILED` | 最近一次取 token 失败 | 「重试」+ 错误信息 |

### D6 · 「断开飞书」清理

```kotlin
fun disconnect() {
    store.clearAll() // 清 app_id/secret/token/expires
    // 清 feishu_ref 表(由 feishu-bidir-sync 提供 FeishuSyncRepository.disconnectAll())
    syncRepo.disconnectAll()
}
```

不删本地 note;断开后所有飞书同步功能不可用,UI 跳回「未配置」状态。

### D7 · apikey 教育拦截复用

设置页「飞书授权」section 进入时,先读 `userPrefsStore.ackApikeyPromptFlow.first()`:
- `true` → 直接显示授权 UI
- `false` → 弹 apikey 教育 dialog(复用 `ApikeyPromptDialog`),用户 ack 后才进授权 UI

## Open Questions

- 飞书「自建应用」是否需要审核 / 发布才能调 API?目前认知:「企业内部应用」无需发布即可使用全部权限,但需要管理员在后台开通;具体配置步骤落到 `docs/usage/feishu-app-credentials.md`(本 change 不自动化)
- 飞书 token TTL 是否固定 2h?实测可能 2h~3h 不等;本地存 `expires_at`,TTL 由服务端响应 `expire` 字段决定,不写死