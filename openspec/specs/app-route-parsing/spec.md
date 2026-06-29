# app-route-parsing Specification

## Purpose
TBD - created by archiving change hardening-sse-and-widget-init. Update Purpose after archive.
## Requirements
### Requirement: Widget launch route as sealed type
The system SHALL represent widget launch routes as a sealed type `WidgetLaunchRoute` with exhaustive variants `NewNote`, `OpenNote(noteId: Long)`, `EditNote(noteId: Long, prefillFocus: Boolean = false)`. All callers MUST use this type instead of raw string route parsing.

#### Scenario: Widget intent parsed into sealed route
- **WHEN** the widget receiver receives an intent with `EXTRA_LAUNCH_ROUTE` extra
- **THEN** `WidgetIntentHelpers.parseLaunchRoute(intent)` returns a non-null `WidgetLaunchRoute` instance based on the extra's tag (`new_note` → `NewNote`, `open_note:<id>` → `OpenNote`, `edit_note:<id>:<prefill>` → `EditNote`)
- **AND** unparseable extras cause the helper to return null and the receiver logs `Log.w(TAG, "unparseable widget route: $extra")` without launching

#### Scenario: AppNav consumes sealed route via when
- **WHEN** `AppNav` receives a `WidgetLaunchRoute` from the receiver
- **THEN** it dispatches via `when (route) { is NewNote -> ...; is OpenNote -> ...; is EditNote -> ... }` covering all variants exhaustively
- **AND** the compiler enforces exhaustiveness, so adding a new variant produces a compile error in `AppNav`

#### Scenario: Removed string prefix parsing
- **WHEN** developers search the codebase for `route.startsWith("quicknote/")` or `route.contains("prefillFocus=true")`
- **THEN** zero matches are found in production code paths
- **AND** the only `route: String` overloads of `WidgetIntentHelpers.launchWithTaskStack` are removed

