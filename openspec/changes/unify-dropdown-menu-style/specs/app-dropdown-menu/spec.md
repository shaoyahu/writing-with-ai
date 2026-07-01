## ADDED Requirements

### Requirement: AppActionDropdown provides unified action menu
The system SHALL provide `AppActionDropdown` composable that wraps Material3 `DropdownMenu` with unified styling: `containerColor = surfaceContainerHigh`, `shape = RoundedCornerShape(cornerRadius.md)` = 12dp, `shadowElevation = 2dp`, `tonalElevation = 0dp`. It SHALL accept `items: List<AppActionItem>` as a declarative list.

#### Scenario: Action menu renders with unified style
- **WHEN** AppActionDropdown is displayed
- **THEN** it uses surfaceContainerHigh background, 12dp corner radius, and 2dp shadow elevation

#### Scenario: Action menu item with leading icon
- **WHEN** an AppActionItem has a non-null leadingIcon
- **THEN** the icon is displayed before the text with onSurfaceVariant tint

#### Scenario: Destructive action item uses error color
- **WHEN** an AppActionItem has isDestructive = true
- **THEN** the item text and icon use MaterialTheme.colorScheme.error color

#### Scenario: Disabled action item uses muted color
- **WHEN** an AppActionItem has enabled = false
- **THEN** the item text and icon use onSurface at 38% alpha

### Requirement: AppActionItem data class defines action menu items
The system SHALL provide `AppActionItem` data class with fields: `text: String`, `onClick: () -> Unit`, `leadingIcon: ImageVector? = null`, `enabled: Boolean = true`, `isDestructive: Boolean = false`.

#### Scenario: Minimal action item creation
- **WHEN** creating AppActionItem with only text and onClick
- **THEN** leadingIcon is null, enabled is true, isDestructive is false

### Requirement: AppSelectionDropdown provides unified selection menu
The system SHALL provide `AppSelectionDropdown<T>` composable that wraps `ExposedDropdownMenuBox` + `OutlinedTextField(readOnly)` + `ExposedDropdownMenu` with unified styling matching AppActionDropdown. It SHALL accept `options: List<T>`, `selected: T`, `onSelected: (T) -> Unit`, `label: @Composable () -> Unit`, `optionLabel: (T) -> String`.

#### Scenario: Selection menu renders with unified style
- **WHEN** AppSelectionDropdown is displayed
- **THEN** the dropdown panel uses surfaceContainerHigh background, 12dp corner radius, and 2dp shadow elevation

#### Scenario: Selected option shows check icon
- **WHEN** an option matches the current selected value
- **THEN** it displays a trailing Icons.Filled.Check icon tinted with primary color, and text uses primary color

#### Scenario: Unselected option shows no check icon
- **WHEN** an option does not match the current selected value
- **THEN** it has no trailing icon and text uses onSurface color

#### Scenario: Selection menu auto-manages expanded state
- **WHEN** AppSelectionDropdown is used
- **THEN** the caller does not need to manage expanded state; the component handles it internally

#### Scenario: Selecting an option closes the menu
- **WHEN** user selects an option from AppSelectionDropdown
- **THEN** onSelected is invoked with the chosen option and the menu closes
