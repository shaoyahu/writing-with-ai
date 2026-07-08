# design-system-v2 Specification

## Purpose
TBD - created by archiving change ui-redesign-v2. Update Purpose after archive.
## Requirements
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
`CornerRadius` data class SHALL provide xs(4dp), sm(8dp), md(12dp), lg(16dp), xl(24dp). Card components SHALL use md(12dp). ModalBottomSheet SHALL use lg(16dp). Search bars SHALL use xl(24dp). Dropdown menus SHALL use md(12dp).

#### Scenario: Card uses md corner radius
- **WHEN** any Card is rendered
- **THEN** its corner radius is 12dp

#### Scenario: Dropdown menu uses md corner radius
- **WHEN** any DropdownMenu or ExposedDropdownMenu is rendered via AppActionDropdown or AppSelectionDropdown
- **THEN** its corner radius is 12dp (cornerRadius.md)

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


### Requirement: Surface overlay components use surfaceContainerHigh + tonalElevation, not BorderStroke

Material 3 浮层组件(Floating toolbar / ModalBottomSheet / DropdownMenu container / Dialog)MUST 使用 `color = MaterialTheme.colorScheme.surfaceContainerHigh` + `tonalElevation` 表达浮层,MUST NOT 使用 `BorderStroke`(BorderStroke 是卡片类组件的视觉语言)。`surfaceContainerHigh` 是 M3 1.2+ ColorScheme 自动派生的 token,业务代码 MUST NOT 硬编码 hex 替代。

#### Scenario: SelectionFloatingToolbar 用 surfaceContainerHigh
- **WHEN** 浮选工具栏渲染
- **THEN** 背景为 `MaterialTheme.colorScheme.surfaceContainerHigh`,`tonalElevation = 6.dp`,`shadowElevation = 8.dp`;MUST NOT 出现 `BorderStroke` 或 `Color(0x...)` 字面量

#### Scenario: grep 验证无 BorderStroke 用于浮层
- **WHEN** `grep -r "BorderStroke" app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/SelectionFloatingToolbar.kt`
- **THEN** 0 匹配

#### Scenario: grep 验证浮层组件无硬编码 hex
- **WHEN** `grep -rE "Color\(0x" app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/`
- **THEN** 0 匹配(浮层组件走 token)

### Requirement: Floating toolbar icon button pattern uses IconButton + label below, not FilledTonalButton

浮选工具栏的操作区 MUST 使用 `IconButton` + 下方 `Text(style = labelSmall)` 排版,而非 `FilledTonalButton`(FilledTonalButton 视觉过重,会"压住"下方内容)。已添加态 MUST 仅切换 `Icon` 类型与 `tint`,MUST NOT 切换按钮底色。

#### Scenario: 操作区是 IconButton 不是 FilledTonalButton
- **WHEN** 工具栏渲染操作区
- **THEN** `grep "FilledTonalButton" app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/SelectionFloatingToolbar.kt` 返回 0 匹配

#### Scenario: 已添加态不变底色
- **WHEN** `isEntityAdded = true`
- **THEN** ⭐IconButton 内 `Icon` 切到 `Icons.Filled.Star` + `tint = MaterialTheme.colorScheme.primary`;IconButton 底色 MUST NOT 改变
