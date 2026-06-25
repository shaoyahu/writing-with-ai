# ui-page-redesign Specification

## Purpose
TBD - created by archiving change ui-redesign-v2. Update Purpose after archive.
## Requirements
### Requirement: Note list search bar uses filled capsule style
The search bar on QuickNoteListScreen SHALL use a filled background (surfaceVariant) with 24dp corner radius (capsule shape), no outlined border, and a leading search icon. It SHALL NOT use OutlinedTextField.

#### Scenario: Search bar renders as capsule
- **WHEN** the note list screen is displayed
- **THEN** the search bar has a surfaceVariant background, 24dp corner radius, no border outline, and a leading search icon

### Requirement: Note list item uses border card with colored accent bar
Each NoteRow SHALL render as a Card with 12dp corner radius, 1dp outlineVariant border, no elevation shadow. The card SHALL display a 3dp-wide colored accent bar on the left edge using the first tag's hash color (or primary if no tags).

#### Scenario: Note row with tags shows colored accent bar
- **WHEN** a NoteRow is displayed and has at least one tag
- **THEN** a 3dp-wide vertical bar on the left edge uses a color derived from the first tag name

#### Scenario: Note row without tags uses primary accent bar
- **WHEN** a NoteRow is displayed and has no tags
- **THEN** the 3dp-wide vertical bar uses `primary` color

### Requirement: Empty state shows large icon and brand copy
When the note list is empty, the screen SHALL display a large (64dp) icon, brand-tagline text, and a CTA button styled with primary color. It SHALL NOT show only plain text and a default button.

#### Scenario: Empty note list shows branded empty state
- **WHEN** the note list has no items
- **THEN** a 64dp icon, brand tagline text, and primary-colored CTA button are centered on screen

### Requirement: Note detail page has fixed bottom action bar
QuickNoteDetailScreen SHALL display a fixed bottom bar containing Share and AI (AutoAwesome) icon buttons. Pin/Delete/Export operations SHALL remain in the TopAppBar MoreVert menu.

#### Scenario: Bottom action bar shows Share and AI icons
- **WHEN** a note detail is displayed
- **THEN** a bottom bar with Share and AutoAwesome icon buttons is visible without scrolling

### Requirement: Note detail title uses headlineLarge style
The note title in QuickNoteDetailScreen SHALL use `MaterialTheme.typography.headlineLarge` style. Tags SHALL be displayed as a FlowRow of SuggestionChips directly below the title.

#### Scenario: Note title renders large
- **WHEN** a note detail is displayed with a title
- **THEN** the title text uses headlineLarge style

### Requirement: Related notes section wrapped in Surface card
The RelatedNotesSection in QuickNoteDetailScreen SHALL be wrapped in a Surface with surfaceVariant container color and md(12dp) corner radius, with a section header containing the Hub icon and title text.

#### Scenario: Related notes in Surface card
- **WHEN** the related notes section is displayed
- **THEN** it is contained within a Surface card with surfaceVariant background and 12dp corner radius

### Requirement: Editor title uses borderless large TextField
The QuickNoteEditorScreen title field SHALL use BasicTextField with headlineMedium textStyle, no border/decoration, and full width. The content field SHALL use BasicTextField with bodyLarge textStyle and Modifier.weight(1f) to fill remaining height.

#### Scenario: Editor title is borderless large text
- **WHEN** the editor screen is displayed
- **THEN** the title field has no outline border and uses headlineMedium textStyle

#### Scenario: Editor content fills remaining space
- **WHEN** the editor screen is displayed
- **THEN** the content text field fills all vertical space remaining after title and tag sections

### Requirement: Editor tag section wrapped in Surface
The tag input area in QuickNoteEditorScreen SHALL be wrapped in a Surface with surfaceVariant container color, providing visual separation from the content area.

#### Scenario: Tag section visually separated
- **WHEN** the editor screen is displayed
- **THEN** the tag input area is enclosed in a Surface card with surfaceVariant background

### Requirement: My screen section cards have rounded corners and icons
Each section card in MyScreen SHALL use 12dp corner radius. Each ListItem SHALL include a leading icon. Each section SHALL have a title label (Text with titleSmall style and primary color) above the card.

#### Scenario: My screen section has header and icons
- **WHEN** the My screen is displayed
- **THEN** each section has a title label above the card, and each list item has a leading icon

### Requirement: Onboarding has branded header area
OnboardingScreen SHALL display a branded header area at the top with primaryContainer background, containing headlineLarge title text and bodyMedium subtitle text. The privacy policy section SHALL be wrapped in a Surface card with surfaceContainerLow background.

#### Scenario: Onboarding branded header
- **WHEN** the onboarding screen is displayed
- **THEN** a primaryContainer-colored header area with large title text is visible at the top

### Requirement: Model management provider cards simplified
ModelManagementScreen provider cards SHALL show only: display name, status badge (configured/not), and a single "Configure" or "Modify" text button. The ping section SHALL be an inline badge below the provider list rather than a separate card.

#### Scenario: Provider card shows essential info only
- **WHEN** the model management screen is displayed
- **THEN** each provider card shows name, status badge, and a single action button

### Requirement: App shell FAB uses secondary (amber) color
The center FAB in AppShell SHALL use `MaterialTheme.colorScheme.secondary` (amber) as containerColor, making it visually distinct from primary-colored elements and drawing attention as the primary CTA.

#### Scenario: FAB uses amber color
- **WHEN** the app shell bottom bar is displayed
- **THEN** the center FAB has amber (secondary) container color

