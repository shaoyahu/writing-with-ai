# widget-init-race Specification

## Purpose
TBD - created by archiving change hardening-sse-and-widget-init. Update Purpose after archive.
## Requirements
### Requirement: Widget repository injection via Hilt EntryPoint
The system SHALL inject `QuickNoteWidgetRepository` into widget code paths via `HiltWorkerFactory` + `EntryPointAccessors.fromApplication`, removing the global mutable `QuickNoteWidgetHiltBridge.repository` field. The repository MUST be accessible from both the widget receiver process and the worker process without timing dependencies on `Application.onCreate` completion.

#### Scenario: Worker resolves repository via EntryPoint
- **WHEN** `QuickNoteWidgetWorker` is invoked by WorkManager (process boot or periodic)
- **THEN** it resolves `WidgetEntryPoint.repository()` via `EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)`
- **AND** it does NOT read any global mutable state

#### Scenario: Glance widget resolves repository via EntryPoint
- **WHEN** `QuickNoteWidget.provideGlance` is invoked
- **THEN** it resolves `WidgetEntryPoint.repository()` via `EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)`
- **AND** if the resolution throws (Application not initialized), it logs `Log.w(TAG, "...")` and emits `Result.failure()` to let Glance render the default empty widget

### Requirement: Widget worker error classification
The system SHALL classify widget worker errors as transient (retry with backoff) or fatal (mark failure, do not retry). Transient errors include `IOException` and `SQLiteException`; all other `Throwable`s are treated as fatal. `CancellationException` MUST always be re-thrown.

#### Scenario: Transient IO error triggers retry
- **WHEN** `QuickNoteWidget().updateAll()` throws `IOException` (e.g. disk full, file lock)
- **THEN** `QuickNoteWidgetWorker.doWork` returns `Result.retry()` after `Log.w(TAG, "transient IO failure", e)`

#### Scenario: Transient DB error triggers retry
- **WHEN** `QuickNoteWidget().updateAll()` throws `SQLiteException` (e.g. database locked)
- **THEN** `QuickNoteWidgetWorker.doWork` returns `Result.retry()` after `Log.w(TAG, "transient DB failure", e)`

#### Scenario: Fatal error marks failure
- **WHEN** `QuickNoteWidget().updateAll()` throws any other `Throwable`
- **THEN** `QuickNoteWidgetWorker.doWork` returns `Result.failure()` after `Log.e(TAG, "fatal widget update failure", e)`

#### Scenario: Cancellation re-thrown
- **WHEN** the worker is cancelled mid-`doWork`
- **THEN** `CancellationException` is re-thrown without being swallowed

### Requirement: Glance fallback on repository unavailability
The system SHALL NOT silently render an empty widget list when the repository is unavailable. The user-visible widget MUST indicate the unavailability (via default Glance empty state) and the failure MUST be observable in logcat.

#### Scenario: First-boot Glance render with cold Application
- **WHEN** Glance triggers `provideGlance` before `Application.onCreate` has fully initialized
- **THEN** the worker / widget logs `Log.w(TAG, "widget repository unavailable")`
- **AND** returns `Result.failure()` rather than `emptyList()`
- **AND** the widget shows the default Glance empty placeholder

#### Scenario: Subsequent periodic render recovers
- **WHEN** the next 15-minute periodic worker fires after Application is fully initialized
- **THEN** the repository resolves successfully
- **AND** the widget renders the actual note list

