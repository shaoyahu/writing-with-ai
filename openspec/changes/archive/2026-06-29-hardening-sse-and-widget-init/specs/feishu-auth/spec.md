## ADDED Requirements

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
