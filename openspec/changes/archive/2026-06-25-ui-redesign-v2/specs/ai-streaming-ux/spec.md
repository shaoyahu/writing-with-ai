## MODIFIED Requirements

### Requirement: StreamingPanel uses updated design tokens
The StreamingPanel ModalBottomSheet SHALL use lg(16dp) corner radius from LocalCornerRadius. The header row SHALL use ink-green primary color. The accept button SHALL use primary (ink-green) container color. The typing indicator dots SHALL use primary color.

#### Scenario: Streaming panel uses new color tokens
- **WHEN** the streaming panel is displayed
- **THEN** the header, accept button, and typing indicator use ink-green primary color from the new design system

### Requirement: ActionSheet uses updated design tokens
The ActionSheet popup SHALL use md(12dp) corner radius from LocalCornerRadius. Menu items SHALL use primary color for icons. The arrow triangle SHALL match the surfaceContainerHigh color.

#### Scenario: Action sheet uses new corner radius
- **WHEN** the action sheet popup is displayed
- **THEN** the menu card uses 12dp corner radius
