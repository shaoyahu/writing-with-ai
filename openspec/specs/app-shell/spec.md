# app-shell

## Purpose

TBD — synced from OpenSpec change `init-android-project`(2026-06-18)。原 change 在 `openspec/changes/archive/2026-06-18-init-android-project/`。

应用入口骨架:`WritingApp`(`@HiltAndroidApp`)+ `MainActivity`(`ComponentActivity` + `setContent { App() }`)+ `AppNav.kt` 空 NavHost;`App()` 承载整个应用根 Composable。

## Requirements

### Requirement: Application class is Hilt-enabled

The app MUST define a single `Application` subclass annotated with `@HiltAndroidApp`, registered as the application class in `AndroidManifest.xml`.

#### Scenario: Hilt application registered
- **WHEN** the manifest `app/src/main/AndroidManifest.xml` is inspected
- **THEN** the `application` element has `android:name=".app.WritingApp"` (or fully-qualified `com.yy.writingwithai.app.WritingApp`)

#### Scenario: WritingApp class annotated
- **WHEN** the `WritingApp` class file is inspected
- **THEN** it is declared as `class WritingApp : Application()` AND it carries the `@HiltAndroidApp` annotation

### Requirement: MainActivity hosts a Compose root

The app MUST have a single `MainActivity` extending `ComponentActivity` whose `onCreate` calls `setContent { App() }`; the `App()` Composable is the root of the entire Compose tree.

#### Scenario: Single Activity entry
- **WHEN** the manifest is inspected
- **THEN** exactly one `<activity>` element exists (the launcher), with `android:name=".app.MainActivity"`

#### Scenario: Compose root invoked
- **WHEN** `MainActivity.onCreate` runs
- **THEN** it calls `setContent { App() }` where `App()` is a top-level `@Composable` function in the `app/` package

### Requirement: AppNav defines an empty NavHost

`AppNav.kt` MUST define a `NavHostController`-based `NavHost` with at least one placeholder route, ready for subsequent changes to add real destinations.

#### Scenario: NavHost instantiated
- **WHEN** the `App()` Composable is rendered
- **THEN** `AppNav(...)` is invoked inside the Material 3 themed surface AND the `NavHost` has at least one destination that displays a placeholder Composable (e.g., "writing-with-ai" greeting)

#### Scenario: Back press behavior is system-driven
- **WHEN** the user triggers system back gesture on the only destination
- **THEN** the activity finishes (predictive back gesture is wired by `enableOnBackInvokedCallback = true` in M0; system back handling is delegated to `NavHostController`; M4's `predictive-back-gesture` change will refine this)