## ADDED Requirements

### Requirement: LlmEntityExtractor parses strict JSON

`core/note/entity/LlmEntityExtractor.parseJsonEntities(raw)` MUST parse via `Json.parseToJsonElement(raw).jsonArray` rather than substring `[...]` extraction. If the response is not a top-level `JsonArray`, the function MUST return `emptyList()` and log a WARN with the raw prefix for debugging.

#### Scenario: Garbage between brackets rejected
- **WHEN** LLM returns `[{...}] garbage [{...}]` (model mid-stream corruption)
- **THEN** `Json.parseToJsonElement` throws OR returns non-array → `emptyList()` returned; no entity rows persisted

#### Scenario: Valid array parses
- **WHEN** LLM returns `[{"type":"PERSON","key":"alice","surface":"Alice"}]`
- **THEN** `parseJsonEntities` returns `[Triple(PERSON, "alice", "Alice")]`; entity persisted via `entityDao.upsertAll`

### Requirement: LlmEntityExtractor failures are observable

`LlmEntityExtractor.extractAndPersist` MUST emit `Log.w` + `metrics.recordFailure(op, cause)` for each caught exception. Returns `0` after logging.

#### Scenario: LLM error logged
- **WHEN** `aiGateway.streamWritingOp()` throws any `Throwable`
- **THEN** `Log.w("LlmEntityExtractor", "extract failed", throwable)` fires; metrics counter incremented; function returns `0`