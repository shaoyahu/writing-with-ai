## ADDED Requirements

### Requirement: Label collision avoidance
The graph renderer SHALL place each node's title label on the side (right / left / above / below) that minimizes overlap with neighboring nodes' labels and with the node circle itself, choosing the side with the largest angular clearance from all other nodes within a `2 × (radius + labelWidth)` distance threshold.

#### Scenario: Center node and 1-hop on same horizontal axis
- **WHEN** the center node and a 1-hop node are placed within 100px horizontally on the canvas
- **THEN** the renderer MUST position the closer-to-overlap node's label on the opposite side (e.g., left node's label goes right, center node's label stays right but pushed further out) so the two labels do not overlap horizontally.

#### Scenario: All four quadrants occupied
- **WHEN** a node has neighbors in all four quadrants within the collision threshold
- **THEN** the renderer MUST default to the right-side offset (current behavior) as the tie-breaker to preserve scan-order stability.

### Requirement: Edge color and weight upgrade
The graph renderer MUST use a higher-contrast edge color and thicker stroke than the current `outline.copy(alpha=0.7)` / `strokeWidth=1.5+weight*2` baseline, ensuring edges remain clearly visible against both Material 3 light and dark surface colors.

#### Scenario: Light theme edge visibility
- **WHEN** the app is in light theme (Material 3 light scheme)
- **THEN** edge stroke MUST use `onSurfaceVariant.copy(alpha=0.6)` as the color and `2.5f + weight * 3f` as the width, where weight is in `[0, 1]`.

#### Scenario: Dark theme edge visibility
- **WHEN** the app is in dark theme
- **THEN** edge stroke MUST use `outlineVariant` (without alpha reduction) and the same `2.5f + weight * 3f` width.

#### Scenario: WIKILINK vs other link types preserved
- **WHEN** an edge's `linkType` is `WIKILINK`
- **THEN** the stroke MUST remain solid (no `PathEffect`); for all other link types the stroke MUST remain dashed.

### Requirement: Graph header node and edge count
The TopAppBar of `NoteGraphScreen` SHALL display, immediately below the title `关联图`, a subtitle line formatted as `X 个节点 · Y 条关联` where X is `snapshot.nodes.size` and Y is `snapshot.edges.size`, sourced from the loaded `GraphSnapshot`.

#### Scenario: Loaded graph with 3 nodes and 2 edges
- **WHEN** the view model transitions to `GraphUiState.Loaded` with a snapshot containing 3 nodes and 2 edges
- **THEN** the TopAppBar subtitle MUST show `3 个节点 · 2 条关联`.

#### Scenario: Empty graph (no nodes)
- **WHEN** the snapshot contains 0 nodes
- **THEN** the TopAppBar subtitle MUST show `0 个节点 · 0 条关联` (the empty-state body copy explains why).

#### Scenario: Singular noun
- **WHEN** the snapshot contains exactly 1 node
- **THEN** the subtitle MUST use `1 个节点` (singular form) and `Y 条关联`.

### Requirement: First-time guidance banner
The graph screen SHALL show a short guidance banner at the bottom of the canvas when the loaded snapshot has <= 2 total nodes (i.e., very small graphs where users are most likely confused), explaining what the graph represents and how to interact with it.

#### Scenario: Snapshot has 1 or 2 nodes
- **WHEN** `snapshot.nodes.size <= 2`
- **THEN** the renderer MUST display a non-interactive banner with text `关联图显示了通过链接、标签、实体连过来的笔记;点击节点跳转,双指捏合缩放`, positioned above the entity chip overlay (or in its place if no chips), using `surfaceContainerHigh` background and `labelMedium` text style.

#### Scenario: Snapshot has more than 2 nodes
- **WHEN** `snapshot.nodes.size > 2`
- **THEN** the guidance banner MUST NOT be shown (sufficient density self-explains the graph).

### Requirement: Empty-state actionable copy
The graph screen's empty state (snapshot with 0 nodes reachable from center) MUST replace generic "no relations" copy with an actionable hint that tells the user how to populate the graph.

#### Scenario: 0 reachable nodes
- **WHEN** the loaded snapshot's `nodes` list contains only the center node (size = 1) and 0 edges
- **THEN** the empty-state block MUST show the text `给这条笔记加 #标签 或引用其他笔记,自动生成关联图` alongside a graph-connect icon, in place of the current `note_graph_empty` text.

#### Scenario: Center node has 1-hop neighbors but no edges
- **WHEN** the snapshot has nodes but 0 edges
- **THEN** the empty-state copy MUST NOT trigger (the graph has structure, even if disconnected); the regular Loaded rendering applies.