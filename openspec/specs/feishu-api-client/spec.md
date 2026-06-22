## ADDED Requirements

### Requirement: FeishuApiClient abstraction

The system MUST define a `FeishuApiClient` interface in `core/feishu/api/` that wraps all Feishu Open API HTTP calls. It MUST expose methods for document CRUD and block-level operations.

#### Scenario: Client interface defined
- **WHEN** `FeishuApiClient` is used by `FeishuSyncService`
- **THEN** it MUST provide `createDocument(title, folderToken?): DocCreateResult`, `getBlocks(docId): List<DocxBlock>`, `appendChildren(docId, parentBlockId, children): String`, `getDocument(docId): DocMetadata`, and `batchDeleteChildren(docId, parentBlockId, childIds): Unit`

### Requirement: Auth header injection

Every `FeishuApiClient` request MUST include `Authorization: Bearer <tenant_access_token>` from `TenantTokenProvider`. If no valid credentials exist (no `app_id`/`app_secret`), the call MUST fail with `FeishuError.NotAuthorized`.

#### Scenario: Auth header present
- **WHEN** `FeishuApiClient.getBlocks(docId)` is called with a valid token in cache
- **THEN** the HTTP request MUST include `Authorization: Bearer <token>`

#### Scenario: Auth header absent (no credentials)
- **WHEN** `FeishuApiClient` is called but `FeishuAuthStore` has no `app_id` and no `app_secret`
- **THEN** the call MUST return `FeishuError.NotAuthorized` without making an HTTP request

### Requirement: Tenant token re-fetch on 401

When `FeishuApiClient` receives an HTTP 401 (or 200 with `code == 99991663`), it MUST invalidate the cached `tenant_access_token` via `TenantTokenProvider.invalidate()`, refetch a new token using the stored `app_id`/`app_secret`, and retry the original request once. Persistent 401 after retry MUST surface as `FeishuError.AuthExpired`.

#### Scenario: 401 triggers tenant token refetch
- **WHEN** `FeishuApiClient` receives HTTP 401 with a valid `app_id`/`app_secret` in store
- **THEN** it MUST call `TenantTokenProvider.invalidate()`, re-POST `/open-apis/auth/v3/tenant_access_token/internal`, replace the cached token, and retry the original request once with the new token

#### Scenario: 401 persists after refetch
- **WHEN** `FeishuApiClient` receives HTTP 401 twice (initial + retry with fresh token)
- **THEN** the call MUST return `FeishuError.AuthExpired`; the user MUST re-enter `app_id`/`app_secret` (the credentials themselves are likely wrong)

#### Scenario: 99991663 (Feishu-specific token invalid code) treated as 401
- **WHEN** `FeishuApiClient` receives HTTP 200 with body `{code: 99991663, msg: "token invalid"}`
- **THEN** it MUST trigger the same re-fetch + retry path as HTTP 401

### Requirement: Error classification

HTTP responses and Feishu response codes MUST be mapped to domain error types: HTTP 400 → `BadRequest(code, msg)`, 401 → re-fetch path (or `AuthExpired`), 403 → `Forbidden(scope)`, 404 → `NotFound(resource)`, 429 → `RateLimited(retryAfter)`, 5xx → `ServerError(code)`. Feishu body `code != 0` (e.g., `10003` invalid credentials) MUST map to `BadRequest(code, msg)`.

#### Scenario: 429 with retry-after
- **WHEN** `FeishuApiClient` receives HTTP 429 with `Retry-After: 5`
- **THEN** it MUST return `RateLimited(retryAfterSeconds = 5)`

#### Scenario: Feishu body code != 0
- **WHEN** `FeishuApiClient` receives HTTP 200 with body `{code: 10003, msg: "invalid app_id", data: {}}`
- **THEN** it MUST return `BadRequest(code = 10003, msg = "invalid app_id")`

#### Scenario: HTTP 5xx
- **WHEN** `FeishuApiClient` receives HTTP 502
- **THEN** it MUST return `ServerError(code = 502)`

### Requirement: Rate limit safety margin

A 20% safety margin MUST be enforced on the Feishu 1000 req/min/tenant_token limit: requests are voluntarily delayed when the count in the current 1-minute window reaches 800. Single-user scenarios stay well below this; the limiter is a defensive measure.

#### Scenario: Rate limit approaching
- **WHEN** 800 requests have been made in the current 1-minute sliding window
- **THEN** subsequent calls MUST be delayed until the window slides forward enough to drop below 800