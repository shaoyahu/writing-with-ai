## ADDED Requirements

### Requirement: Entity type catalog

The system SHALL support exactly 12 entity types: PERSON, WORK, EVENT, LOCATION, ORG, CONCEPT, DATE, URL, QUOTE, PRODUCT, TASK, NUMBER. Each type has a fixed kebab-case key prefix: `person::`, `work::`, `event::`, `loc::`, `org::`, `concept::`, `date::`, `url::`, `quote::`, `product::`, `task::`, `num::`.

#### Scenario: 12 types enumerated in prompt
- **WHEN** `LlmEntityExtractor` builds prompt via `NoteAssociationPrompt.buildExtractEntities(title, content, locale)`
- **THEN** the system prompt MUST list all 12 types with one-line descriptions

#### Scenario: key prefix is type-bound
- **WHEN** entity row is persisted to `note_entities`
- **THEN** `entityKey` MUST start with the prefix matching `entityType` (e.g. `PERSON` → `person::*`)

### Requirement: Bilingual prompt construction

The system SHALL build the extraction prompt according to the user's app locale: when `locale == "zh"`, system prompt is Chinese; otherwise system prompt is English. The user message body is the note's original content regardless of locale.

#### Scenario: Chinese locale yields Chinese system prompt
- **WHEN** `Locale.getDefault().language == "zh"` and `buildExtractEntities` is called
- **THEN** the system prompt MUST be Chinese and include a one-line example

#### Scenario: English locale yields English system prompt
- **WHEN** `Locale.getDefault().language == "en"` and `buildExtractEntities` is called
- **THEN** the system prompt MUST be English and include a one-line example

#### Scenario: User content never enters system prompt
- **WHEN** `buildExtractEntities` is called for note N
- **THEN** the note title and content MUST appear only in the user message, never in the system message

### Requirement: Entity extraction on save

The system MUST extract entities from a note within 5 seconds of save completion when extraction is enabled and an AI provider is configured.

#### Scenario: Save triggers debounced extraction
- **WHEN** a note is saved and `NoteAssociationSettingsStore.isEntityExtractionEnabled() == true`
- **THEN** within 5 seconds the system MUST enqueue `EntityExtractor.extractAndPersist(noteId)` for that note

#### Scenario: No provider configured skips extraction silently
- **WHEN** a note is saved and `apikeyStore.observeConfiguredProviders().first()` is empty
- **THEN** the system MUST NOT call the AI gateway and MUST NOT surface an error to the user

#### Scenario: 24h rate limit per note
- **WHEN** `extractAndPersist(noteId)` is called and the note's `lastExtractedAt` is within 24 hours of now
- **THEN** the system MUST skip extraction and return 0 without making an AI call

### Requirement: Entity row persistence

The system MUST persist each extracted entity as a `NoteEntityRow(noteId, entityType, entityKey, surfaceForm, spanStart, spanEnd, lastExtractedAt)`. The composite primary key is `(noteId, entityKey)`; duplicate inserts use `OnConflictStrategy.REPLACE`.

#### Scenario: Surface form preserved for UI display
- **WHEN** LLM returns `{"type":"WORK","key":"work::sanguo","surface":"《三国演义》"}`
- **THEN** the row MUST store `surfaceForm = "《三国演义》"` so the UI can render the original surface text

#### Scenario: Key normalization applied post-extraction
- **WHEN** LLM returns an entity key with uppercase letters or whitespace
- **THEN** the system MUST normalize to lowercase and replace `[^a-z0-9_]` with `_` before persisting

### Requirement: Entity alias merging

The system MUST support user-initiated alias merging via the `entity_aliases` table. Given an alias `aliasKey`, lookups MUST treat it as equivalent to `canonicalEntityKey` for hit computation.

#### Scenario: Hit query unions canonical and aliases
- **WHEN** note A has `entityKey = "person::xiaoming"` and an alias row `("person::xiaoming_alias", "person::xiaoming")` exists
- **THEN** a query for shared entities MUST match notes containing either `person::xiaoming` or `person::xiaoming_alias`

### Requirement: Historical backfill worker

The system MUST enqueue an `EntityBackfillWorker` exactly once when the Room schema version transitions to v4. The worker MUST iterate every note whose `lastExtractedAt` is 0 or earlier than the schema upgrade timestamp, calling `extractAndPersist` sequentially with at least 5 seconds delay between notes.

#### Scenario: Backfill processes all stale notes
- **WHEN** the worker runs and 100 notes have `lastExtractedAt == 0`
- **THEN** the worker MUST process all 100 notes, persisting extracted entities for each, and MUST NOT re-process notes updated after `lastExtractedAt`

#### Scenario: Backfill is cancellable
- **WHEN** the user invokes "取消回填" from settings
- **THEN** the system MUST cancel the work via `WorkManager.cancelAllByTag("entity_backfill")` and persist partial progress (already-processed notes keep their entities)

#### Scenario: Backfill reports progress
- **WHEN** the worker is processing note N out of total T
- **THEN** `WorkInfo.progress` MUST include `processed = N`, `total = T`, `currentNoteId = note N's id`

### Requirement: Graceful failure handling

The system MUST NOT block note save when entity extraction fails. Failures (LLM timeout, non-JSON response, network error) MUST be silently dropped and the save MUST succeed.

#### Scenario: Non-JSON response drops extracted entities
- **WHEN** the LLM returns text that fails `Json.decodeFromString` for the entity response schema
- **THEN** the system MUST log the failure and MUST NOT persist any entity rows for that note

#### Scenario: Exception during extraction does not propagate
- **WHEN** `extractAndPersist(noteId)` throws any exception other than `CancellationException`
- **THEN** the system MUST catch it, return 0, and MUST NOT propagate to the save call site