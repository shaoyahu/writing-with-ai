## ADDED Requirements

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