# note-entity-link Specification

## Purpose
TBD - created by archiving change entity-extraction-association. Update Purpose after archive.
## Requirements
### Requirement: Entity-hit link type

The system MUST define a new `LinkType.ENTITY_HIT` enum value and persist edges with this type when notes share at least one entity key (after alias resolution).

#### Scenario: Shared entity creates ENTITY_HIT edge
- **WHEN** note A has `entityKey = "work::sanguoyanyi"` and note B has the same `entityKey`
- **THEN** `NoteLinkDao.upsertAll` MUST persist a row `(srcNoteId = A, dstNoteId = B, linkType = ENTITY_HIT, weight = 1.0)` with `evidence` containing the shared entity keys as JSON

#### Scenario: No shared entity creates no ENTITY_HIT edge
- **WHEN** note A and note B share no entity keys and no aliases resolve to common keys
- **THEN** the system MUST NOT persist any `ENTITY_HIT` edge between A and B

### Requirement: Aggregation score formula

The `getRelated` and `getBacklinks` SQL aggregations MUST compute score as the sum of: `ENTITY_HIT × 1.50 × max(weight)` + `WIKILINK × 1.00` + `TAG_OVERLAP × 1.50 × max(weight)` + `CONTENT_SIM × 1.00 × max(weight)` + `LLM_EXTRACT × 0.80 × max(weight)`. The `LLM_EXTRACT` weight is zero by default for new edges; existing legacy rows may contribute a positive score.

#### Scenario: Score formula weights applied
- **WHEN** note A has edges: ENTITY_HIT (w=1.0), WIKILINK, TAG_OVERLAP (jaccard=0.4) to note B
- **THEN** the row score MUST equal `1.50 × 1.0 + 1.00 + 1.50 × 0.4 = 3.10`

### Requirement: Per-note link cap of 100

The system MUST cap the per-source-note related count at 100 edges total. The cap MUST be enforced by: (a) computing the union of `ENTITY_HIT` and `LLM_EXTRACT` candidates, (b) sorting by score desc, (c) truncating to 100. The `WIKILINK`, `TAG_OVERLAP`, and `CONTENT_SIM` edges are not part of the 100-cap budget.

#### Scenario: 2:1 ratio between entity hits and semantic hits
- **WHEN** the cap is applied and `ENTITY_HIT` candidates exist
- **THEN** the system MUST take up to 66 `ENTITY_HIT` candidates (score desc) and fill remaining capacity (up to 34) with `LLM_EXTRACT` candidates (score desc), truncating the combined list to 100

#### Scenario: Insufficient entity hits fall back to all semantic
- **WHEN** fewer than 66 `ENTITY_HIT` candidates exist
- **THEN** the system MUST take all `ENTITY_HIT` candidates plus `LLM_EXTRACT` candidates up to the 100 cap, sorted by score desc

#### Scenario: No entity hits all-semantic fallback
- **WHEN** zero `ENTITY_HIT` candidates exist for a note
- **THEN** the system MUST fall back to invoking `SemanticNoteLinker` for the note and take up to 100 `LLM_EXTRACT` edges sorted by score desc

### Requirement: Configurable association threshold

The system MUST read `threshold` from `NoteAssociationSettingsStore` (DataStore) and apply `HAVING score > :threshold` in the SQL aggregation. Default value is 0.25. Valid range is 0.10 to 1.00, step 0.05.

#### Scenario: Default threshold is 0.25
- **WHEN** `NoteAssociationSettingsStore.threshold` is unset
- **THEN** `getRelated` MUST use `0.25` as the HAVING threshold

#### Scenario: Threshold filter excludes low-score rows
- **WHEN** threshold is 0.50 and a candidate row's score is 0.40
- **THEN** the row MUST NOT appear in `getRelated` / `getBacklinks` results

### Requirement: Shared-entity evidence in UI

The detail screen MUST display, for each related note surfaced via `ENTITY_HIT`, the list of shared entity surface forms (e.g. "三国演义", "小明") on long-press or hover.

#### Scenario: Detail page shows shared entities
- **WHEN** the detail screen renders related notes and a row's `evidence` JSON parses to `{"sharedEntities":["三国演义","小明"]}`
- **THEN** the row's expanded view MUST display both surface forms in a chip group

### Requirement: Threshold slider in settings

The settings page MUST provide a slider for `threshold` ranging from 0.10 to 1.00 in 0.05 increments. Slider value persists to `NoteAssociationSettingsStore` and is read by subsequent `getRelated` calls.

#### Scenario: Slider change applies immediately
- **WHEN** the user moves the threshold slider from 0.25 to 0.50
- **THEN** the next `getRelated` call MUST use 0.50 as the threshold and the detail page MUST re-render with the new filter

