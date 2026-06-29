## ADDED Requirements

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
The system SHALL provide a `core/ui/AnimatedSwitch.kt` Composable that wraps Material3 `Switch` and reads `LocalAnimationTokens.current.switchSpec` for thumb-position animation.

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
The system SHALL provide `feature/settings/animation/AnimationStylePreviewScreen.kt` with 4 selectable cards (MINIMAL / FLUID / IMMERSIVE / NONE), each with a mini live preview.

#### Scenario: 4 cards rendered
- **WHEN** user opens "动画风格" settings page
- **THEN** the screen SHALL render exactly 4 cards (MINIMAL / FLUID / IMMERSIVE / NONE)
- **AND** each card SHALL show a mini preview (nav transition + Switch + Tab animation)

#### Scenario: Reduce-motion banner
- **WHEN** `AccessibilityManager.isReduceMotionEnabled == true`
- **THEN** the screen SHALL display a Banner explaining reduce-motion forces NONE

#### Scenario: Selecting IMMERSIVE writes DataStore
- **WHEN** user taps the IMMERSIVE card
- **THEN** `UserPrefsStore.setAnimationStyle(IMMERSIVE)` SHALL be invoked
- **AND** the active style SHALL update to IMMERSIVE

#### Scenario: Route registration
- **WHEN** user navigates from MyScreen to animation settings
- **THEN** route `SettingsAnimationStyle` SHALL resolve to `AnimationStylePreviewScreen`

### Requirement: i18n parity for animation settings
The system SHALL add the 10 animation-style string keys to both `values/strings.xml` and `values-en/strings.xml` with matching key sets.

#### Scenario: Key set equality
- **WHEN** `grep -oE 'name="anim_style_[a-z_]+"' values/strings.xml | sort -u` is compared to the same command on `values-en/strings.xml`
- **THEN** the two outputs SHALL be identical (10 keys each)
