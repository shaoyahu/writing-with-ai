## ADDED Requirements

### Requirement: Consent page renders card-style sections
The system SHALL render the onboarding consent page as a vertically stacked list of collapsible cards, one per H2 section of the policy markdown.

#### Scenario: 5 cards rendered for 5-section policy
- **WHEN** the policy markdown contains 5 H2 sections (e.g. 数据存储 / AI 功能 / 第三方 / 撤回同意 / 联系方式)
- **THEN** the page SHALL render exactly 5 `ConsentSectionCard` items
- **AND** the first card SHALL be expanded by default; the rest SHALL be collapsed

#### Scenario: Card shows title, icon, summary
- **WHEN** a `ConsentSectionCard` is rendered
- **THEN** it SHALL display: H2 title text, an icon mapped from the title (via keyword match), and a summary line sourced from a string resource (e.g. `consent_section_data_storage_summary`)

#### Scenario: Card expands on tap
- **WHEN** user taps the collapsed card header
- **THEN** the card SHALL expand and reveal `MarkdownBlockView` rendering of the section body
- **AND** expansion SHALL use `LocalAnimationTokens.current.expandEnter` / `collapseExit`

### Requirement: parseGroupedMarkdown splits policy by H2
The system SHALL provide `feature/onboarding/SimpleMarkdown.kt` extension `parseGroupedMarkdown(text: String): List<ConsentSection>` that groups blocks by H2 markers (`## `).

#### Scenario: 5 H2 sections produce 5 ConsentSection
- **WHEN** `parseGroupedMarkdown` is called with the policy text
- **THEN** it SHALL return a `List<ConsentSection>` of length equal to the number of H2 headers

#### Scenario: Each section carries parsed MarkdownBlocks
- **WHEN** an H2 section contains paragraphs, lists, or quotes
- **THEN** its `blocks: List<MarkdownBlock>` SHALL contain the parsed `MarkdownBlock` items in source order

#### Scenario: parseSimpleMarkdown API preserved
- **WHEN** existing callers (e.g. older `MarkdownBlockView` consumers) call `parseSimpleMarkdown(text)`
- **THEN** the function SHALL return the same `List<MarkdownBlock>` as before
- **AND** its signature SHALL be unchanged (backward-compatible)

### Requirement: Scroll progress bar reflects scroll position
The system SHALL render `ConsentProgressBar` above the card list, animating from 0.0 → 1.0 as the user scrolls through the policy content.

#### Scenario: Initial state at 0
- **WHEN** the page is first composed and scroll position is at the top
- **THEN** `ConsentProgressBar` SHALL display 0% fill

#### Scenario: Reaches 1.0 at scroll bottom
- **WHEN** the LazyColumn's last visible item is fully visible (i.e. user has scrolled to the bottom)
- **THEN** `ConsentProgressBar` SHALL display 100% fill
- **AND** the fill SHALL be smoothed via `animateFloatAsState`

### Requirement: Accept button gated by scroll-bottom state
The system SHALL disable the "同意并继续" button until the user has scrolled to the bottom of the policy content, and SHALL enable it (with animated color + alpha) once the bottom is reached.

#### Scenario: Disabled at top
- **WHEN** the LazyColumn's last visible item is NOT fully visible
- **THEN** the accept button SHALL be disabled (alpha 0.38, container color = disabled container)
- **AND** SHALL NOT be clickable

#### Scenario: Enabled at bottom with animation
- **WHEN** the LazyColumn's last visible item becomes fully visible (and stays visible for ≥1 frame)
- **THEN** the accept button SHALL become enabled (alpha 1.0, primary container color) over `tween(300)`
- **AND** SHALL accept click events calling the existing consent accept handler

#### Scenario: Reject button always enabled
- **WHEN** the page is rendered (regardless of scroll position)
- **THEN** the "拒绝并退出" button SHALL be enabled and clickable

### Requirement: Brand header renders at top
The system SHALL render a brand header section above the progress bar, displaying the app name and welcome subtitle.

#### Scenario: Header content
- **WHEN** the page is first composed
- **THEN** the header SHALL display `R.string.onboarding_title` ("欢迎使用 writing-with-ai") and `R.string.onboarding_subtitle` ("请阅读并同意以下条款")

### Requirement: ViewModel interface unchanged
The system SHALL preserve `OnboardingConsentViewModel` (or equivalent state holder) public API and state shape; only the Composable layer is rewritten.

#### Scenario: Existing accept/reject handlers invoked
- **WHEN** user taps the accept or reject button
- **THEN** the existing ViewModel handler SHALL be invoked
- **AND** behavior on accept (navigate forward, persist consent) and reject (exit app) SHALL be unchanged

#### Scenario: Policy loading error handled
- **WHEN** policy markdown fails to load
- **THEN** the page SHALL display a fallback message via `R.string.onboarding_policy_load_failed`
- **AND** the existing error path SHALL remain in effect

### Requirement: i18n parity for consent sections
The system SHALL add the 14 consent-related string keys (10 `consent_section_*` + 3 `consent_*` + 1 `onboarding_policy_load_failed`) to both `values/strings.xml` and `values-en/strings.xml` with matching key sets.

#### Scenario: Key set equality
- **WHEN** the union of `consent_*` / `consent_section_*` / `onboarding_policy_load_failed` keys is extracted from both `values/strings.xml` and `values-en/strings.xml`
- **THEN** the two key sets SHALL be identical (14 keys each)
