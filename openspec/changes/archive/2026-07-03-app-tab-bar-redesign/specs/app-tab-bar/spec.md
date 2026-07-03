## MODIFIED Requirements

### Requirement: Bottom tab bar styled to match the My tab's card aesthetic

应用 MUST 在主屏(`AppShell`)底部渲染底部 tab 栏，**视觉风格对齐【我的】tab 的 SectionCard 圆角 + primary tint icon + surface 容器**。共 3 槽，槽位从左到右依次为:

| 槽位 | 类型 | 选中态样式 | 未选中态样式 | 文案资源 |
| --- | --- | --- | --- | --- |
| 1(笔记) | `Surface` 子卡，16dp 圆角，`weight = 1f` | `containerColor = colorScheme.primary` + `contentColor = colorScheme.onPrimary` + `Icons.Filled.Notes` | `containerColor = Color.Transparent` + `contentColor = colorScheme.onSurfaceVariant` + `Icons.Outlined.Notes` | `R.string.tab_notes` |
| 2(中央创建) | `CenterCreateCard`(`Surface` 子卡，16dp 圆角，`weight = 1f`，无 elevation 无 offset)，始终 primaryContainer 高亮 | `containerColor = colorScheme.primaryContainer` + `contentColor = colorScheme.onPrimaryContainer` + `Icons.Filled.Add` + `Spacer(4.dp)` + `Text("+ 新建", style = labelSmall)` + `vertical padding = 12.dp`(总高 68dp 跟 TabCard 等高，无选中 / 未选中态区分) | — | `R.string.tab_new_note`(label) + `R.string.tab_new_note_cd`(contentDescription) |
| 3(我的) | `Surface` 子卡，16dp 圆角，`weight = 1f` | `containerColor = colorScheme.primary` + `contentColor = colorScheme.onPrimary` + `Icons.Filled.Person` | `containerColor = Color.Transparent` + `contentColor = colorScheme.onSurfaceVariant` + `Icons.Outlined.Person` | `R.string.tab_my` |

外层容器 MUST 是 1 个 `Surface`，顶部 24dp 圆角，`containerColor = colorScheme.surfaceVariant`,`tonalElevation = 1.dp`(营造跟【我的】tab 的 surfaceVariant 背景 + SectionCard 卡片同源的视觉)。

三槽 MUST 在同一 `Row` 内 `Arrangement.spacedBy(8.dp)` 均匀分布，完全 inline 在外层 `Surface` 内，**无**任何 `offset` / `Box.align(TopCenter)` / `FloatingActionButton` 凸起结构。

#### Scenario: 容器为 surfaceVariant 圆角 Surface
- **WHEN** `AppTabBar` 渲染
- **THEN** 外层是 1 个 `Surface`,`shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)`,`containerColor = colorScheme.surfaceVariant`,`tonalElevation = 1.dp`

#### Scenario: 三槽在同一 Row 内 inline 排列
- **WHEN** `AppTabBar` 渲染
- **THEN** `Surface { Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), Arrangement.spacedBy(8.dp), verticalAlignment = CenterVertically) { TabCard(Notes, weight=1f); CenterCreateCard(weight=1f); TabCard(Me, weight=1f) } }`,**不**存在 `Box.align(TopCenter)` / `offset(y = ...)` / `FloatingActionButton`

#### Scenario: 选中 tab 子卡用 primary 容器
- **WHEN** 当前选中 tab 是笔记 / 我的
- **THEN** 对应槽位 `Surface` 子卡 `containerColor = colorScheme.primary`,`contentColor = colorScheme.onPrimary`，图标为 `Icons.Filled.*` 实心版本，文案 `Text(stringResource(...))` 用 `contentColor`

#### Scenario: 未选中 tab 子卡透明容器
- **WHEN** 当前选中 tab 是 X(X ≠ 当前槽位)
- **THEN** 对应槽位 `Surface` 子卡 `containerColor = Color.Transparent`,`contentColor = colorScheme.onSurfaceVariant`，图标为 `Icons.Outlined.*` 描边版本，文案 `Text(stringResource(...))` 用 `contentColor`

#### Scenario: tab 子卡圆角 16dp
- **WHEN** `AppTabBar` 渲染两个 `Surface` 子卡
- **THEN** 子卡 `shape = RoundedCornerShape(16.dp)`,`clickable { onClick }`(整张子卡可点，不仅是图标)

#### Scenario: tab 槽文案来自 strings.xml
- **WHEN** 渲染任一 tab 槽位
- **THEN** 文案 MUST `Text(stringResource(R.string.tab_notes))` / `R.string.tab_my`,**不**直接 `Text("笔记")` 硬编码

#### Scenario: 中央 CenterCreateCard 始终 primaryContainer 高亮
- **WHEN** 渲染中央槽位
- **THEN** `CenterCreateCard` 是 `Surface(onClick = onCenterFabClick, shape = RoundedCornerShape(16.dp), color = colorScheme.primaryContainer, contentColor = colorScheme.onPrimaryContainer)`，内部 `Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = CenterHorizontally) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tab_new_note_cd)); Spacer(Modifier.size(4.dp)); Text(stringResource(R.string.tab_new_note), style = MaterialTheme.typography.labelSmall) }`，含"+ 新建" label 文本

#### Scenario: 中央槽位触控目标 ≥ 56dp
- **WHEN** 渲染中央 `CenterCreateCard`
- **THEN** Surface 整体高度 = icon 24dp + spacer 4dp + text ~16dp + 上下 padding 12dp × 2 = **68dp**，跟 `TabCard`(icon 24dp + spacer 4dp + text 16dp + padding 12dp × 2)同尺寸，三 Surface 在 Row 内等高且 icon 基线对齐;Surface 高度 ≥ 56dp 满足 M3 触控目标;`weight = 1f` 在 Row 内撑满剩余宽度

#### Scenario: 不再使用 M3 NavigationBar / NavigationBarItem / FloatingActionButton
- **WHEN** grep `AppShell.kt` `NavigationBar` / `NavigationBarItem` / `FloatingActionButton`
- **THEN** 0 匹配(标准 M3 组件整段替换为自定义 Surface + 3 子卡，CenterCreateCard 接管创建入口)
