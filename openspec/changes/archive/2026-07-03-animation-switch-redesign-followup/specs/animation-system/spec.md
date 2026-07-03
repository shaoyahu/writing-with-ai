# animation-system Delta Spec

## MODIFIED Requirements

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

## ADDED Requirements

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
- **THEN** both "导航动画" and "标签动画" toggles SHALL be displayed as disabled (non-interactive)
- **AND** a banner SHALL display explaining reduce-motion forces NONE
- **AND** the persisted toggle values SHALL NOT be overwritten

#### Scenario: AnimationToggleRow is reusable

- **WHEN** `AnimationDetailScreen` renders the toggle rows
- **THEN** it SHALL call `AnimationToggleRow(title, description, checked, enabled, onCheckedChange)` from the top-level `feature/settings/animation/AnimationToggleRow.kt`
- **AND** the same Composable SHALL be available for reuse in other animation settings pages if needed

#### Scenario: Route registration

- **WHEN** user navigates from MyScreen → Display section → Animation details
- **THEN** route `SettingsAnimationDetail` SHALL resolve to `AnimationDetailScreen`
- **AND** `MeTabTarget.SettingsAnimationDetail` SHALL route via the type-safe navigation graph

### Requirement: AnimationToggleRow lives at package top level

The system SHALL provide `feature/settings/animation/AnimationToggleRow.kt` as a top-level (non-private) `@Composable fun AnimationToggleRow(title, description, checked, enabled, onCheckedChange)`. The Composable SHALL render a `Surface` row with leading title + description and a trailing `AnimatedSwitch`. It SHALL be shared by `AnimationStylePreviewScreen` (if needed in future) and `AnimationDetailScreen` (initial consumer).

#### Scenario: Shared between animation pages

- **WHEN** `AnimationDetailScreen` renders toggle rows
- **THEN** it SHALL call the top-level `AnimationToggleRow` function
- **AND** the function SHALL NOT be declared `private` inside any one screen file

#### Scenario: Disabled state visual

- **WHEN** `enabled = false`
- **THEN** the row SHALL be visually dim (reduced opacity) AND `onCheckedChange` SHALL NOT be invoked on tap
