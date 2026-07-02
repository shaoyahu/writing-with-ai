# animation-system Delta Spec

## MODIFIED Requirements

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

### Requirement: AnimationStylePreviewScreen lists 4 styles
The system SHALL provide `feature/settings/animation/AnimationStylePreviewScreen.kt` with 4 selectable style cards (MINIMAL / FLUID / IMMERSIVE / NONE) acting as a visual style library, plus 2 independent toggle rows for "导航动画" and "标签动画" bound to `UserPrefsStore.navAnimationsEnabled` and `UserPrefsStore.tabAnimationsEnabled` respectively.

#### Scenario: 4 style cards rendered
- **WHEN** user opens "动画风格" settings page
- **THEN** the screen SHALL render exactly 4 style cards (MINIMAL / FLUID / IMMERSIVE / NONE) as a visual library
- **AND** each card SHALL show a mini preview of nav transition + Switch + Tab animation
- **AND** tapping a card SHALL invoke `UserPrefsStore.setAnimationStyle(<style>)` only

#### Scenario: Nav animation toggle persists
- **WHEN** user toggles the "导航动画" `AnimatedSwitch` from ON to OFF
- **THEN** `UserPrefsStore.setNavAnimationsEnabled(false)` SHALL be invoked
- **AND** within 1 second, the active `LocalAnimationTokens.navEnter / navExit / navPopEnter / navPopExit` SHALL resolve to `EnterTransition.None / ExitTransition.None` regardless of the active `AnimationStyle`

#### Scenario: Tab animation toggle persists
- **WHEN** user toggles the "标签动画" `AnimatedSwitch` from ON to OFF
- **THEN** `UserPrefsStore.setTabAnimationsEnabled(false)` SHALL be invoked
- **AND** within 1 second, the active `LocalAnimationTokens.tabContentSpec` SHALL resolve to `snap()`

#### Scenario: Reduce-motion banner
- **WHEN** `AccessibilityManager.isReduceMotionEnabled == true`
- **THEN** the screen SHALL display a Banner explaining reduce-motion forces NONE
- **AND** both "导航动画" and "标签动画" toggles SHALL be displayed as disabled (non-interactive) and forced OFF

#### Scenario: Route registration
- **WHEN** user navigates from MyScreen to animation settings
- **THEN** route `SettingsAnimationStyle` SHALL resolve to `AnimationStylePreviewScreen`

## ADDED Requirements

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
