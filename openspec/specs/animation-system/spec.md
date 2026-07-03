# animation-system Specification

## Purpose
TBD - created by archiving change animation-system-and-consent-redesign. Update Purpose after archive.
## Requirements
### Requirement: AnimationStyle persisted across processes
The system SHALL persist the user's chosen animation style in DataStore under the key `animation_style_v1` (enum name as String), and SHALL restore it on next process launch.

#### Scenario: Persist MINIMAL choice
- **WHEN** user picks `AnimationStyle.MINIMAL` in settings
- **THEN** DataStore `animation_style_v1` value SHALL equal `"MINIMAL"`
- **AND** next app launch SHALL resolve to `AnimationStyle.MINIMAL` before first frame

#### Scenario: Persist IMMERSIVE choice
- **WHEN** user picks `AnimationStyle.IMMERSIVE`
- **THEN** DataStore `animation_style_v1` value SHALL equal `"IMMERSIVE"`

#### Scenario: Unknown stored value falls back to MINIMAL
- **WHEN** DataStore `animation_style_v1` holds a String that does not match any `AnimationStyle` enum name
- **THEN** resolver SHALL return `AnimationStyle.MINIMAL`
- **AND** resolver SHALL log a warning (non-PII, single line)

#### Scenario: Missing stored value defaults to MINIMAL
- **WHEN** DataStore has no `animation_style_v1` key (fresh install or schema cleared)
- **THEN** resolver SHALL return `AnimationStyle.MINIMAL`

### Requirement: AnimationTokens provided via CompositionLocal
The system SHALL provide an `AnimationTokens` data class via `LocalAnimationTokens: CompositionLocal<AnimationTokens>` to all Composable children of `WritingAppTheme`.

#### Scenario: Tokens read at NavHost lambda
- **WHEN** `NavHost.enterTransition` lambda executes during a navigation
- **THEN** the lambda SHALL be able to read the current `LocalAnimationTokens.current.navEnter`
- **AND** the read value SHALL match the active style

#### Scenario: Style switch propagates to all readers
- **WHEN** user switches style from FLUID to IMMERSIVE in settings while SettingsScreen and a separate Switch consumer are both composed
- **THEN** BOTH consumers SHALL recompose with the new tokens
- **AND** neither consumer SHALL retain stale FLUID tokens after recomposition

#### Scenario: CompositionLocal uses compositionLocalOf
- **WHEN** `WritingAppTheme` provides `LocalAnimationTokens`
- **THEN** the implementation SHALL use `compositionLocalOf` (not `staticCompositionLocalOf`)

### Requirement: Reduce-motion forces NONE
The system SHALL force `AnimationStyle.NONE` when Android's `AccessibilityManager.isReduceMotionEnabled` is true.

#### Scenario: Reduce-motion enabled at launch
- **WHEN** `AccessibilityManager.isReduceMotionEnabled == true` on app start
- **THEN** `WritingAppTheme` SHALL provide tokens equal to `AnimationStyle.NONE.tokens()`
- **AND** the user's persisted `animation_style_v1` SHALL be preserved (not overwritten)

#### Scenario: Reduce-motion toggled at runtime
- **WHEN** user toggles reduce-motion ON in system Settings while app is in foreground
- **THEN** within 1 second the active tokens SHALL become `AnimationStyle.NONE.tokens()`

#### Scenario: Reduce-motion toggled OFF
- **WHEN** user toggles reduce-motion OFF
- **THEN** tokens SHALL revert to the user's persisted style (e.g. IMMERSIVE if persisted)

### Requirement: NavHost consumes nav tokens
The system SHALL configure `NavHost` (in `AppNav.kt` and `AppShell.kt`) to use token-driven `enterTransition` / `exitTransition` / `popEnterTransition` / `popExitTransition`.

#### Scenario: Forward navigation under MINIMAL
- **WHEN** active style is MINIMAL and user navigates from screen A → B
- **THEN** transition SHALL match `AnimationStyle.MINIMAL.tokens().navEnter` / `navExit`

#### Scenario: Pop navigation under IMMERSIVE
- **WHEN** active style is IMMERSIVE and user pops from screen B → A
- **THEN** transition SHALL match `AnimationStyle.IMMERSIVE.tokens().navPopEnter` / `navPopExit`

### Requirement: AnimatedSwitch encapsulates Switch animation
The system SHALL provide a `core/ui/AnimatedSwitch.kt` Composable that wraps Material3 `Switch` and reads `LocalAnimationTokens.current.switchSpec` for thumb-position animation. The Composable SHALL position the thumb so that the horizontal padding inside the track is symmetric: the closed-state thumb center SHALL be at `trackWidth / 2` measured from the right edge, and the open-state thumb center SHALL be at `trackWidth / 2` measured from the left edge (i.e. `(trackWidth - thumbSize) / 2` from each side).

#### Scenario: SettingsScreen uses AnimatedSwitch
- **WHEN** `SettingsScreen` renders a toggle row
- **THEN** it SHALL call `AnimatedSwitch(checked, onCheckedChange, modifier)`
- **AND** SHALL NOT call bare `Switch(...)`

#### Scenario: NoteAssociationSettingsScreen uses AnimatedSwitch
- **WHEN** `NoteAssociationSettingsScreen` renders a toggle row
- **THEN** it SHALL call `AnimatedSwitch(...)`

#### Scenario: AnimatedSwitch under NONE style
- **WHEN** active style is NONE and user taps a Switch
- **THEN** thumb SHALL snap to the new position without animation

#### Scenario: AnimatedSwitch thumb padding is symmetric
- **WHEN** the Switch is in the closed (off) state
- **THEN** the horizontal distance from the thumb's outer edge to the left edge of the track SHALL equal the horizontal distance from the thumb's outer edge to the right edge of the track within 1 dp
- **AND** the same SHALL hold for the open (on) state

### Requirement: AnimatedVisibility / AnimatedContent consumers use tokens
The system SHALL replace bare `expandVertically()` / `shrinkVertically()` in collapsible sections, and use `AnimatedContent` in tab content, with token-driven animations.

#### Scenario: CustomProviderEditScreen collapsible
- **WHEN** `CustomProviderEditScreen` renders the "模型 & 认证" collapsible section
- **THEN** `AnimatedVisibility(enter = LocalAnimationTokens.current.expandEnter, exit = collapseExit)`
- **AND** it SHALL NOT use bare `expandVertically()` / `shrinkVertically()`

#### Scenario: PromptTemplateScreen content transition
- **WHEN** user switches tabs in `PromptTemplateScreen`
- **THEN** content area SHALL use `AnimatedContent` with `tabContentSpec` from `LocalAnimationTokens.current`

### Requirement: AnimationStylePreviewScreen lists 4 styles
The system SHALL provide `feature/settings/animation/AnimationStylePreviewScreen.kt` with **4 selectable style cards only** (MINIMAL / FLUID / IMMERSIVE / NONE) acting as a visual style library. Each card SHALL present its style as a 4-of-1 single-select choice. The screen SHALL NOT expose nav/tab animation toggles; those belong to `AnimationDetailScreen`.

#### Scenario: 4 cards rendered
- **WHEN** user opens "动画风格" settings page
- **THEN** the screen SHALL render exactly 4 cards (MINIMAL / FLUID / IMMERSIVE / NONE) as a visual library
- **AND** exactly one card SHALL display a selected-state indicator driven by `RadioButton(selected = …)`
- **AND** no card SHALL render a secondary "checkmark" icon as a duplicate selected-state indicator

#### Scenario: Selecting IMMERSIVE writes DataStore
- **WHEN** user taps the IMMERSIVE card
- **THEN** `UserPrefsStore.setAnimationStyle(IMMERSIVE)` SHALL be invoked
- **AND** the active style SHALL update to IMMERSIVE

#### Scenario: Reduce-motion banner
- **WHEN** `AccessibilityManager.isReduceMotionEnabled == true`
- **THEN** the screen SHALL display a Banner explaining reduce-motion forces NONE

#### Scenario: Route registration
- **WHEN** user navigates from MyScreen to animation settings
- **THEN** route `SettingsAnimationStyle` SHALL resolve to `AnimationStylePreviewScreen`

### Requirement: AnimationDetailScreen exposes nav/tab animation toggles
The system SHALL provide `feature/settings/animation/AnimationDetailScreen.kt` with 2 independent toggle rows: "导航动画" (navigation) and "标签动画" (tab content). Each row SHALL be implemented via the reusable `AnimationToggleRow.kt` Composable in the same package, and SHALL bind to `UserPrefsStore.navAnimationsEnabled` and `UserPrefsStore.tabAnimationsEnabled` respectively. When `AccessibilityManager.isReduceMotionEnabled == true`, both toggles SHALL render as disabled (non-interactive) but visually present, and a banner SHALL explain reduce-motion forces NONE.

#### Scenario: Both toggles rendered
- **WHEN** user navigates to "动画详细" page
- **THEN** the screen SHALL render 2 toggle rows (navigation + tab) with title and description in addition to a header

#### Scenario: Nav animation toggle persists
- **WHEN** user toggles the "导航动画" `AnimatedSwitch` from ON to OFF
- **THEN** `UserPrefsStore.setNavAnimationsEnabled(false)` SHALL be invoked
- **AND** within 1 second, the active `LocalAnimationTokens.navEnter / navExit / navPopEnter / navPopExit` SHALL resolve to `EnterTransition.None / ExitTransition.None` regardless of the active `AnimationStyle`

#### Scenario: Tab animation toggle persists
- **WHEN** user toggles the "标签动画" `AnimatedSwitch` from ON to OFF
- **THEN** `UserPrefsStore.setTabAnimationsEnabled(false)` SHALL be invoked
- **AND** within 1 second, the active `LocalAnimationTokens.tabContentSpec` SHALL resolve to `snap()`

#### Scenario: Reduce-motion disables toggles
- **WHEN** `AccessibilityManager.isReduceMotionEnabled == true`
- **THEN** both toggles SHALL be displayed as disabled (non-interactive) but visually present
- **AND** a banner SHALL explain reduce-motion forces NONE

#### Scenario: AnimationToggleRow is reusable
- **WHEN** `AnimationToggleRow.kt` is committed at the same package top level as the screens
- **THEN** both `AnimationStylePreviewScreen` and `AnimationDetailScreen` (if sharing) can reference it without creating a circular import

#### Scenario: Route registration
- **WHEN** user navigates from "动画风格" page to "动画详细" page
- **THEN** route `SettingsAnimationDetail` SHALL resolve to `AnimationDetailScreen`

### Requirement: AnimationToggleRow lives at package top level
The system SHALL place `AnimationToggleRow.kt` at the same package as `AnimationStylePreviewScreen.kt` and `AnimationDetailScreen.kt` (`feature.settings.animation`), not nested in either screen's sub-folder. This ensures both screens can reuse the row without a circular import.

#### Scenario: Shared between animation pages
- **WHEN** `AnimationToggleRow.kt` exists at `feature/settings/animation/AnimationToggleRow.kt`
- **THEN** both `AnimationStylePreviewScreen.kt` and `AnimationDetailScreen.kt` in the same package SHALL be able to import it directly via `package feature.settings.animation`

#### Scenario: Disabled state visual
- **WHEN** `AnimationToggleRow(enabled = false)` is rendered
- **THEN** the row SHALL appear with reduced opacity / muted colors AND the underlying `AnimatedSwitch` SHALL ignore click events (i.e. `clickable.enabled = false`)

### Requirement: i18n parity for animation settings
The system SHALL add the 10 animation-style string keys to both `values/strings.xml` and `values-en/strings.xml` with matching key sets.

#### Scenario: Key set equality
- **WHEN** `grep -oE 'name="anim_style_[a-z_]+"' values/strings.xml | sort -u` is compared to the same command on `values-en/strings.xml`
- **THEN** the two outputs SHALL be identical (10 keys each)

### Requirement: NavAnimationsEnabled persisted across processes
The system SHALL persist the user's "导航动画" choice in DataStore under the key `nav_animations_enabled_v1` (Boolean), and SHALL restore it on next process launch. Default SHALL be `true` when the key is missing or unparseable.

#### Scenario: Toggle off persists
- **WHEN** user turns "导航动画" OFF in settings
- **THEN** DataStore `nav_animations_enabled_v1` SHALL equal `false`
- **AND** next app launch SHALL resolve to `false` before first frame

#### Scenario: Default true on fresh install
- **WHEN** DataStore has no `nav_animations_enabled_v1` key
- **THEN** resolver SHALL return `true`

#### Scenario: Corrupt value falls back to true
- **WHEN** DataStore `nav_animations_enabled_v1` holds a value that is not a valid Boolean
- **THEN** resolver SHALL return `true`
- **AND** resolver SHALL log a warning (non-PII, single line)

### Requirement: TabAnimationsEnabled persisted across processes
The system SHALL persist the user's "标签动画" choice in DataStore under the key `tab_animations_enabled_v1` (Boolean), and SHALL restore it on next process launch. Default SHALL be `true` when the key is missing or unparseable.

#### Scenario: Toggle off persists
- **WHEN** user turns "标签动画" OFF in settings
- **THEN** DataStore `tab_animations_enabled_v1` SHALL equal `false`
- **AND** next app launch SHALL resolve to `false` before first frame

#### Scenario: Default true on fresh install
- **WHEN** DataStore has no `tab_animations_enabled_v1` key
- **THEN** resolver SHALL return `true`

#### Scenario: Corrupt value falls back to true
- **WHEN** DataStore `tab_animations_enabled_v1` holds a value that is not a valid Boolean
- **THEN** resolver SHALL return `true`
- **AND** resolver SHALL log a warning (non-PII, single line)

### Requirement: AnimationTokens supports navEnabled and tabEnabled override
The system SHALL provide `AnimationTokens.tokensFor(style, navEnabled, tabEnabled)` that returns a token set honoring the active `AnimationStyle` and the override booleans. When `navEnabled == false`, `navEnter / navExit / navPopEnter / navPopExit` SHALL be `EnterTransition.None / ExitTransition.None`. When `tabEnabled == false`, `tabContentSpec` SHALL be `snap()`. The legacy `AnimationStyle.tokens()` SHALL remain available and SHALL be equivalent to `tokensFor(style, navEnabled = true, tabEnabled = true)`.

#### Scenario: Tokens honor both overrides
- **WHEN** style is IMMERSIVE and `navEnabled == false` and `tabEnabled == false`
- **THEN** all `navEnter / navExit / navPopEnter / navPopExit` SHALL be None variants
- **AND** `tabContentSpec` SHALL be `snap()`
- **AND** other token fields (`switchSpec`, `expandEnter`, `collapseExit`, etc.) SHALL remain IMMERSIVE

#### Scenario: Legacy tokens() unchanged
- **WHEN** callers invoke `AnimationStyle.IMMERSIVE.tokens()`
- **THEN** result SHALL be `tokensFor(IMMERSIVE, navEnabled = true, tabEnabled = true)`
- **AND** existing callers SHALL NOT need to update their call sites

