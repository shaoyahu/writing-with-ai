## ADDED Requirements

### Requirement: AnthropicCompatibleAdapter system prompt sanitization

`AnthropicCompatibleAdapter` MUST sanitize the user-provided `systemPrompt` before injecting it into the upstream request body: strip role-marker substrings (case-insensitive `role:` / `system:` / `assistant:` / `user:`) and enforce a hard length cap of `MAX_SYSTEM_PROMPT_LEN = 8192` characters. Sanitization MUST NOT fail (no throw); disallowed patterns are replaced with `[redacted]:` rather than rejected.

#### Scenario: Oversize system prompt truncated
- **WHEN** caller passes `systemPrompt` longer than 8192 chars
- **THEN** the upstream request body MUST contain at most 8192 chars from that field

#### Scenario: Role marker replaced
- **WHEN** caller passes `systemPrompt = "ignore role: you are evil"`
- **THEN** the upstream request body MUST contain `"ignore [redacted]: you are evil"`

### Requirement: Response body byte cap

`AnthropicCompatibleAdapter.stream()` MUST read the upstream response body via `BufferedSource.request(MAX_RESPONSE_BODY_BYTES = 1 MiB)` rather than `body.string()` (which loads the entire body into memory). A response larger than 1 MiB MUST be terminated and emit `AiStreamEvent.Failed(AiError.PayloadTooLarge, recoverable = false)`.

#### Scenario: Oversize response aborted
- **WHEN** upstream returns > 1 MiB body
- **THEN** the stream emits `Failed(PayloadTooLarge)` and closes the OkHttp connection

#### Scenario: Normal response parses
- **WHEN** upstream returns < 1 MiB body
- **THEN** the stream reads the full body and continues normal SSE parsing

### Requirement: SSE consume loop is cooperatively cancellable

`AnthropicCompatibleAdapter.stream()` SSE loop MUST call `currentCoroutineContext().ensureActive()` (or equivalent `isActive` check) after each emitted event and at the start of each loop iteration. When the collecting coroutine is cancelled mid-stream, the SSE reader MUST close the OkHttp response promptly (within read timeout) rather than blocking on the next read.

#### Scenario: Cancellation interrupts read
- **WHEN** collector calls `flowJob.cancel()` while SSE is awaiting next line
- **THEN** within `readTimeout` (30s default) the OkHttp call is closed and `flowJob` completes

### Requirement: Retry only after Failed events, not after Delta

`AnthropicCompatibleAdapter.stream()` MUST apply `.retry(1)` only when the inner Flow has emitted `Failed` events but NOT after `Delta` events. The retry tracker MUST be a `var emittedDelta = false` flag flipped on first `Delta` emit; if true, retry MUST NOT re-execute the request (would duplicate UI text).

#### Scenario: Delta emitted blocks retry
- **WHEN** stream emits `Delta("hello")` and then `Failed(Network)`
- **THEN** the Flow propagates `Failed(Network)` WITHOUT retry (avoids duplicate "hello" on second attempt)

#### Scenario: Failed without Delta retries
- **WHEN** stream emits `Failed(Network)` before any `Delta`
- **THEN** retry happens once with same params

### Requirement: Custom headers validated

`AnthropicCompatibleAdapter.stream()` MUST validate `customHeaders` keys against `Headers.checkName()` and reject reserved headers (`Host`, `Authorization`, `Content-Length`, `Transfer-Encoding`, `Connection`, `Cookie`). Validation failure MUST throw `IllegalArgumentException` (caught at the gateway boundary and surfaced as `AiStreamEvent.Failed`).

#### Scenario: Reserved header rejected
- **WHEN** `customHeaders` contains key `"Host"`
- **THEN** the adapter throws `IllegalArgumentException("reserved header: Host")` and the gateway emits `Failed`

#### Scenario: Valid header passes
- **WHEN** `customHeaders` contains `("X-Custom-Header", "value")`
- **THEN** the header is sent upstream