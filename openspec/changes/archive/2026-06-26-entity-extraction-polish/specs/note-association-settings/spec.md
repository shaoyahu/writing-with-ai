## Purpose

Defines the settings page exposing the configurable `NoteAssociationSettingsStore.threshold` slider, backfill pause toggle, manual re-run trigger, and live progress display via `WorkManager` subscription.

## ADDED Requirements

### Requirement: Threshold slider in settings

The settings page MUST provide a slider for `threshold` ranging from `0.05` to `0.80` in `0.05` increments. The slider's initial value MUST equal `NoteAssociationSettingsStore.threshold()`. Drag-finish (`onValueChangeFinished`) MUST persist the new value via `NoteAssociationSettingsStore.setThreshold()`. Slider MUST display the current numeric value as a label.

#### Scenario: Slider default value loaded from store
- **WHEN** the settings page opens and `NoteAssociationSettingsStore.threshold() == 0.25`
- **THEN** the slider MUST render with handle at the `0.25` position and label MUST read `0.25`

#### Scenario: Drag finish persists value
- **WHEN** the user releases the slider at `0.50`
- **THEN** `NoteAssociationSettingsStore.setThreshold(0.50)` MUST be called and a subsequent `threshold()` call MUST return `0.50`

#### Scenario: Threshold clamped on persist
- **WHEN** `setThreshold(1.5)` is called
- **THEN** the persisted value MUST be `0.80` (clamped to slider upper bound)

### Requirement: Pause backfill toggle

The settings page MUST provide a switch bound to `NoteAssociationSettingsStore.pauseBackfill()`. Toggling the switch MUST call `setPauseBackfill(newValue)`. When `pauseBackfill() == true`, both the scheduler and the worker MUST short-circuit and MUST NOT process any new notes.

#### Scenario: Pause switch persisted
- **WHEN** the user toggles the switch from OFF to ON
- **THEN** `NoteAssociationSettingsStore.setPauseBackfill(true)` MUST be called

#### Scenario: Scheduler respects pause
- **WHEN** `pauseBackfill() == true` and the app cold-starts
- **THEN** `BackfillScheduler.scheduleEntityBackfillIfNeeded()` MUST return without enqueueing `EntityBackfillWorker`

#### Scenario: Worker self-checks pause
- **WHEN** `EntityBackfillWorker.doWork()` starts and `pauseBackfill() == true`
- **THEN** `doWork()` MUST return `Result.failure(workDataOf("reason" to "paused"))` before iterating any note

### Requirement: Manual re-run backfill button

The settings page MUST provide a button "立即重跑回填". Tapping the button MUST call `BackfillScheduler.scheduleEntityBackfillNow(force = true)`, enqueueing a fresh `EntityBackfillWorker` via `enqueueUniqueWork(NAME, REPLACE, request)`. The button MUST be disabled while a backfill is RUNNING.

#### Scenario: Force re-run replaces existing work
- **WHEN** the user taps "立即重跑回填" while a previous backfill is SUCCEEDED or absent
- **THEN** a new `EntityBackfillWorker` MUST be enqueued with `ExistingWorkPolicy.REPLACE` (cancels any QUEUED predecessor)

#### Scenario: Re-run button disabled during active run
- **WHEN** a backfill `WorkInfo.state == RUNNING`
- **THEN** the re-run button MUST be visually disabled and MUST NOT trigger a new enqueue

### Requirement: Live backfill progress display

The settings page MUST subscribe to `WorkManager.getWorkInfosByTagFlow("entity_backfill")` and render the latest `WorkInfo.progress` as a `LinearProgressIndicator` with values `processed / total` plus labels for `succeeded`, `failed`, and `WorkInfo.state` (RUNNING / ENQUEUED / SUCCEEDED / FAILED).

#### Scenario: Progress updates as worker reports
- **WHEN** `EntityBackfillWorker.setProgress(processed=5, total=20, succeeded=4, failed=1)` fires
- **THEN** the UI MUST re-render the progress bar at 25% and labels MUST show `4 succeeded, 1 failed`

#### Scenario: No active work hides progress
- **WHEN** no `WorkInfo` for tag `entity_backfill` exists
- **THEN** the progress section MUST be hidden (not zero-filled)

#### Scenario: Failed state surfaces reason
- **WHEN** the most recent `WorkInfo.state == FAILED` with `outputData["reason"] == "paused"`
- **THEN** the UI MUST display a `Text` label `已暂停` and MUST NOT auto-clear

### Requirement: Threshold default value migration

On first open of the settings page after upgrade, the page MUST check `NoteAssociationSettingsStore.threshold()` and IF the stored value is greater than `0.50`, reset it to `0.10` (SQL default). Otherwise no migration runs. The reset MUST emit a one-time toast / snackbar: "默认阈值已从 0.25 收紧到 0.10".

#### Scenario: Legacy 0.25 value auto-reset
- **WHEN** the settings page opens and stored `threshold == 0.25`
- **THEN** the slider MUST render at `0.10`, `setThreshold(0.10)` MUST be called, and a one-time info banner MUST appear

#### Scenario: User-customized 0.30 not reset
- **WHEN** stored `threshold == 0.30`
- **THEN** the slider MUST render at `0.30` and no banner / no reset MUST occur

### Requirement: Settings route navigation

`AppNav` MUST expose a route `note_association_settings`. `SettingsScreen` MUST render a row "笔记关联" that navigates to this route on tap.

#### Scenario: Navigate to settings page
- **WHEN** the user taps "笔记关联" in `SettingsScreen`
- **THEN** the back stack MUST push `note_association_settings` and the settings page MUST become visible

#### Scenario: Back from settings page
- **WHEN** the user presses the system back gesture on `note_association_settings`
- **THEN** the back stack MUST pop to `SettingsScreen` (the slider value MUST persist as last `onValueChangeFinished`)