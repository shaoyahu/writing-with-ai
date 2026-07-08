# developer-mode Specification

## Purpose

「开发者模式」入口与提示词编辑器:通过点击版本号随机次数激活,提供自定义拆解提示词能力。

Synced from OpenSpec change `entity-management-and-ai-decompose`(2026-07-08)。

## Requirements

### Requirement: Developer mode activation

The system SHALL activate developer mode when user taps version text a random number of times (5-12).

#### Scenario: Random tap count
- **WHEN** user taps version text
- **THEN** system randomly selects a target count between 5 and 12

#### Scenario: Visual feedback on tap
- **WHEN** user taps version text
- **THEN** version text shakes with animation (no other UI feedback)

#### Scenario: Developer mode activated
- **WHEN** user reaches the randomly selected tap count
- **THEN** developer mode is enabled and "开发者选项" appears in "我的" tab

### Requirement: Developer mode deactivation

The system SHALL allow disabling developer mode from developer options screen.

#### Scenario: Disable developer mode
- **WHEN** user clicks "关闭开发者模式" in developer options
- **THEN** developer mode is disabled, "开发者选项" entry is hidden

### Requirement: Prompt editor

The system SHALL provide a prompt editor in developer options for customizing the AI decompose prompt.

#### Scenario: Edit prompt
- **WHEN** user navigates to "编辑拆解提示词"
- **THEN** a multi-line text editor shows current prompt with save and reset buttons

#### Scenario: Save custom prompt
- **WHEN** user edits prompt and clicks save
- **THEN** custom prompt is saved to database and used for future AI decompose calls

#### Scenario: Reset to default
- **WHEN** user clicks "恢复默认"
- **THEN** prompt resets to initial default and default is used for future calls

### Requirement: Default prompt content

The system SHALL provide a default prompt that instructs AI to extract entities from note content.

#### Scenario: Default prompt structure
- **WHEN** system uses default prompt
- **THEN** prompt includes: role definition, entity type list (Chinese), task description, output format (JSON), deduplication rule, and a one-line example