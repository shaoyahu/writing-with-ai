## ADDED Requirements

### Requirement: Label collision algorithm covered by JVM unit tests

The 4-direction label collision avoidance algorithm in `NoteGraphCanvas` (`computeNodeLayouts` / `LABEL_PRIORITY` / `pickFallbackDirection`) MUST be covered by JVM unit tests in `app/src/test/java/com/yy/writingwithai/feature/quicknote/graph/NoteGraphLayoutTest.kt`. To make the algorithm testable without bringing up Compose Canvas, the rendering-layer entry MUST expose a pure helper `internal fun computeNodeLayoutsFor(snapshot, coords, canvasSize: Size, density: Float): Map<String, NodeLayout>` that the existing `DrawScope.computeNodeLayouts` calls as a thin wrapper.

#### Scenario: Empty snapshot
- **WHEN** the renderer computes layouts for a snapshot with zero nodes
- **THEN** the resulting map MUST be empty and no exception is thrown.

#### Scenario: Single node, no neighbors
- **WHEN** the snapshot contains exactly one node with a non-blank title and no other nodes are within collision range
- **THEN** the node's `labelBox` MUST be non-null and placed on the `LABEL_RIGHT` side per `LABEL_PRIORITY` default.

#### Scenario: Horizontal pair forces LEFT avoidance
- **WHEN** two nodes are placed on the same horizontal axis within `2 * (radius_self + labelWidth_self)` distance
- **THEN** the left node's label MUST resolve to `LABEL_LEFT` so its label box does not overlap the right node's circle or label box.

#### Scenario: All four quadrants occupied triggers fallback
- **WHEN** a node has at least one neighbor in each of the four `LABEL_*` quadrants within the collision threshold
- **THEN** the algorithm MUST fall back to `pickFallbackDirection`, which MUST pick the `LABEL_*` whose unit vector has the smallest angular distance from the vector pointing away from the nearest neighbor; ties MUST resolve to `LABEL_RIGHT` per `LABEL_PRIORITY`.

#### Scenario: Multi-direction neighbor label check
- **WHEN** a candidate label direction for the current node overlaps the candidate's neighbor's `LABEL_RIGHT` label box but does NOT overlap the same neighbor's `LABEL_LEFT` / `LABEL_ABOVE` / `LABEL_BELOW` label boxes
- **THEN** the algorithm MUST treat the neighbor as occupying that direction (4-direction OR check) and reject the candidate direction, because the neighbor has not yet been assigned a direction in the same sweep.

#### Scenario: Untitled node has no label box
- **WHEN** a node's `title` is null or blank
- **THEN** the resulting `NodeLayout.labelBox` MUST be null and the algorithm MUST skip the collision check for that node.

### Requirement: Test architecture constraint

The collision-algorithm test MUST NOT depend on Robolectric, Compose UI test, or any Android framework class beyond `androidx.compose.ui.geometry.{Offset, Rect, Size}`. All test fixtures MUST be constructible in pure JVM (construct `GraphSnapshot` / `NodeCoords` data classes directly with literal values).

#### Scenario: Test runs in plain JVM unit test task
- **WHEN** `./gradlew :app:testDebugUnitTest` is invoked
- **THEN** `NoteGraphLayoutTest` MUST run as a normal JUnit5 test, complete in under 1 second total, and require no emulator / device.

#### Scenario: No Robolectric configuration required
- **WHEN** the developer opens `NoteGraphLayoutTest.kt` without `@RunWith(RobolectricTestRunner::class)`
- **THEN** the test class MUST compile and run; no `@Config` annotation is required.

## MODIFIED Requirements

(none — existing 5 REQUIREMENTs in `note-graph-readability` are unchanged; this delta only ADDs a testing requirement on top.)

## REMOVED Requirements

(none)