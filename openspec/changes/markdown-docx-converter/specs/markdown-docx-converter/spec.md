## ADDED Requirements

### Requirement: Markdown to Docx block conversion

The system MUST provide a `MarkdownToDocxConverter` that transforms Markdown content into a list of Feishu Docx block objects suitable for the `POST /docx/v1/documents/{docId}/blocks/{rootBlockId}/children` endpoint.

#### Scenario: Heading conversion
- **WHEN** Markdown contains `# 标题`
- **THEN** the converter MUST emit a `heading_1` block with `elements: [{text_run: {content: "标题"}}]`

#### Scenario: Bullet list conversion
- **WHEN** Markdown contains `- item`
- **THEN** the converter MUST emit a `bullet` block with `elements: [{text_run: {content: "item"}}]`

### Requirement: Supported Markdown subset

The converter MUST support: `# H1-H3`, paragraphs, `- bullet`, `1. ordered`, `> quote`, fenced code blocks, `**bold**`, `*italic*`, `[text](url)` links, and `---` horizontal rules. The converter MUST gracefully degrade unsupported elements to text placeholders.

#### Scenario: Image degrades to text placeholder
- **WHEN** Markdown contains `![alt](path)`
- **THEN** the converter MUST emit a `text` block with content `[图片：path]`

#### Scenario: Table without merged cells
- **WHEN** Markdown contains a simple `|` table
- **THEN** the converter MUST emit a `table` block with text-grid layout

#### Scenario: Unsupported HTML passes through
- **WHEN** Markdown contains `<div>raw</div>`
- **THEN** the converter MUST emit a `text` block with the raw HTML string

### Requirement: Docx to Markdown conversion

The system MUST provide a `DocxToMarkdownConverter` that transforms Feishu Docx blocks back into Markdown, sufficient for round-trip fidelity of all supported elements.

#### Scenario: Block types map to Markdown
- **WHEN** Docx blocks contain `heading_1`, `bullet`, `ordered`, `quote`, `code`, `text`, `table`, `divider`
- **THEN** each MUST be converted to its Markdown equivalent

#### Scenario: Unknown block type degrades
- **WHEN** Docx blocks contain an unsupported type (e.g., `grid`, `mention`)
- **THEN** the converter MUST emit `[不支持类型:grid]` in the Markdown output

### Requirement: Round-trip fidelity test

The system MUST provide unit tests verifying that for each supported Markdown element, `MarkdownToDocxConverter → DocxToMarkdownConverter` produces equivalent output. Tests MUST be located in `app/src/test/java/com/yy/writingwithai/core/feishu/converter/`.

#### Scenario: Round-trip for all supported elements
- **WHEN** the round-trip test runs against the canonical sample containing all supported elements
- **THEN** the diff between input Markdown and round-trip output MUST be empty

#### Scenario: Round-trip ignores degraded placeholders
- **WHEN** Markdown contains `![image](path)`
- **THEN** after round-trip, the output MUST contain `[图片：path]`