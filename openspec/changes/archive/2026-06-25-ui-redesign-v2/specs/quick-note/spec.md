## ADDED Requirements

### Requirement: Quick note list screen uses new design tokens
QuickNoteListScreen SHALL use the filled capsule search bar (surfaceVariant background, xl 24dp corner radius). NoteRow SHALL use border-card style (12dp corner radius, 1dp outlineVariant border, 0 elevation). EmptyState SHALL show 64dp icon + brand tagline + primary CTA.

#### Scenario: Note list uses capsule search and border cards
- **WHEN** the note list screen is displayed
- **THEN** the search bar is a capsule (surfaceVariant background, 24dp corner radius) and note rows are border cards (12dp radius, no shadow)

### Requirement: Quick note detail screen uses new layout
QuickNoteDetailScreen SHALL display headlineLarge title, FlowRow tags, and a fixed bottom bar with Share + AutoAwesome icons. RelatedNotesSection SHALL be wrapped in Surface(surfaceVariant, 12dp radius).

#### Scenario: Note detail uses new layout
- **WHEN** note detail screen is displayed
- **THEN** title uses headlineLarge, bottom bar shows Share + AI icons, related notes in Surface card

### Requirement: Quick note editor uses borderless TextFields
QuickNoteEditorScreen SHALL use BasicTextField for title (headlineMedium, no border) and content (bodyLarge, weight(1f)). Tag section SHALL be wrapped in Surface(surfaceVariant).

#### Scenario: Editor uses borderless fields
- **WHEN** the editor is displayed
- **THEN** title field has no border outline, content field fills remaining height, tags in Surface card
