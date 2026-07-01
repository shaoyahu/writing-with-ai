## MODIFIED Requirements

### Requirement: CornerRadius token provides five tiers
`CornerRadius` data class SHALL provide xs(4dp), sm(8dp), md(12dp), lg(16dp), xl(24dp). Card components SHALL use md(12dp). ModalBottomSheet SHALL use lg(16dp). Search bars SHALL use xl(24dp). Dropdown menus SHALL use md(12dp).

#### Scenario: Card uses md corner radius
- **WHEN** any Card is rendered
- **THEN** its corner radius is 12dp

#### Scenario: Dropdown menu uses md corner radius
- **WHEN** any DropdownMenu or ExposedDropdownMenu is rendered via AppActionDropdown or AppSelectionDropdown
- **THEN** its corner radius is 12dp (cornerRadius.md)
