## ADDED Requirements

### Requirement: Theme supports light, dark, and system modes

The app MUST define both a light and a dark Material 3 `ColorScheme`; the active scheme MUST follow the system dark-mode setting by default.

#### Scenario: System light mode uses light scheme
- **WHEN** the device system theme is light AND the app is launched
- **THEN** `MaterialTheme.colorScheme` exposes the light `ColorScheme` (background is light, foreground is dark)

#### Scenario: System dark mode uses dark scheme
- **WHEN** the device system theme is dark AND the app is launched
- **THEN** `MaterialTheme.colorScheme` exposes the dark `ColorScheme` (background is dark, foreground is light)

### Requirement: Components consume design tokens via MaterialTheme

All colors, typography, and shapes in Composable code MUST be referenced through `MaterialTheme.colorScheme.*` / `MaterialTheme.typography.*` / `MaterialTheme.shapes.*`; no raw hex colors or `sp` font sizes are allowed in feature code.

#### Scenario: No raw hex in feature code
- **WHEN** the `feature/` directory is grepped for `Color(0x` (hex literals)
- **THEN** no matches exist outside of `app/ui/theme/Color.kt`

#### Scenario: No hardcoded sp in feature code
- **WHEN** the `feature/` directory is grepped for `\.sp\b` (literal sp values)
- **THEN** no matches exist outside of `app/ui/theme/Type.kt`

### Requirement: Design tokens exposed via CompositionLocal

Custom design tokens (corner radius, spacing scale) MUST be exposed through a `CompositionLocal` so business code does not redefine them.

#### Scenario: LocalSpacing token available
- **WHEN** a Composable in any package reads `LocalSpacing.current`
- **THEN** it receives a `Spacing` value object with `sm`, `md`, `lg` fields (no compile error)

#### Scenario: LocalCornerRadius token available
- **WHEN** a Composable in any package reads `LocalCornerRadius.current`
- **THEN** it receives a `CornerRadius` value object with `sm`, `md`, `lg` fields (no compile error)