## Context

- **当前实现**:`app/AppShell.kt` 的 `AppTabBar` 私有 Composable 用 `Box` 外层 + `Surface`(顶部 24dp 圆角 + surfaceVariant + 1dp tonalElevation)内嵌 `Row` + 2 个 `Surface(onClick)` 子卡(笔记 / 我的，16dp 圆角)+ 中央 `FloatingActionButton`(`Box.align(TopCenter).offset(y = -20.dp)` 抬出 bar 上沿，12dp elevation,primary 容器)。**第一版(2026-06-30)**:用户反馈中央 FAB 凸起突兀、跟扁平 surfaceVariant 卡片不一致，改成内嵌 primary 子卡(`CenterCreateCard`)，无 offset 无 elevation。**第二版(2026-06-30 v4)**:用户反馈 primary 色中央按钮"笨重"、没有"所有位置都可以添加"的感觉，改成 primaryContainer 柔和背景 + "+ 新建" label，结构跟两侧 TabCard 对称(icon + spacer + label)
- **目标视觉**:跟【我的】tab(`MyScreen.kt`)的 `SectionCard`(12dp 圆角 + 0dp elevation + surface 容器)+ `ListItem`(`primary` tint icon + `onSurfaceVariant` chevron)同源;底部 tab bar 整体成"卡片化"语言
- **设计 token 复用**:`LocalCornerRadius.current`(`app/ui/theme/Theme.kt` 提供 md/lg/xl 三档)、`MaterialTheme.colorScheme.{primary,onPrimary,surfaceVariant,onSurfaceVariant}`
- **约束**:`AppShell` 公共 API(`rootNavController` / `onCreateClick` 形参)不动;`AppTabBar` 私有 Composable 形参(`currentDestination` / `onSelectTab` / `onCenterFabClick`)不动;NavHost / 子 NavController / popUpTo 锚点策略不动
- **dependencies**:无新增第三方依赖;`Surface` / `Row` / `Arrangement.SpaceBetween` 已在 material3 / foundation BOM 内

## Goals / Non-Goals

**Goals:**
- 视觉对齐【我的】tab:圆角卡片 + primary tint icon + surfaceVariant 容器
- 三槽内嵌对称布局(笔记 / + / 我的)，无凸起无 elevation，完全 inline 在 bar 内
- 中央「+」为 primaryContainer 柔和色子卡(`CenterCreateCard`)，含"+ 新建" label，始终高亮，作为「创建」主焦点持续可见
- 保持功能性:2 tab + 中央创建入口，选中态可读，点击区域 ≥ 48dp
- insets 处理保留(`windowInsetsPadding(systemBars.only(Bottom))`)
- 入口签名 / 回调 / strings.xml 不动

**Non-Goals:**
- 不动 tab 数量(保持 2 个:笔记 / 我的)
- 不动中央「+」入口语义(全局新建笔记入口)，只是从 FAB overlay 改成内嵌子卡
- 不动 `onSelectTab(Any)` 回调类型(仍传 route 实例)
- 不动 `composable<Notes>` / `composable<Me>` block 内部逻辑
- 不改 spec 里"Center FAB creates a new note from any tab" / "Tab switching uses NavController state" / 其他非样式 Requirement(语义保留，实现细节换)

## Decisions

### D1: 外层用 1 个 `Surface` 而非 `NavigationBar`

**选 Surface**:M3 `NavigationBar` 不支持自定义容器色 / 圆角 / 表面变体，只能"贴底"且配色受 colorScheme.NavigationBar 约束。要把 tab bar 视觉改成"surfaceVariant 圆角卡片"，必须脱离 NavigationBar 走自定义 Surface。

**否决 M3 NavigationBar 改色**:`MaterialTheme.colorScheme` 没有 NavigationBar 容器色 override 入口(`NavigationBarTokens` 在 material3 1.7+ 是 internal)，改不动。

**否决保留 NavigationBar + 自定义 background Modifier**:`NavigationBar` 内部 `Surface` 已经撑满，外面包任何 modifier 都会被裁。

### D2: 中央「+」改成内嵌子卡(`CenterCreateCard`)而非凸起 FAB

**选内嵌子卡**:第一版设计用 `Box` overlay + `FloatingActionButton` + `offset(y = -20.dp)` 把圆形 FAB 抬出 bar 上沿。视觉上"凸起"突兀，跟扁平 surfaceVariant 容器语言不一致;且 FAB 用 `secondary` 改 `primary` 后跟选中 tab 子卡同色相邻，层级靠 elevation 区分，脆弱。第二版改成内嵌 primary 子卡(仅 `Add` icon，无 label)，用户反馈"笨重、没有所有位置都可以添加的感觉"。**第三版(v4)**改成 `primaryContainer` 柔和背景 + `onPrimaryContainer` 内容色 + `Add` icon + `Spacer(4dp)` + `Text("+ 新建")` label，结构跟两侧 `TabCard` 完全对称(icon + spacer + label)，视觉权重均匀，primaryContainer 比 primary 更柔和不"笨重",label 文字明确传达"可创建"语义。

**否决保留圆形 FAB + 降低凸起幅度**:`offset` 从 -20dp 改 -8dp 仍突兀，且圆形轮廓在 surfaceVariant 卡片上没视觉锚点;FAB 跟 tab 子卡形态差异(圆 vs 圆角矩形)继续割裂。

**否决圆形 outline 按钮**:轮廓线在 surfaceVariant 背景上对比度低，「+」图标作为 primary action 视觉权重不够。

**否决扩宽 primary chip**(96dp 宽 + 「+ 新建」文字):三槽宽度不等，中间独大打破视觉均匀;「新建」二字跟两侧「笔记」「我的」形成 3-字 vs 2-字 label 节奏不一致。

**否决 primary 色无 label 子卡(v3)**:用户反馈 primary 色太"笨重"，且无 label 时用户无法一眼看出"所有位置都可以添加"。primaryContainer 更柔和，label 明确传达语义。

### D3: 子卡用 `Surface(onClick = ...)` 而非 `Card(onClick = ...)`

**选 Surface**:M3 `Card` 默认 0dp elevation + 圆角，但走的是 `ElevatedCard` / `OutlinedCard` / `FilledCard` 三种语义，在本场景下"按钮化的卡片"语义不准确。`Surface(onClick = ...)` 走 `Surface` + `clickable` 组合，跟"button-like 容器"语义更匹配，且 `containerColor` / `contentColor` 显式可控。

**否决 Card + clickable**:`Card` 没有 `onClick` slot，得用 `Modifier.clickable {}` 套外面，不如 `Surface(onClick)` 干净。

### D4: elevation 1dp 的 tonalElevation(非 shadowElevation)

**选 tonalElevation**:M3 `Surface` 的 `tonalElevation` 在 light theme 表现为"稍微提亮的 surface 色调"，在 dark theme 表现为"稍微变暗"，营造"轻微浮起"的视觉;不需要真实阴影，符合"圆角卡片在 surfaceVariant 背景上"扁平风格。

**否决 shadowElevation**:`shadowElevation` 画真实阴影，会让 tab bar 底部出现阴影线，跟【我的】tab 的扁平卡片不一致。

### D5: tab 选中态用 `primary` 容器色 + `onPrimary` 内容色

**选 primary 容器 + onPrimary 内容**:M3 标准"filled button / selected navigation item"配色，符合 Material 3 颜色系统;onPrimary 自动反白(主题墨绿 → 米白)。

**否决 primaryContainer 容器色**:primaryContainer 是 M3 3 档色阶的中间档，比 primary 浅;在 surfaceVariant 背景上对比度不够。

### D6: tab 子卡 `clickable` 整张可点，不仅是 Icon / Text

**选整卡 clickable**:用户手指在 tab 子卡任意位置都能点，触控目标 56-72dp，符合 Material 3 NavigationBarItem 默认触控目标规范。

**否决只 Icon / Text 可点**:触控目标过小(< 32dp)，无障碍工具 / 大手指用户难以点中。

## Risks / Trade-offs

- **[R1] 中央「+」始终 primaryContainer 高亮，与选中 tab 子卡(primary)不同色** → 接受:primaryContainer 是 M3 3 档色阶的中间档，比 primary 浅，视觉上与选中 tab 的 primary 形成层级区分;中央作为常驻创建入口而非选中态，语义上不冲突;三槽 inline 在同一 baseline，色块边界清晰(`Modifier.padding` + `Arrangement.spacedBy(8.dp)`)
- **[R2] `Surface` 容器色覆盖了 `MaterialTheme.colorScheme.surfaceVariant`，可能跟其它 surfaceVariant 使用冲突(比如 Scaffold containerColor)** → Mitigation:`AppShell` 外层 Scaffold 用 `contentWindowInsets = WindowInsets(0)`,inner 各 Screen 自管背景(已有，见 ux-2026-06-23 反馈);tab bar 自身的 surfaceVariant 不外溢
- **[R3] `Surface(onClick)` 走 ripple effect，可能跟 NavigationBarItem 默认 ripple 表现不同** → 接受:`Surface(onClick)` 的 ripple 是 M3 标准 ripple(`LocalIndication.current`)，与项目其它 Surface 按钮一致;NavigationBarItem 内部也是 Surface + clickable
- **[R4] 中央「+」含"+ 新建" label，与两侧 tab label 可能混淆** → Mitigation:中央按钮用 primaryContainer 色(跟两侧 selected primary 不同色)，且 label 前缀"+"号明确标识"创建动作"而非"tab 名称";`Icon` 的 `contentDescription = stringResource(R.string.tab_new_note_cd)` 给无障碍工具读屏

## Migration Plan

- 单文件改动:`app/src/main/java/com/yy/writingwithai/app/AppShell.kt` — 只动 `AppTabBar` 私有 Composable，不动 `AppShell` 公共 Composable / NavHost / NavController 逻辑
- strings.xml 新增 `tab_new_note`("+ 新建")字符串资源
- 无 Nav route 改动 / 无 Hilt 模块改动 / 无 DB schema 改动
- 部署:`/opsx:apply` 实施 tasks.md → `assembleDebug` → `ktlintCheck` → `testDebugUnitTest` → 装机肉眼比对(USER-OWNED)
- 回滚:`git revert` 单 commit 即可，因改动 1 文件 1 Composable，无副作用

## Open Questions

(无 — 设计 token / 颜色 / 圆角尺寸全部从【我的】tab 的现有用法直接对齐，无新决策点)
