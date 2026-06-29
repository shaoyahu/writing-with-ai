## MODIFIED Requirements

### Requirement: Bottom tab bar styled to match the My tab's card aesthetic

应用 MUST 在主屏(`AppShell`)底部渲染底部 tab 栏,**视觉风格对齐【我的】tab 的 SectionCard 圆角 + primary tint icon + surface 容器**。共 3 槽,槽位从左到右依次为:

| 槽位 | 类型 | 选中态样式 | 未选中态样式 | 文案资源 |
| --- | --- | --- | --- | --- |
| 1(笔记) | `Surface` 子卡,16dp 圆角 | `containerColor = colorScheme.primary` + `contentColor = colorScheme.onPrimary` + `Icons.Filled.Notes` | `containerColor = Color.Transparent` + `contentColor = colorScheme.onSurfaceVariant` + `Icons.Outlined.Notes` | `R.string.tab_notes` |
| 2(中央 FAB) | 圆形凸起 `FloatingActionButton`,56dp 尺寸,`Modifier.offset(y = -20.dp)` 抬出 bar 上沿 | `containerColor = colorScheme.primary` + `contentColor = colorScheme.onPrimary` + `Icons.Filled.Add` + 12dp elevation(无选中 / 未选中态区分) | — | `R.string.tab_new_note_cd`(`contentDescription` only) |
| 3(我的) | `Surface` 子卡,16dp 圆角 | `containerColor = colorScheme.primary` + `contentColor = colorScheme.onPrimary` + `Icons.Filled.Person` | `containerColor = Color.Transparent` + `contentColor = colorScheme.onSurfaceVariant` + `Icons.Outlined.Person` | `R.string.tab_my` |

外层容器 MUST 是 1 个 `Surface`,顶部 24dp 圆角,`containerColor = colorScheme.surfaceVariant`,`tonalElevation = 1.dp`(营造跟【我的】tab 的 surfaceVariant 背景 + SectionCard 卡片同源的视觉)。

中央槽位 MUST 是"空槽"(内部放 FAB,本身不响应 tab 选中)。

#### Scenario: 容器为 surfaceVariant 圆角 Surface
- **WHEN** `AppTabBar` 渲染
- **THEN** 外层是 1 个 `Surface`,`shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)`,`containerColor = colorScheme.surfaceVariant`,`tonalElevation = 1.dp`

#### Scenario: 选中 tab 子卡用 primary 容器
- **WHEN** 当前选中 tab 是笔记 / 我的
- **THEN** 对应槽位 `Surface` 子卡 `containerColor = colorScheme.primary`,`contentColor = colorScheme.onPrimary`,图标为 `Icons.Filled.*` 实心版本,文案 `Text(stringResource(...))` 用 `contentColor`

#### Scenario: 未选中 tab 子卡透明容器
- **WHEN** 当前选中 tab 是 X(X ≠ 当前槽位)
- **THEN** 对应槽位 `Surface` 子卡 `containerColor = Color.Transparent`,`contentColor = colorScheme.onSurfaceVariant`,图标为 `Icons.Outlined.*` 描边版本,文案 `Text(stringResource(...))` 用 `contentColor`

#### Scenario: tab 子卡圆角 16dp
- **WHEN** `AppTabBar` 渲染两个 `Surface` 子卡
- **THEN** 子卡 `shape = RoundedCornerShape(16.dp)`,`clickable { onClick }`(整张子卡可点,不仅是图标)

#### Scenario: tab 槽文案来自 strings.xml
- **WHEN** 渲染任一 tab 槽位
- **THEN** 文案 MUST `Text(stringResource(R.string.tab_notes))` / `R.string.tab_my`,**不**直接 `Text("笔记")` 硬编码

#### Scenario: 中央 FAB 抬出 bar 上沿
- **WHEN** `AppTabBar` 渲染
- **THEN** 中央 `FloatingActionButton` 视觉上**高于**外层 `Surface`,`Modifier.offset(y = -20.dp)` 抬出 bar 上沿,`elevation = 12.dp` 阴影可见

#### Scenario: 中央 FAB 容器色 = primary
- **WHEN** 渲染中央 FAB
- **THEN** `containerColor = colorScheme.primary`,`contentColor = colorScheme.onPrimary`(跟 selected tab 视觉呼应),`shape = CircleShape`,`Icon(Icons.Filled.Add)` + `contentDescription = stringResource(R.string.tab_new_note_cd)`,不显示 label 文本

#### Scenario: 不再使用 M3 NavigationBar / NavigationBarItem
- **WHEN** grep `AppShell.kt` `NavigationBar` / `NavigationBarItem`
- **THEN** 0 匹配(标准 M3 组件整段替换为自定义 Surface + 2 子卡)
