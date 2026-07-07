## MODIFIED Requirements

### Requirement: Decompose menu entry

The system SHALL display "拆解" or "重新拆解" in the note detail dropdown menu. The menu item SHALL be visually normal (not grayed out) and clickable regardless of API key configuration. When no AI model is configured, clicking SHALL show an error dialog.

#### Scenario: AI model configured shows decompose menu
- **WHEN** user has configured at least one AI model API key
- **THEN** menu shows "拆解" or "重新拆解" with normal visual style, clickable

#### Scenario: No AI model shows decompose menu with error on click
- **WHEN** no AI model is configured
- **THEN** menu shows "拆解" with normal visual style, but clicking shows error dialog "请先配置 AI 模型" with "去设置" button

#### Scenario: Already decomposed note shows re-decompose
- **WHEN** current note has existing entity records
- **THEN** menu item text shows "重新拆解"

### Requirement: Decompose triggers entity extraction

The system SHALL perform full AI decompose when user clicks "拆解" or confirms "重新拆解". This includes: AI extraction of new entities, matching existing entities, and refreshing UI.

#### Scenario: Successful decompose
- **WHEN** user clicks "拆解"
- **THEN** system: 1) shows full-screen loading; 2) calls AI to extract new entities; 3) matches existing entities; 4) persists results; 5) dismisses loading and refreshes highlights

#### Scenario: Re-decompose with confirmation
- **WHEN** user clicks "重新拆解"
- **THEN** system shows confirmation dialog "重新拆解将覆盖现有实体，确定吗？", on confirm proceeds with full decompose

#### Scenario: Decompose finds no entities
- **WHEN** AI returns empty entity list
- **THEN** loading dismisses and Snackbar shows "未发现实体"

#### Scenario: Decompose fails
- **WHEN** AI call fails
- **THEN** loading dismisses and Snackbar shows error message

### Requirement: Entity highlight rendering

The system SHALL render entity text with **blue font color** and a **blue cross-star mark** (十字星星) in the **upper-right corner** of the entity text in the note detail content.

#### Scenario: Entities shown with blue font and cross-star
- **WHEN** current note has entity extraction records
- **THEN** each entity text range is rendered with `color = colorScheme.primary` (blue) and a small cross-star icon (`✦` or custom drawable) positioned at the upper-right corner of the last character

#### Scenario: Cross-star occupies fixed width
- **WHEN** entity text is rendered
- **THEN** the cross-star mark occupies approximately 8-12dp width and does not overlap adjacent text

#### Scenario: Title entities not highlighted
- **WHEN** entity's spanStart is within title range (spanStart < title.length + 1)
- **THEN** the entity is not highlighted in the content

#### Scenario: Overlapping entities use longest match
- **WHEN** two entity spans overlap (e.g., "小明" and "小明家")
- **THEN** keep the highlight and click annotation for the entity with the longer span range

#### Scenario: Cached entities shown on re-entry
- **WHEN** user re-opens a previously decomposed note
- **THEN** system loads entities directly from `note_entities` table and immediately renders blue font + cross-star highlights without re-triggering AI
