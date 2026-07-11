# note-entity-link Delta Spec — note-graph-view

## MODIFIED Requirements

### Requirement: SemanticNoteLinker failures are observable

`core/note/impl/SemanticNoteLinker` (formerly `LlmNoteLinkExtractor`) MUST NOT swallow `Exception` silently. Each catch block MUST emit a `Log.w` (or `Log.e`) entry with the failure cause + an injected `ExtractionMetrics.recordFailure(op, cause)` call. Returns `0` after logging.

#### Scenario: LLM gateway IOException logged
- **WHEN** `gateway.streamWritingOp()` throws `IOException("connection reset")`
- **THEN** `Log.w(TAG, "LLM extract failed", throwable)` and `metrics.recordFailure("EXPAND", ioException)` both fire; method returns `0`

#### Scenario: TokenLimitExceeded handled separately
- **WHEN** `TokenLimitExceeded` is thrown
- **THEN** logged at `WARN` level with `maxChars` context; metrics receives `FailureKind.RateLimited` enum (not IOException)

### Requirement: DAO threshold parameter is required

`NoteLinkDao.getRelated` and `NoteLinkDao.getBacklinks` MUST accept a non-null `threshold: Double` parameter. Callers MUST NOT rely on a hardcoded fallback inside the DAO. The Room `@Query` MUST bind `:threshold` in the `HAVING` clause.

#### Scenario: Caller passes store threshold
- **WHEN** `CompositeNoteLinker.recomputeForNote(id)` invokes `getRelated(id, limit, store.threshold())`
- **THEN** the SQL MUST use the passed threshold verbatim, no substitution or clamping in the DAO

#### Scenario: DAO signature change is compile-time enforced
- **WHEN** a caller is missing the `threshold` argument
- **THEN** the Kotlin compiler MUST emit a "no value passed for parameter" error

### Requirement: NoteLinkCap respects threshold

`NoteLinkCap.enforce(candidates, cap, threshold)` MUST exclude candidates whose `score <= threshold` before applying the 2:1 ratio truncation. The default cap is `100`; the default threshold MUST equal `NoteAssociationSettingsStore.DEFAULT_THRESHOLD` (0.10).

#### Scenario: Low-score candidates dropped before cap
- **WHEN** 200 candidates have scores in `[0.05, 0.95]` and threshold is 0.25
- **THEN** candidates with score `≤ 0.25` MUST be removed; the remaining `> 0.25` candidates MUST be truncated to 100 by 2:1 ratio

#### Scenario: All candidates below threshold yields empty
- **WHEN** all candidates have score `≤ 0.10` and threshold is `0.25`
- **THEN** `enforce` MUST return `emptyList()`

### Requirement: Note-graph-view entry point on detail screen

The detail screen overflow menu (`AppActionDropdown` invoked from `QuickNoteDetailScreen`'s top app bar, see `feature/quicknote/detail/QuickNoteDetailScreen.kt:715`) MUST expose a "查看关联图" action item whose `appActionId` routes the user to `note_graph/{noteId}` via the root `NavController`. The action MUST be enabled only when the current note has at least one relation (any `NoteLinkEntity` row where `srcNoteId = noteId` or `dstNoteId = noteId`, OR at least one row in `note_entities` for `noteId`).

#### Scenario: Detail screen exposes graph entry when note has relations
- **WHEN** the user opens `QuickNoteDetailScreen` for note N and `note_links` contains any row referring to N (either as src or dst)
- **THEN** the overflow dropdown MUST contain an enabled "查看关联图" item
- **AND** selecting that item MUST call `onNavigateToGraph(noteId = N)` which `navController.navigate(NoteGraph(noteId = N))`

#### Scenario: Detail screen hides graph entry when note has no relations
- **WHEN** note N has zero rows in `note_links` (any direction) AND zero rows in `note_entities`
- **THEN** the overflow dropdown MUST NOT contain the "查看关联图" item
- **AND** the detail screen MUST expose an alternative affordance via `note_association_section_title` (existing empty state from M5-1)

### Requirement: Note-graph-view screen route exists

`app/AppNav.kt` MUST register a type-safe route `@Serializable data class NoteGraph(val noteId: String)` with a `composable<NoteGraph>` block that renders `NoteGraphScreen(noteId, onBack, onNodeTap)` in the `feature/quicknote/graph/` package. The route MUST be reachable via `navController.navigate(NoteGraph(noteId = <id>))`.

#### Scenario: Route is registered and accepts noteId
- **WHEN** `grep "AppNav.kt" "NoteGraph"` is run
- **THEN** exactly one `@Serializable data class NoteGraph(val noteId: String)` declaration AND one `composable<NoteGraph>` block in `AppNav.kt` are present

#### Scenario: Navigation from detail overflow navigates to graph screen
- **WHEN** the user taps "查看关联图" in the detail overflow and `onNavigateToGraph(N)` is called
- **THEN** `navController.navigate(NoteGraph(N))` is invoked
- **AND** the NavHost composes `NoteGraphScreen(noteId = N, ...)`
- **AND** pressing system back pops back to `QuicknoteDetail(N)` (existing behavior)

### Requirement: Note-graph-view layout shows center note, related notes, and entities

The graph screen MUST render a force-directed layout on a Compose `Canvas` containing:
- the current note as the **center node** (visually distinguished by `surfaceVariant` fill and 1.5× the radius of related nodes),
- up to 30 **1-hop related notes** as nodes, sourced from `NoteLinker.getRelated(center, 30)` ∪ `NoteLinker.getBacklinks(center, 30)` deduplicated by `noteId`,
- up to 20 **2-hop notes** as nodes, sourced from `NoteLinkDao.getRelated(hop1.id, 4, threshold)` aggregated across 1-hop nodes (excluding center + existing 1-hop), capped at 20,
- up to 8 **shared-entity chips** anchored to the center, sourced from `NoteEntityDao.getByNoteId(noteId)` taking the first 8 `surfaceForm` values.

Node radius MUST be derived as `12 + sigmoid(score) * 20` dp where `score` is the `NoteLinkDao.RelatedRow.score`. Edges MUST be drawn between connected nodes with stroke alpha 0.5 and stroke width proportional to `weight` (1–3 dp).

#### Scenario: Layout contains expected node counts
- **WHEN** the graph view loads for note N with 5 1-hop and 12 2-hop candidates
- **THEN** the rendered scene MUST contain exactly 1 center node + 5 1-hop nodes + up to 12 2-hop nodes = 18 nodes total (under the 50 cap)
- **AND** MUST contain edges between center and each 1-hop node, plus edges between 1-hop and their 2-hop neighbours

#### Scenario: Layout enforces 50-node cap
- **WHEN** the loader would otherwise produce 60+ candidate nodes
- **THEN** the final `GraphSnapshot.nodes` MUST contain at most 50 entries (1 center + 30 1-hop + 19 2-hop, or the highest-score subset)
- **AND** the `GraphSnapshot` MUST contain an attribute `truncated: Boolean = true` if any candidate was dropped

### Requirement: Note-graph-view supports pan and zoom interaction

The graph screen MUST wire `Modifier.pointerInput` with `detectTransformGestures` accepting single-pointer drag (pan) and two-finger pinch (zoom). The pan offset MUST be applied via a single `Modifier.graphicsLayer { translationX; translationY }` and zoom factor MUST be applied via `Modifier.graphicsLayer { scaleX; scaleY }`. Pan MUST be clamped to `[-2 × canvasWidth, 2 × canvasWidth]` in each axis; zoom MUST be clamped to `[0.5, 4.0]`.

#### Scenario: One-finger drag pans the canvas
- **WHEN** the user drags from `(x0, y0)` to `(x1, y1)` where the pointer is not within 12dp of any node center
- **THEN** the graph `graphicsLayer` translation MUST be offset by `(dx = x1 - x0, dy = y1 - y0)` and the scene re-renders under the new offset

#### Scenario: Two-finger pinch zooms the canvas
- **WHEN** the user pinches with centroid at `(cx, cy)` and zoom factor `z`
- **THEN** the graph `graphicsLayer` scale MUST become `scale * z` and the scene re-renders
- **AND** the zoom factor MUST be clamped to `[0.5, 4.0]`

#### Scenario: Pan offset is clamped to scene bounds
- **WHEN** pan exceeds `2 × canvasWidth` (e.g. user drags far off-screen)
- **THEN** the translation MUST be clamped so the center node never moves more than 2× canvas widths from the screen center

### Requirement: Note-graph-view tap on node navigates to that note's detail

The graph screen MUST wire `Modifier.pointerInput(nodes.toList()) { detectTapGestures(onTap = { offset -> /* hit test */ }) }` so that a tap whose hit point lies within `nodeRadius + 12dp` of a node center invokes `onNodeTap(node.noteId)` which calls `navController.navigate(QuicknoteDetail(node.noteId))`. Drag distance < 12dp MUST be classified as a tap; ≥ 12dp MUST be classified as a pan (handled by the pan detector) so the two gestures do not conflict.

#### Scenario: Tap on a non-center node navigates to that note's detail
- **WHEN** the user taps within `nodeRadius + 12dp` of node X's center on the canvas (and tap distance < 12dp)
- **THEN** `onNodeTap(X.noteId)` MUST be invoked
- **AND** `navController.navigate(QuicknoteDetail(X.noteId))` MUST push `QuicknoteDetail(X.noteId)` onto the back stack

#### Scenario: Tap on the center node is a no-op
- **WHEN** the user taps within `nodeRadius + 12dp` of the center node's center
- **THEN** `onNodeTap` MUST NOT be invoked (no self-navigation, no infinite loop)

#### Scenario: Drag above 12dp threshold does not trigger tap navigation
- **WHEN** the user touches within a node but then drags more than 12dp before releasing
- **THEN** the gesture MUST be classified as a drag and `onNodeTap` MUST NOT be invoked

### Requirement: Note-graph-view empty state

The graph screen MUST render an empty-state placeholder when `GraphSnapshot.nodes.size <= 1` (only the center node exists, no 1-hop or 2-hop candidate and zero `note_entities` rows). The placeholder MUST show `R.string.note_graph_empty` (中文: "此笔记暂无关联"; 英文: `TODO(en):` placeholder until polish) inside a centered `Surface(color = surfaceVariant, shape = RoundedCornerShape(12.dp))`.

#### Scenario: Note with no relations shows empty state
- **WHEN** the loader produces a `GraphSnapshot` containing only the center node (zero 1-hop, zero 2-hop, zero entity rows)
- **THEN** the screen MUST NOT render nodes or edges
- **AND** MUST render `R.string.note_graph_empty` text inside a centered surface, replacing the canvas content

### Requirement: Note-graph-view layout coordinates are cached

The graph screen MUST persist converged layout coordinates (positions of all nodes after `ForceLayout.converge()` returns success) into `SharedPreferences` keyed `graph_layout_<noteId_sanitized>_v1` containing a serialized `Map<String, NodeCoords>` (JSON via `kotlinx.serialization`). On subsequent entry with the same `noteId`, the loader MUST use cached coordinates as the initial state for `ForceLayout` and skip the iterative convergence if every cached node id is still present in the snapshot.

#### Scenario: First entry computes layout
- **WHEN** the user enters the graph view for note N for the first time
- **THEN** the loader reads SharedPreferences and finds NO key matching `graph_layout_N_v1`
- **AND** `ForceLayout.converge()` runs from random initial positions

#### Scenario: Second entry uses cached layout
- **WHEN** the user re-enters the graph view for note N with all cached node ids still present in the fresh snapshot
- **THEN** the loader MUST skip `ForceLayout.converge()` and emit cached coordinates as the initial `LayoutState`
- **AND** the cached coordinates MUST be written back on convergence failure + success path (re-validated each entry)

#### Scenario: Layout fallback when force-directed diverges
- **WHEN** `ForceLayout.converge()` does not reach `convergenceTolerance = 0.05` within `maxIter = 300` iterations
- **THEN** `GraphRenderer` MUST fall back to a `CircularLayout` placing the center at origin, 1-hop nodes on the outer ring (radius = 100dp) and 2-hop nodes on the inner ring (radius = 60dp) in stable angle order (sorted by `noteId.hashCode()`), so the user always sees a sensible layout even if force-directed diverges

### Requirement: Note-graph-view i18n coverage

The graph screen MUST localize all UI strings via `R.string.*` resources in `values/strings.xml` (Chinese, authoritative) and `values-en/strings.xml` (English; `TODO(en):` prefix permitted until polish). The required keys are:

- `note_graph_title`: graph screen top app bar title (中文: "关联图")
- `note_graph_empty`: empty-state copy when no relations (中文: "此笔记暂无关联")
- `note_graph_entry_action`: overflow menu entry label on detail screen (中文: "查看关联图")
- `note_graph_node_untitled`: fallback node label when title is blank (中文: "未命名")
- `note_graph_legend_related`: legend entry for "related note" node color (中文: "相关笔记")
- `note_graph_legend_entity`: legend entry for "shared entity" chip (中文: "共享实体")

#### Scenario: Strings resolve in Chinese locale
- **WHEN** the device locale is `zh` and the graph screen renders
- **THEN** `note_graph_title`, `note_graph_empty`, etc. MUST display the Chinese values from `values/strings.xml`

#### Scenario: English locale uses TODO placeholder without breaking build
- **WHEN** `values-en/strings.xml` has `TODO(en): note_graph_title` (and the other 5 keys) as values
- **THEN** the APK MUST still build and launch under English locale; the screenshot will display the placeholder string (consistent with existing `quick-note` spec "i18n coverage" Requirement)
