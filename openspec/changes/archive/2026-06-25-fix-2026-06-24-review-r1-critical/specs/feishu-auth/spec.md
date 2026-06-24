## ADDED Requirements

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