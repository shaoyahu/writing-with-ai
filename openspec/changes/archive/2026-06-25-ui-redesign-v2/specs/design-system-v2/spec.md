## ADDED Requirements

### Requirement: Color scheme uses ink-green and amber brand palette
The application SHALL use primary=#1B6B4A (ink-green), secondary=#D4940A (amber), tertiary=#2BAD8E (mint) as the brand color palette in light mode. The palette SHALL be derived from Material 3 dynamic color rules to ensure accessible contrast ratios.

#### Scenario: Light theme renders ink-green primary
- **WHEN** the system is in light mode
- **THEN** `MaterialTheme.colorScheme.primary` resolves to #1B6B4A and all primary-tinted UI elements (FAB, buttons, selected tab, chip highlights) use ink-green

#### Scenario: Dark theme adapts palette
- **WHEN** the system is in dark mode
- **THEN** primary resolves to a lighter ink-green tint, primaryContainer uses a deep ink-green, and all surfaces maintain WCAG AA contrast against their on- counterparts

### Requirement: Card uses border stroke instead of elevation shadow
All Card components in the application SHALL use elevation=0 with a 1dp BorderStroke using `outlineVariant` color by default. Selected/active cards SHALL use a 2dp BorderStroke using `primary` color.

#### Scenario: Default card renders with border line
- **WHEN** a Card is displayed in default state
- **THEN** it has no elevation shadow and shows a 1dp border in `outlineVariant` color

#### Scenario: Selected card renders with primary border
- **WHEN** a Card is in selected/active state
- **THEN** it shows a 2dp border in `primary` color

### Requirement: CornerRadius token provides five tiers
`CornerRadius` data class SHALL provide xs(4dp), sm(8dp), md(12dp), lg(16dp), xl(24dp). Card components SHALL use md(12dp). ModalBottomSheet SHALL use lg(16dp). Search bars SHALL use xl(24dp).

#### Scenario: Card uses md corner radius
- **WHEN** any Card is rendered
- **THEN** its corner radius is 12dp

### Requirement: Spacing token provides nine tiers
`Spacing` data class SHALL provide xs2(2dp), xs(4dp), sm(8dp), sm2(12dp), md(16dp), md2(20dp), lg(24dp), xl(32dp), xl2(40dp). Business Composables SHALL NOT use bare `.dp` for spacing — all spacing MUST reference `LocalSpacing.current`.

#### Scenario: Spacing token covers all common gaps
- **WHEN** a Composable needs 12dp spacing
- **THEN** it uses `spacing.sm2` instead of `12.dp`

### Requirement: Custom semantic colors include warning
`CustomColors` SHALL include `warning: Color` and `warningDark: Color` tokens in addition to existing `success`/`successDark`, providing a complete semantic color set (success/warning/error).

#### Scenario: Warning color accessible via customColors
- **WHEN** a Composable needs a warning/attention color
- **THEN** it reads `MaterialTheme.customColors.warning` (amber tint in light, lighter amber in dark)
