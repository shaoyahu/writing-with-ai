## ADDED Requirements

### Requirement: LlmNoteLinkExtractor failures are observable

`core/note/impl/LlmNoteLinkExtractor` MUST NOT swallow `Exception` silently. Each catch block MUST emit a `Log.w` (or `Log.e`) entry with the failure cause + an injected `ExtractionMetrics.recordFailure(op, cause)` call. Returns `0` after logging.

#### Scenario: LLM gateway IOException logged
- **WHEN** `gateway.streamWritingOp()` throws `IOException("connection reset")`
- **THEN** `Log.w(TAG, "LLM extract failed", throwable)` and `metrics.recordFailure("EXPAND", ioException)` both fire; method returns `0`

#### Scenario: TokenLimitExceeded handled separately
- **WHEN** `TokenLimitExceeded` is thrown
- **THEN** logged at `WARN` level with `maxChars` context; metrics receives `FailureKind.RateLimited` enum (not IOException)