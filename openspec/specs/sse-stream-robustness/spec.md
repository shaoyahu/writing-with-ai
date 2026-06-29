# sse-stream-robustness Specification

## Purpose
TBD - created by archiving change hardening-sse-and-widget-init. Update Purpose after archive.
## Requirements
### Requirement: SSE truncation detection
The system SHALL detect SSE stream truncation (socket disconnect mid-event, missing trailing newline) and emit `SseEvent.Error` instead of `SseEvent.Done`, so that downstream consumers treat the incomplete payload as a failure rather than a success.

#### Scenario: Clean termination via [DONE] sentinel
- **WHEN** the SSE source provides a complete event terminated by `\n\n` and then a `[DONE]` sentinel
- **THEN** `SseParser` emits `SseEvent.Data` for the payload and `SseEvent.Done` once

#### Scenario: Clean termination via blank line without [DONE]
- **WHEN** the SSE source provides a complete event terminated by `\n\n` and then the source is exhausted
- **THEN** `SseParser` emits `SseEvent.Data` and then `SseEvent.Done` (blank line itself marks clean event boundary)

#### Scenario: Truncation mid-event (no trailing newline)
- **WHEN** the SSE source provides a partial `data:` line and the source is then exhausted without a trailing newline or `[DONE]` sentinel
- **THEN** `SseParser` MUST NOT emit `SseEvent.Done`. It MUST emit `SseEvent.Error` carrying `EOFException` with message "SSE stream truncated" before terminating

#### Scenario: Truncation before any data
- **WHEN** the SSE source is exhausted without any `data:` line or `[DONE]` sentinel
- **THEN** `SseParser` emits `SseEvent.Done` (no data to flag as truncated, EOF is acceptable end)

### Requirement: SSE downstream error surfacing
The system SHALL surface SSE truncation errors to the UI as a failed AI state (`AiActionUiState.Failed`) with a localized "stream_truncated" error code, NEVER as `Done`.

#### Scenario: Truncation flows to Failed UI state
- **WHEN** `AnthropicCompatibleAdapter` receives `SseEvent.Error(EOFException)` mid-stream
- **THEN** `CoreAiGateway` translates it to `AiStreamEvent.Failed` with `AiError.StreamTruncated`
- **AND** `AiActionViewModel` transitions to `AiActionUiState.Failed` (not `Done`)

#### Scenario: History records truncated call as failure
- **WHEN** a stream ends in truncation
- **THEN** `CoreAiGateway` records the history entry with `error = "stream_truncated"` and `output_text = <partial accumulated text>`
- **AND** the user can see the failure reason in the history list

