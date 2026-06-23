# note-association Specification

## Purpose
TBD - created by archiving change note-association. Update Purpose after archive.
## Requirements
### Requirement: Note links storage schema
The system SHALL persist directed links between notes in a `note_links` table with composite primary key `(srcNoteId, dstNoteId, linkType)`. Each row MUST carry `linkType` (enum: `WIKILINK` | `TAG_OVERLAP` | `CONTENT_SIM` | `LLM_EXTRACT`), `weight` (Float in [0,1]), `createdAt`, `updatedAt`, and an optional `evidence` JSON string. Foreign keys to `notes(id)` on both `srcNoteId` and `dstNoteId` MUST use `ON DELETE CASCADE`. Indices MUST exist on `srcNoteId`, `dstNoteId`, and `linkType`.

#### Scenario: Save a link row
- **WHEN** `NoteLinker` writes a link of type `WIKILINK` from note A to note B
- **THEN** a row exists with `(srcNoteId=A, dstNoteId=B, linkType='WIKILINK', weight=1.0)`

#### Scenario: Cascade on note deletion
- **WHEN** note B is deleted
- **THEN** every `note_links` row where `srcNoteId=B` or `dstNoteId=B` is removed

#### Scenario: Duplicate signal rejected
- **WHEN** a second `TAG_OVERLAP` row is written for the same `(A, B)` pair
- **THEN** the existing row's `weight`, `updatedAt`, and `evidence` are replaced (not duplicated)

### Requirement: Note content full-text search
The system SHALL mirror `notes.title` and `notes.content` into a FTS5 virtual table `notes_fts` via Room `@Fts5(contentEntity = NoteEntity::class)` with the tokenizer `unicode61 remove_diacritics 2`. The FTS table MUST be kept in sync with `notes` on insert/update/delete via Room's contentEntity linkage.

#### Scenario: New note is searchable
- **WHEN** a note with title "hiking trip" and body mentioning "alps" is saved
- **THEN** querying `notes_fts MATCH 'alps'` returns that note

#### Scenario: Edits propagate to FTS
- **WHEN** a note's `content` is updated
- **THEN** the corresponding `notes_fts` row reflects the new content without manual reindex

### Requirement: Note linker SPI
The system SHALL expose `NoteLinker` interface with four methods: `suspend fun recomputeForNote(noteId: String)`, `suspend fun recomputeAll(): Int`, `suspend fun getRelated(noteId: String, limit: Int): List<RelatedNote>`, `suspend fun getBacklinks(noteId: String, limit: Int): List<RelatedNote>`. `RelatedNote` MUST carry `noteId`, `title`, `preview` (first 80 chars of content), `score` (Float), and `signals` (Set<LinkType>).

#### Scenario: Hilt provides default impl
- **WHEN** a class injects `NoteLinker`
- **THEN** the bound instance is `CompositeNoteLinker`

#### Scenario: Recompute for one note
- **WHEN** `recomputeForNote("A")` is called
- **THEN** all existing `note_links` rows with `srcNoteId="A"` are removed, then re-populated based on the current state of note A

#### Scenario: Recompute all
- **WHEN** `recomputeAll()` is called on a database with 100 notes
- **THEN** every `note_links` row is regenerated; the return value equals the number of processed notes

### Requirement: Local link signals (tag overlap and content similarity)
The system SHALL compute two local link signals without invoking any AI: (1) `TAG_OVERLAP` with `weight = jaccard(tags(src), tags(dst))` for every other note sharing ≥1 tag, (2) `CONTENT_SIM` with `weight = 1 - normalize(bm25(notes_fts, src.content))` for the top-K (default 20) FTS matches. Both signals SHALL be computed during the write path and persisted to `note_links`.

#### Scenario: Jaccard weight range
- **WHEN** note A has tags `{x, y, z}` and note B has tags `{y, z, w}`
- **THEN** the `TAG_OVERLAP` weight for A→B is `0.5` (= 2/4)

#### Scenario: Identical tag sets
- **WHEN** two notes share the exact same tag set
- **THEN** the `TAG_OVERLAP` weight is `1.0`

#### Scenario: No shared tag
- **WHEN** two notes have disjoint tag sets
- **THEN** no `TAG_OVERLAP` row is written

#### Scenario: FTS top-K cap
- **WHEN** note A has 1000 FTS matches
- **THEN** at most 20 `CONTENT_SIM` rows are written for A

### Requirement: Read query aggregation
The system SHALL compute a final relevance score per candidate note via SQL aggregation: `score = 1.00 * hasWikilink + 1.50 * tagOverlapWeight + 1.00 * contentSimWeight + 0.80 * llmExtractWeight`, where each component is the maximum weight observed for the corresponding `linkType` (or 0 if no row). Results MUST be filtered with `HAVING score > 0.10`, ordered by `score DESC`, and limited to `limit` (default 10). All weight coefficients MUST be defined in `core/note/config/LinkWeights.kt`.

#### Scenario: Wikilink dominates
- **WHEN** note A has a `WIKILINK` to B and weak `TAG_OVERLAP` to C
- **THEN** in `getRelated("A")` the relative order places B above C

#### Scenario: Threshold filters noise
- **WHEN** candidate X has aggregate score 0.05
- **THEN** X is excluded from `getRelated` results

#### Scenario: Backlinks are inbound only
- **WHEN** `getBacklinks("B")` is called
- **THEN** results include every note X for which a `note_links` row exists with `srcNoteId=X` and `dstNoteId=B`, regardless of `linkType`

### Requirement: Write path fan-out
The system SHALL trigger `noteLinker.recomputeForNote(noteId)` at the end of `NoteRepository.save(note)`. The fan-out MUST run in `applicationScope` on `Dispatchers.IO`, MUST remove all existing `note_links` rows for the source note first, MUST compute and upsert all local signals (WIKILINK / TAG_OVERLAP / CONTENT_SIM) in parallel, and MUST NOT block the calling coroutine beyond the trigger.

#### Scenario: Save triggers recompute
- **WHEN** `NoteRepository.save(noteA)` returns successfully
- **THEN** `note_links` rows for `srcNoteId=noteA.id` are eventually regenerated (within 200ms for 1k notes target)

#### Scenario: Debounce rapid saves
- **WHEN** `save(noteA)` is called 5 times within 500ms with the same note id
- **THEN** only one `recomputeForNote(noteA.id)` is dispatched

#### Scenario: No recompute on save error
- **WHEN** `NoteRepository.save(noteA)` throws
- **THEN** no `recomputeForNote` is triggered for that note

### Requirement: Wikilink syntax
The system SHALL recognize wikilinks in note content as the regex `\[\[([^\[\]\n]+?)\]\]` (single line, non-greedy, no nesting). On write, the system SHALL resolve each wikilink target to a note id via `SELECT id FROM notes WHERE LOWER(title) = LOWER(?) ORDER BY updatedAt DESC LIMIT 1` and write a `WIKILINK` row in `note_links` for each successful resolution.

#### Scenario: Resolved wikilink
- **WHEN** note A's content contains `[[Project Plan]]` and a note titled "Project Plan" exists
- **THEN** a `WIKILINK` row exists with `srcNoteId=A.id, dstNoteId=<project plan id>, weight=1.0`

#### Scenario: Unresolved wikilink
- **WHEN** note A's content contains `[[Nonexistent]]` and no note has that title
- **THEN** no `WIKILINK` row is written; the original `[[Nonexistent]]` text is preserved in `notes.content` unchanged

#### Scenario: Case-insensitive match
- **WHEN** a wikilink target is `[[project plan]]` (lowercase) and a note titled "Project Plan" exists
- **THEN** resolution succeeds

#### Scenario: Multiple matches pick latest
- **WHEN** two notes share the title "Project Plan"
- **THEN** resolution selects the one with the larger `updatedAt`

### Requirement: Wikilink editor autocomplete
The editor screen SHALL detect the user typing `[[` and SHALL open an autocomplete popup listing up to 8 candidate note titles ranked by FTS prefix match on `title`. Selecting a candidate SHALL insert `[[<title>]]` at the cursor.

#### Scenario: Autocomplete triggers on `[[`
- **WHEN** the user types `[[` followed by one or more characters
- **THEN** a popup appears with up to 8 matching note titles

#### Scenario: Insertion on selection
- **WHEN** the user selects a candidate from the popup
- **THEN** the `[[<prefix>` text is replaced with `[[<full title>]]` at the cursor position

#### Scenario: No candidates
- **WHEN** the user types `[[xxx` and no note title matches
- **THEN** the popup shows a single "Create new note 'xxx'" action

### Requirement: LLM extraction opt-in
The system SHALL support an opt-in `LLM_EXTRACT` link signal computed by calling `AiGateway.chat(prompt, schema)` with a prompt template stored at `core/ai/prompt/note_association_prompt.kt`. The setting "保存时使用 AI 找关联" MUST default to OFF. Extraction MUST only run when this setting is enabled AND an apikey is configured in `SecureApiKeyStore`.

#### Scenario: Default off
- **WHEN** a fresh install runs `NoteRepository.save(noteA)` for the first time
- **THEN** no `LLM_EXTRACT` rows are written and no AI call is made

#### Scenario: Opt-in enables extraction
- **WHEN** the user enables the setting and apikey is configured
- **THEN** `save` triggers an LLM extraction in addition to local signals; `LLM_EXTRACT` rows are written for returned candidates with `weight = llmConfidence` and `evidence = {"reason": "..."}`

#### Scenario: Extraction skipped without apikey
- **WHEN** the user enables the setting but no apikey is configured
- **THEN** extraction is silently skipped; no error is surfaced

### Requirement: LLM extraction failure fallback
The system SHALL catch any exception from `AiGateway.chat()` during extraction, SHALL record a row in `ai_history` with `status='FAIL'`, and MUST NOT write `LLM_EXTRACT` rows or propagate the error. Local signal results MUST remain intact.

#### Scenario: Network failure does not block save
- **WHEN** LLM extraction fails with a network exception
- **THEN** the save call returns successfully; only local signals populate `note_links` for the note

#### Scenario: Bad JSON from LLM does not block save
- **WHEN** the LLM returns malformed JSON
- **THEN** the failure is logged to `ai_history`; no `LLM_EXTRACT` rows are written; local signals remain

#### Scenario: Cost is recorded
- **WHEN** LLM extraction completes (success or fail)
- **THEN** an `ai_history` row is written with the action `note-association-extract`, token counts, duration, and outcome

### Requirement: Detail page related section
The detail screen MUST render a "相关笔记" section below the note body, populated by `noteLinker.getRelated(noteId, limit = 10)`. Each entry MUST show title, a 1-line content preview, and a tap target navigating to the linked note's detail. When the result is empty, the section MUST show a "暂无关联" empty state with a "用 AI 找关联" button (P4, visible only when setting is enabled and apikey configured).

#### Scenario: Renders related notes
- **WHEN** the detail screen is opened for note A which has 3 entries in `getRelated`
- **THEN** a "相关笔记" section is visible with 3 tappable rows

#### Scenario: Empty state shows manual trigger
- **WHEN** the detail screen is opened for note A and `getRelated` returns empty AND the LLM setting is on AND apikey is configured
- **THEN** a "暂无关联" message and a "用 AI 找关联" button are visible

#### Scenario: Empty state without LLM
- **WHEN** `getRelated` returns empty AND the LLM setting is off
- **THEN** only "暂无关联" is shown; no LLM button

### Requirement: Detail page backlinks section
The detail screen MUST render a "反向链接" section below "相关笔记", populated by `noteLinker.getBacklinks(noteId, limit = 10)`. Each entry MUST show the source note's title and a 1-line content preview. When empty, the section MUST be hidden entirely (not shown as empty state).

#### Scenario: Shows backlinks
- **WHEN** note A is referenced by notes X and Y via wikilink or content similarity
- **THEN** a "反向链接" section shows entries for X and Y

#### Scenario: Hides empty section
- **WHEN** no note links to A
- **THEN** the "反向链接" section is not rendered

### Requirement: Dangling wikilink UI
The detail screen MUST resolve wikilinks from the rendered content. Unresolved targets MUST be displayed inline as `[[<title>]]` with a "未找到" affordance and a "创建新笔记" button that opens the editor pre-filled with the target title.

#### Scenario: Inline rendering
- **WHEN** the content contains `[[Project Plan]]` and the target exists
- **THEN** the text "Project Plan" is rendered as a tappable link to the target's detail

#### Scenario: Dangling link affordance
- **WHEN** the content contains `[[Nonexistent]]` and no target exists
- **THEN** the text is rendered with a visual indicator and a "创建新笔记" button is shown adjacent

### Requirement: Initial backfill
The system SHALL enqueue a `WorkManager` `OneTimeWorkRequest` on first launch (gated by a `SharedPreferences` flag) that calls `noteLinker.recomputeAll()`. The job MUST be deferred by 5 seconds, MUST be `NetworkType.NOT_REQUIRED`, and MUST process notes in batches of 50 with `setProgress` updates for observability.

#### Scenario: Backfill on first run
- **WHEN** the app starts for the first time after upgrade to the new schema
- **THEN** a WorkManager job is enqueued; a notification or in-app indicator shows progress

#### Scenario: Backfill idempotent
- **WHEN** `recomputeAll()` completes successfully
- **THEN** a `SharedPreferences` flag is set; subsequent launches skip the backfill unless schema version changes again

### Requirement: Performance budget
The system SHALL meet these performance targets on a mid-tier device (e.g. Pixel 6) with up to 1000 notes:
- `recomputeForNote` end-to-end: p95 ≤ 200ms (local signals only, LLM excluded)
- `getRelated` query: p95 ≤ 50ms
- FTS index storage: ≤ 2× the source `notes` storage

#### Scenario: Local recompute budget
- **WHEN** `recomputeForNote` is called against a 1000-note database
- **THEN** p95 wall time over 100 runs is ≤ 200ms

#### Scenario: Read query budget
- **WHEN** `getRelated` is called for a note with 50 candidate links
- **THEN** p95 wall time is ≤ 50ms

### Requirement: LLM extraction rate limit
Background LLM extraction via WorkManager SHALL enforce a per-note rate limit of 1 extraction per 24 hours, persisted via a timestamp in `SharedPreferences` or `note_links.evidence` (with a dedicated marker). If the rate limit is exceeded, the extraction MUST be skipped silently.

#### Scenario: Rate limit enforced
- **WHEN** a note was last LLM-extracted 1 hour ago and a new background pass runs
- **THEN** the note is skipped; no `LLM_EXTRACT` row is written

#### Scenario: Manual button bypasses rate limit
- **WHEN** the user taps the "用 AI 找关联" button on a note
- **THEN** extraction proceeds regardless of the last extraction time

### Requirement: Tests
The system SHALL provide unit tests for: `WikilinkParser` (resolution, dangling, case, multiline), `jaccard` (empty/disjoint/identical/partial), FTS bm25 normalization, and the read-query SQL aggregation. Integration tests SHALL cover the full save→recompute→getRelated cycle against an in-memory Room database. P4 components SHALL use a mocked `AiGateway` to verify prompt assembly, JSON parsing, success path, and failure fallback.

#### Scenario: Wikilink parser unit test
- **WHEN** `parseWikilinks("see [[A]] and [[B]]")` is called
- **THEN** it returns `["A", "B"]`

#### Scenario: Save→related integration test
- **WHEN** notes A and B with overlapping tags are saved
- **THEN** `getRelated("A")` returns B with a positive score

