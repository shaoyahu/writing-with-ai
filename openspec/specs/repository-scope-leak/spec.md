# repository-scope-leak Specification

## Purpose
TBD - created by archiving change hardening-sse-and-widget-init. Update Purpose after archive.
## Requirements
### Requirement: Process-level ApplicationScope via Hilt
The system SHALL provide a process-level `CoroutineScope` qualified by Hilt `@ApplicationScope` annotation. This scope is the single source for all "process-resident, fire-and-forget" coroutines.

#### Scenario: ApplicationScope is process-singleton
- **WHEN** any Hilt-injected class requests `@ApplicationScope CoroutineScope`
- **THEN** it receives the same instance throughout the process lifetime
- **AND** the scope is created at first injection with `SupervisorJob() + Dispatchers.Default`

#### Scenario: ApplicationScope cancellation on Application exit
- **WHEN** the Android process is terminated by the system
- **THEN** the `ApplicationScope` is cancelled implicitly via process death
- **AND** no coroutine leak warning is emitted because the scope is a process-singleton bound to process lifetime

### Requirement: NoteRepository no longer owns a self-managed scope
The system SHALL inject `@ApplicationScope CoroutineScope` into `NoteRepository` via Hilt constructor injection, removing the previously self-managed `CoroutineScope(SupervisorJob() + Dispatchers.IO)` field.

#### Scenario: NoteRepository construction uses injected scope
- **WHEN** Hilt instantiates `NoteRepository`
- **THEN** the constructor receives the `@ApplicationScope CoroutineScope` as a parameter
- **AND** the internal `private val scope: CoroutineScope` field references the injected scope (no self-management)

#### Scenario: recomputeFlow collects on injected scope
- **WHEN** `NoteRepository.recomputeFlow.debounce(...).collect { ... }` is started in `init`
- **THEN** it uses the injected `@ApplicationScope` (Dispatchers.Default)
- **AND** the collector is cancellable when the process dies (no manual cancellation required)

#### Scenario: No NoteRepository self-managed SupervisorJob
- **WHEN** developers grep the codebase for `CoroutineScope(SupervisorJob()` in `NoteRepository`
- **THEN** zero matches are found
- **AND** the only `SupervisorJob()` creation is in `di/ApplicationScope.kt`

