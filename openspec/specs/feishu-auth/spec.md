## Purpose

飞书授权 / token 存储 / OAuth 用户流程 / 凭据加密 / 飞书同步相关 UI 行为契约。
## Requirements
### Requirement: Tenant access token acquisition with user-provided app credentials

The system MUST allow users to enter `app_id` and `app_secret` (obtained from the Feishu Open Platform) in the settings page. On tap "连接飞书", the system MUST POST to `https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal` with `{app_id, app_secret}` to obtain a `tenant_access_token` and `expire` (in seconds). No browser, no OAuth redirect, no App Link callback — purely server-side app credential exchange.

#### Scenario: User enters app credentials
- **WHEN** user navigates to Settings > 飞书授权 and enters `app_id` and `app_secret`
- **THEN** both values MUST be encrypted and persisted to `feishu_oauth_prefs` (EncryptedSharedPreferences, separate file from apikey prefs, alias `feishu_oauth_v1`)

#### Scenario: Connect succeeds and token cached
- **WHEN** user taps "连接飞书" with valid credentials entered and POST returns `{code: 0, tenant_access_token: "t-xxx", expire: 7200}`
- **THEN** `TenantTokenProvider` MUST cache the token in memory + `FeishuAuthStore`; `FeishuAuthState` MUST transition to `CONNECTED`; `expires_at = now + 7200s - 5min`

#### Scenario: Connect fails on bad credentials
- **WHEN** user taps "连接飞书" and POST returns `{code: 10003, msg: "invalid app_id or app_secret"}`
- **THEN** `FeishuAuthState` MUST transition to `FAILED`; the settings page MUST show "app_id 或 app_secret 错误" with a "重试" button; no token MUST be persisted

### Requirement: Token storage and lifecycle

`tenant_access_token` and `expires_at` MUST be stored in `feishu_oauth_prefs` (EncryptedSharedPreferences). The token expires in ~2 hours; `TenantTokenProvider` MUST transparently re-fetch a new token (using the same `app_id`/`app_secret`) when the cached one is within 5 minutes of expiry or has already expired. No `refresh_token` concept — the app credentials are reused.

#### Scenario: Token auto-refresh on expiry
- **WHEN** `TenantTokenProvider.getToken()` is called and the cached token's `expires_at < now() + 5min`
- **THEN** it MUST call `POST /open-apis/auth/v3/tenant_access_token/internal` again with the stored `app_id`/`app_secret`, replace the cached token, and return the new value

#### Scenario: Token persisted across process restarts
- **WHEN** the app process restarts and `FeishuApiClient` makes its first call
- **THEN** `TenantTokenProvider` MUST load the cached token from `FeishuAuthStore` and return it if still valid; otherwise fetch a fresh one

#### Scenario: Token never logged
- **WHEN** `tenant_access_token` is written to `feishu_oauth_prefs` or read in memory
- **THEN** the file MUST be excluded from Android Auto Backup (`allowBackup=false`); the token MUST NOT appear in logcat at any level; `BuildConfig.DEBUG` debug logs MUST mask it as `t-***`

### Requirement: Disconnect Feishu authorization

The settings page MUST provide a "断开飞书" button that clears all stored `app_id`, `app_secret`, `tenant_access_token`, `expires_at`, and all `feishu_ref` rows. After disconnection, all previously synced notes remain as local-only notes.

#### Scenario: Disconnect clears all Feishu data
- **WHEN** user taps "断开飞书" and confirms the dialog
- **THEN** all keys in `feishu_oauth_prefs` MUST be cleared; all rows in `feishu_ref` table MUST be deleted; in-memory token cache MUST be invalidated; `FeishuAuthState` MUST transition to `DISCONNECTED`

### Requirement: Credential field security

The `app_secret` input field MUST mask the value after 5 seconds of inactivity, using `PasswordVisualTransformation` by default with a "show" toggle icon.

#### Scenario: Secret masked after inactivity
- **WHEN** user types `app_secret` and 5 seconds elapse without further input
- **THEN** the field MUST switch to password-masked mode; the toggle icon MUST allow manual reveal/mask

### Requirement: Apikey education gate

Before showing the Feishu authorization UI, the system MUST verify the user has acknowledged the apikey cost prompt (`ack_apikey_prompt_v1 == true` from `UserPrefsStore`). If not acknowledged, the apikey prompt dialog MUST be shown first.

#### Scenario: User has not acknowledged apikey prompt
- **WHEN** user opens Settings > 飞书授权 with `ack_apikey_prompt_v1 == false`
- **THEN** the `ApikeyPromptDialog` MUST be shown; the Feishu auth UI MUST NOT render until the user acknowledges

#### Scenario: User has acknowledged apikey prompt
- **WHEN** user opens Settings > 飞书授权 with `ack_apikey_prompt_v1 == true`
- **THEN** the Feishu auth UI MUST render directly without showing the apikey prompt

### Requirement: OAuth state CSRF protection on hosted callback flow

User-OAuth flow MUST use a server-bound `state` parameter to prevent CSRF / authorization-code injection attacks. The flow is:

1. `OAuthLauncher.launch(context)` generates a cryptographically random `state` (UUID v4 or `SecureRandom.nextBytes(16).toBase64()`), persists it to `feishu_oauth_prefs` under key `KEY_OAUTH_STATE_<requestId>` with a 5-minute TTL timestamp, then opens the Feishu authorize URL with the `state` query parameter.
2. The hosted callback page (`https://xiaozha.nananxue.cn/.../feishu-callback/`) forwards the `state` and `code` back to the app via the custom scheme `com.yy.writingwithai://feishu/callback?code=...&state=...`.
3. `OAuthCodeReceiver.onReceive(intent)` reads `state` from `intent.data`, looks up the stored state by the current request id, MUST verify equality (`stored == received`), then proceeds with code exchange. If state is missing, expired (>5 min), or mismatched, the receiver MUST NOT call `exchangeCode(...)` and MUST emit `FeishuAuthState.FAILED(reason = "OAuth state validation failed")`.

#### Scenario: Valid state proceeds to token exchange
- **WHEN** `OAuthLauncher` persists `state = "abc123"` and the browser returns `com.yy.writingwithai://feishu/callback?code=X&state=abc123` within 5 minutes
- **THEN** `OAuthCodeReceiver` MUST verify `stored("abc123") == received("abc123")`, call `FeishuAuthStore.exchangeCode(code)`, then clear the stored state entry

#### Scenario: Missing state rejected
- **WHEN** the browser returns `com.yy.writingwithai://feishu/callback?code=X` with no `state` parameter
- **THEN** `OAuthCodeReceiver` MUST emit `FAILED("OAuth state validation failed")` and MUST NOT call `exchangeCode(code)`

#### Scenario: Mismatched state rejected
- **WHEN** the browser returns `com.yy.writingwithai://feishu/callback?code=X&state=evil`
- **THEN** `OAuthCodeReceiver` MUST emit `FAILED("OAuth state validation failed")` and MUST NOT call `exchangeCode(code)`

#### Scenario: Expired state rejected
- **WHEN** `OAuthLauncher` persisted `state = "abc123"` 6 minutes ago and the browser returns with `state=abc123`
- **THEN** `OAuthCodeReceiver` MUST emit `FAILED("OAuth state validation failed")` and MUST NOT call `exchangeCode(code)`

#### Scenario: State cleared after successful exchange
- **WHEN** token exchange succeeds
- **THEN** the stored state entry MUST be deleted from `feishu_oauth_prefs` to prevent replay

### Requirement: State stored only in EncryptedSharedPreferences, never Intent extras

The OAuth `state` value MUST be persisted to `feishu_oauth_prefs` (EncryptedSharedPreferences), MUST NOT be passed via `Intent.putExtra(...)`, and MUST NOT be logged at any level.

#### Scenario: State not in intent
- **WHEN** `OAuthLauncher` launches the browser Intent
- **THEN** grep of `OAuthLauncher.kt` for `putExtra` MUST return 0 matches related to the `state` value

### Requirement: AuthInterceptor refresh on async dispatcher with timeout

`core/feishu/api/AuthInterceptor` MUST NOT call `runBlocking` directly on the OkHttp dispatcher thread. Instead, it MUST wrap the token fetch in `runBlocking(Dispatchers.IO)` and apply `withTimeoutOrNull(5_000)` so a stuck refresh does not pin an OkHttp worker beyond 5 seconds.

#### Scenario: Normal refresh completes
- **WHEN** cached token valid and no refresh needed
- **THEN** interceptor returns `Authorization: Bearer <token>` within milliseconds

#### Scenario: Slow refresh truncated
- **WHEN** remote token endpoint stalls > 5 seconds
- **THEN** interceptor proceeds with stale token (or aborts request) within ~5s; OkHttp worker released

### Requirement: UserTokenProvider state is mutex-protected

`UserTokenProvider` MUST consolidate token state (`token`, `expiresAt`, `invalidated`) into a single `data class TokenState` protected by one `Mutex`. All reads / writes / invalidations MUST go through `mutex.withLock { ... }`. The `@Volatile Boolean invalidated` pattern is removed.

#### Scenario: Concurrent invalidate + read consistent
- **WHEN** thread A calls `invalidate()` while thread B calls `getToken()` concurrently
- **THEN** the `getToken()` either returns the pre-invalidate token OR a freshly-fetched token, never a half-state where `invalidated = true` but stale `token` exposed

### Requirement: expires_in parse failure has conservative fallback

`UserTokenProvider.exchangeCode()` MUST treat malformed/missing `expires_in` field as a fallback of `60 seconds` (forcing refresh on next call) rather than the historical 7000s default that silently accepted 2-hour tokens. The fallback MUST log a `WARN` with the raw payload sample for telemetry.

#### Scenario: expires_in missing
- **WHEN** token response body has no `"expires_in"` field
- **THEN** token `expiresAt = now + 60_000ms` and a WARN log is emitted

#### Scenario: expires_in malformed
- **WHEN** `"expires_in": "forever"` (non-numeric)
- **THEN** same fallback: 60s + WARN log

### Requirement: appSecret stored in-memory only, not in EncryptedSharedPreferences

`FeishuAuthStore.persistAppSecret(secret)` MUST store the secret in an in-memory `ConcurrentHashMap<requestId, Pair<secret, expiresAt>>` rather than `EncryptedSharedPreferences`. The persistent `KEY_SECRET` key is removed. After successful `exchangeCode` the entry is cleared. Crash / process restart MUST drop the entry; user re-authorizes via OAuth to obtain a fresh secret.

#### Scenario: persistAppSecret is RAM-only
- **WHEN** caller invokes `persistAppSecret("app-secret")`
- **THEN** grep of `FeishuAuthStore.kt` for `EncryptedSharedPreferences.edit().putString(KEY_SECRET` MUST return 0 matches

#### Scenario: Process restart drops secret
- **WHEN** app process is killed and restarted
- **THEN** `getAppSecretSnapshot()` returns `null`; user must complete OAuth flow again

#### Scenario: Per-request scoping
- **WHEN** two concurrent OAuth flows each call `persistAppSecret(secret, requestId = "req1")` and `persistAppSecret(secret2, requestId = "req2")`
- **THEN** `getAppSecretSnapshot("req1")` returns `secret`; `getAppSecretSnapshot("req2")` returns `secret2`; no cross-contamination

### Requirement: OAuth re-delivery persists pending state before resume
The system SHALL persist the pending OAuth exchange to disk (`persistPendingExchange`) on the re-delivery path BEFORE launching `performExchange`, ensuring that a process crash during the resumed exchange still leaves a resumable pending record. The persisted payload MUST equal the original pending (code, appId, secret, requestId).

#### Scenario: Re-delivery path re-persists before launching
- **WHEN** `OAuthCodeReceiver.onCreate` detects a pending exchange via `hasPendingExchange()` and `consumePendingExchange()` returns a non-null pending
- **THEN** the receiver calls `authStore.persistPendingExchange(pending.code, pending.appId, pending.secret, pending.requestId)` and awaits the result (synchronous via `lifecycleScope.launch{...}.join()`)
- **AND** only after successful re-persist does it call `appScope.launch { performExchange(...) }` and `finish()`

#### Scenario: Second crash during re-delivered exchange is resumable
- **WHEN** the process is killed during the `performExchange` launched on the re-delivery path
- **THEN** on next receiver cold start, `hasPendingExchange()` returns true
- **AND** `consumePendingExchange()` returns the re-persisted pending with the same code/appId/secret
- **AND** the user can complete the OAuth flow without restarting from scratch

#### Scenario: Re-delivery with expired pending falls through to state validation
- **WHEN** `hasPendingExchange()` returns true but `consumePendingExchange()` returns null (pending TTL expired)
- **THEN** the receiver does NOT re-persist
- **AND** falls through to the normal `consumeOAuthState()` validation path (which may also fail and show "state expired" toast)

### Requirement: FeishuSyncLogSection visibility tied to connection state

The `FeishuSyncLogSection` Composable MUST only render when `FeishuAuthState.connected == true`. When disconnected, the settings page MUST render only the "连接飞书" CTA, hiding any sync log content.

#### Scenario: Connected settings page shows sync log section
- **WHEN** `FeishuAuthState.connected = true` and user opens `FeishuAuthScreen`
- **THEN** screen MUST render `FeishuSyncLogSection(eventDao)` showing last 20 sync events (newest first) with timestamp / direction / status / error columns
- **AND** each event row MUST display direction icon (PUSH / PULL) and status badge (SUCCESS / FAILURE / CONFLICT)
- **AND** disclaimer line "同步不消耗 AI token，只调飞书 API" MUST be visible above the list

#### Scenario: Disconnected settings page hides sync log
- **WHEN** `FeishuAuthState.connected = false`
- **THEN** `FeishuSyncLogSection` MUST NOT be rendered;screen MUST show only "连接飞书" CTA

