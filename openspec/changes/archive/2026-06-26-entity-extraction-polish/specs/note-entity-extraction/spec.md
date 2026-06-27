## ADDED Requirements

### Requirement: Worker self-checks pauseBackfill before iterating

`EntityBackfillWorker.doWork()` MUST read `NoteAssociationSettingsStore.pauseBackfill()` after entering `Dispatchers.IO` and BEFORE fetching note IDs. If `pauseBackfill() == true`, the worker MUST return `Result.failure(workDataOf("reason" to "paused"))` immediately without iterating any notes or persisting any state.

#### Scenario: Paused worker exits early
- **WHEN** `EntityBackfillWorker.doWork()` starts and `pauseBackfill() == true`
- **THEN** the worker MUST return `Result.failure` with `outputData["reason"] == "paused"` before the first `noteDao.observeAll().first()` call

#### Scenario: Unpaused worker proceeds
- **WHEN** `EntityBackfillWorker.doWork()` starts and `pauseBackfill() == false`
- **THEN** the worker MUST proceed to fetch note IDs and process them normally

### Requirement: BackfillScheduler respects pauseBackfill

`BackfillScheduler.scheduleEntityBackfillIfNeeded()` MUST check `NoteAssociationSettingsStore.pauseBackfill()` BEFORE checking `PREF_ENTITY_BACKFILL_DONE`. If `pauseBackfill() == true`, the method MUST return without enqueueing any work AND MUST NOT set the `PREF_ENTITY_BACKFILL_DONE` flag.

#### Scenario: Paused at startup skips enqueue
- **WHEN** the app cold-starts and `pauseBackfill() == true`
- **THEN** `scheduleEntityBackfillIfNeeded()` MUST return without enqueueing `EntityBackfillWorker`

#### Scenario: Force schedule bypasses pause guard
- **WHEN** `scheduleEntityBackfillNow(force = true)` is invoked from the settings UI button
- **THEN** the worker MUST be enqueued regardless of `pauseBackfill()` state (worker self-check still applies at doWork start)