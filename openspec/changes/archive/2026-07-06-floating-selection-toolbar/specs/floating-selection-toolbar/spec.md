# floating-selection-toolbar Specification

## Purpose

笔记详情页文本选中时浮现的快捷工具栏,提供"加入实体"和"AI 操作"两个入口。取代原来的底部固定操作栏。

## Requirements

### Requirement: Floating toolbar visibility

The system SHALL display the floating toolbar when the user has selected non-empty text in the note body (selection range is not collapsed). The toolbar SHALL be hidden when no text is selected, when the user taps outside the selection, or when the user scrolls the note.

#### Scenario: Selection shows toolbar
- **WHEN** user long-presses a word in the note body and drags to select a non-empty range
- **THEN** the floating toolbar SHALL appear positioned above the selection

#### Scenario: No selection hides toolbar
- **WHEN** user taps outside the selected range or selection becomes collapsed
- **THEN** the floating toolbar SHALL be hidden

#### Scenario: Scroll hides toolbar
- **WHEN** user scrolls the note content while toolbar is visible
- **THEN** the floating toolbar SHALL be hidden

### Requirement: Add entity action

The floating toolbar SHALL expose an "Add Entity" action that lets the user mark the selected text as an entity. This action SHALL be local (no AI dependency) and SHALL be enabled regardless of whether an AI model is configured.

#### Scenario: Tap add entity marks selection as entity
- **WHEN** user has selected text and taps the "Add Entity" button on the floating toolbar
- **THEN** the selected text SHALL be persisted as a row in `note_entities` table with `entityType = CONCEPT`, normalized `entityKey` (lowercase, underscore-joined), original `surfaceForm`, and the corresponding `spanStart` / `spanEnd` offsets

#### Scenario: Add entity is always enabled
- **WHEN** user has not configured any AI model apikey
- **THEN** the "Add Entity" button SHALL remain enabled and visually active (not greyed out)

### Requirement: AI action menu

The floating toolbar SHALL expose an "AI" action button that triggers AI-powered operations on the selected text. Tapping the button SHALL open a dropdown menu listing the AI operations (expand / polish / organize / summarize / translate).

#### Scenario: Tap AI button opens dropdown
- **WHEN** user has selected text and taps the "AI" button on the floating toolbar
- **THEN** a dropdown menu SHALL appear listing the AI operations

#### Scenario: AI disabled when no model configured
- **WHEN** user has not configured any AI model apikey
- **THEN** the "AI" button SHALL be visually disabled (greyed out, `onSurface.copy(alpha = 0.38f)`) and SHALL not be tappable

#### Scenario: AI requires consent
- **WHEN** user taps the "AI" button but has not accepted the AI consent terms
- **THEN** the system SHALL invoke the consent request flow before performing any AI operation

### Requirement: Toolbar styling consistency

The floating toolbar SHALL follow the Material 3 design system and project design tokens (spacing, corner radius, typography). The toolbar SHALL use `surfaceContainerHigh` background color with `cornerRadius.md` (12dp) rounded corners and a 2dp shadow elevation, matching the existing `AppActionDropdown` style.

#### Scenario: Toolbar matches dropdown style
- **WHEN** the floating toolbar is rendered
- **THEN** its container color, corner radius, and shadow elevation SHALL match the existing dropdown menu style (`AppActionDropdown`)

#### Scenario: Toolbar uses design tokens
- **WHEN** the floating toolbar is implemented
- **THEN** all spacing, padding, and corner radius values SHALL use `LocalSpacing.current` and `LocalCornerRadius.current` tokens (no bare `.dp` values)