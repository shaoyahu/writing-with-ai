## ADDED Requirements

### Requirement: QuickNoteWidgetWorker classifies errors and retries transient failures
The system SHALL classify `QuickNoteWidgetWorker.doWork` errors: `IOException` and `SQLiteException` are transient (return `Result.retry()`); all other `Throwable`s are fatal (return `Result.failure()`); `CancellationException` is always re-thrown. The worker MUST log each error with the appropriate level (transient: WARN, fatal: ERROR) including the throwable's stack trace.

#### Scenario: Transient IO retry
- **WHEN** the widget update operation throws `IOException`
- **THEN** the worker logs `Log.w(TAG, "transient IO failure", e)` and returns `Result.retry()`

#### Scenario: Transient SQLite retry
- **WHEN** the widget update operation throws `SQLiteException`
- **THEN** the worker logs `Log.w(TAG, "transient DB failure", e)` and returns `Result.retry()`

#### Scenario: Fatal error failure
- **WHEN** the widget update operation throws any other `Throwable` (e.g. `IllegalStateException`, `OutOfMemoryError`)
- **THEN** the worker logs `Log.e(TAG, "fatal widget update failure", e)` and returns `Result.failure()`

### Requirement: QuickNoteWidget reads repository via Hilt EntryPoint
The system SHALL resolve `QuickNoteWidgetRepository` inside `QuickNoteWidget.provideGlance` via `EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)`, not via a global mutable `QuickNoteWidgetHiltBridge.repository` field. When resolution fails (EntryPoint not available, Application not yet initialized), the widget MUST emit `Result.failure()` and log a warning — it MUST NOT silently return an empty list.

#### Scenario: Repository resolved via EntryPoint
- **WHEN** `QuickNoteWidget.provideGlance` runs
- **THEN** it reads `val repository = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repository()`
- **AND** it does NOT access `QuickNoteWidgetHiltBridge.repository` (the field is removed)

#### Scenario: Cold start with unavailable repository
- **WHEN** `provideGlance` runs before `Application.onCreate` has fully initialized
- **THEN** `EntryPointAccessors.fromApplication` throws (or the call returns null repository)
- **AND** the widget logs `Log.w(TAG, "widget repository unavailable")` and emits `Result.failure()`
- **AND** the user sees the default Glance empty widget placeholder rather than a fake "no notes" state
