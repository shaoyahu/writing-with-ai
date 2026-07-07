## ADDED Requirements

### Requirement: AI decompose extracts new entities

The system SHALL call AI to analyze note content and extract entities not already present in the database. The AI SHALL return a JSON array of entities, each containing `entityType` (in Chinese) and `surfaceForm`.

#### Scenario: AI returns valid entity list
- **WHEN** user clicks "拆解" and AI returns `[{"type":"人物","surface":"张三"},{"type":"作品","surface":"《红楼梦》"}]`
- **THEN** system creates new `NoteEntityRow` records with `source = "AI_EXTRACTED"` for each new entity

#### Scenario: AI returns empty list
- **WHEN** user clicks "拆解" and AI returns `[]`
- **THEN** system shows Snackbar "未发现实体"

#### Requirement: AI decompose matches existing entities

The system SHALL match note content against existing entities in the database (case-insensitive) and create `note_entities` associations for matches.

#### Scenario: Note contains existing entity
- **WHEN** note content contains "张三" and database has entity `person::zhangsan` with `surfaceForm = "张三"`
- **THEN** system creates `note_entities` association for this note and entity without calling AI

#### Scenario: Case-insensitive matching
- **WHEN** note content contains "zhang san" and database has entity `person::zhangsan` with `surfaceForm = "张三"`
- **THEN** system does NOT match (Chinese and English are different surface forms)

#### Scenario: Multiple occurrences of same entity
- **WHEN** note content contains "张三" three times
- **THEN** system creates only ONE `note_entities` record but renders all three occurrences with underline highlight

### Requirement: Re-decompose confirmation

The system SHALL show a confirmation dialog before re-decomposing a note that already has entities.

#### Scenario: Re-decompose confirmed
- **WHEN** user clicks "重新拆解" on a note with existing entities and confirms the dialog
- **THEN** system deletes existing `note_entities` for this note and runs full AI decompose

#### Scenario: Re-decompose cancelled
- **WHEN** user clicks "重新拆解" but cancels the dialog
- **THEN** system does nothing

### Requirement: Decompose loading state

The system SHALL show a full-screen loading indicator during AI decompose that blocks user interaction.

#### Scenario: Decompose in progress
- **WHEN** user clicks "拆解" and AI call is in progress
- **THEN** system shows full-screen loading with "正在拆解..." text and menu is disabled

#### Scenario: Decompose completes
- **WHEN** AI call completes successfully
- **THEN** loading dismisses and entity highlights appear immediately

### Requirement: Auto-refresh existing entities on open

The system SHALL automatically match existing entities when opening a note detail page, without calling AI.

#### Scenario: Open note with existing entity mentions
- **WHEN** user opens a note containing "张三" and database has matching entity
- **THEN** system creates `note_entities` association and renders underline highlight

#### Scenario: Open note with no entity mentions
- **WHEN** user opens a note with no matching existing entities
- **THEN** system does not create any associations and shows no highlights

### Requirement: Pre-decompose API key check

The system SHALL check if AI provider is configured before allowing decompose. If not configured, clicking the menu shows error and navigates to settings.

#### Scenario: No API key configured
- **WHEN** user clicks "拆解" without configured API key
- **THEN** system shows error dialog "请先配置 AI 模型" with "去设置" button that navigates to AI settings

#### Scenario: API key configured but test failed
- **WHEN** user clicks "拆解" with API key that failed connectivity test
- **THEN** system shows same error dialog as "No API key configured"
