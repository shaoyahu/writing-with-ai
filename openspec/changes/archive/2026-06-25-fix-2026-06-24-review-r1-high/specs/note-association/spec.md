## ADDED Requirements

### Requirement: BackfillScheduler flag written only after Worker success

`core/note/backfill/BackfillScheduler.scheduleLinkBackfillIfNeeded()` / `scheduleEntityBackfillIfNeeded()` MUST NOT write `PREF_BACKFILL_DONE = true` synchronously before enqueueing WorkManager. The flag write MUST happen inside the corresponding Worker `doWork()` after `Result.success()`. If `workManager.enqueueUniqueWork()` returns a failed future, the flag MUST remain `false` and a retry happens on next cold start.

#### Scenario: Enqueue success then worker succeeds
- **WHEN** `scheduleLinkBackfillIfNeeded` enqueues `BackfillWorker` and worker `doWork()` returns `Result.success()`
- **THEN** `PREF_BACKFILL_DONE = true` is written in the worker's onSuccess callback (NOT in the scheduler)

#### Scenario: Enqueue throws
- **WHEN** `workManager.enqueueUniqueWork` throws `IllegalStateException` (WorkManager not initialized)
- **THEN** `PREF_BACKFILL_DONE` remains `false`; next cold start retries enqueue

### Requirement: BackfillScheduler cancel targets real tag

`BackfillScheduler.scheduleEntityBackfillIfNeeded()` MUST construct the `OneTimeWorkRequest` with `.addTag(ENTITY_BACKFILL_TAG)` so `cancelEntityBackfill()` (which calls `cancelAllWorkByTag(ENTITY_BACKFILL_TAG)`) is a real cancel rather than a no-op.

#### Scenario: Schedule then cancel effective
- **WHEN** `scheduleEntityBackfillIfNeeded` enqueues with tag, then `cancelEntityBackfill` called
- **THEN** grep WorkManager logs shows `cancelAllWorkByTag` matched the request; the worker is cancelled