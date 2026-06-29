## Context

- **当前实现**:`app/AppShell.kt` 的 `AppTabBar` 私有 Composable 用 M3 `NavigationBar` + 2 个 `NavigationBarItem`(槽 1 笔记 / 槽 3 我的)+ 槽 2 `Spacer(56.dp)` 占位,中央 `FloatingActionButton` 用 `Box.align(TopCenter).offset(y = -20.dp)` 抬出 bar 上沿
- **目标视觉**:跟【我的】tab(`MyScreen.kt`)的 `SectionCard`(12dp 圆角 + 0dp elevation + surface 容器)+ `ListItem`(`primary` tint icon + `onSurfaceVariant` chevron)同源;底部 tab bar 整体成"卡片化"语言
- **设计 token 复用**:`LocalCornerRadius.current`(`app/ui/theme/Theme.kt` 提供 md/lg/xl 三档)、`MaterialTheme.colorScheme.{primary,onPrimary,surfaceVariant,onSurfaceVariant}`
- **约束**:`AppShell` 公共 API(`rootNavController` / `onCreateClick` 形参)不动;`AppTabBar` 私有 Composable 形参(`currentDestination` / `onSelectTab` / `onCenterFabClick`)不动;NavHost / 子 NavController / popUpTo 锚点策略不动
- **dependencies**:无新增第三方依赖;`Surface` / `Row` / `Arrangement.SpaceBetween` 已在 material3 / foundation BOM 内

## Goals / Non-Goals

**Goals:**
- 视觉对齐【我的】tab:圆角卡片 + primary tint icon + surfaceVariant 容器
- 保持功能性:2 tab + 中央 FAB,选中态可读,点击区域 ≥ 48dp
- 中央 FAB 跟 selected tab 视觉呼应(都走 primary 容器色),抬出 bar 上沿不变
- insets 处理保留(`windowInsetsPadding(systemBars.only(Bottom))`)
- 入口签名 / 回调 / strings.xml 不动

**Non-Goals:**
- 不动 tab 数量(保持 2)
- 不动中央 FAB 抬出位移(-20.dp)、尺寸(56dp)、shape(CircleShape)
- 不动 `onSelectTab(Any)` 回调类型(仍传 route 实例)
- 不动 `composable<Notes>` / `composable<Me>` block 内部逻辑
- 不改 spec 里"Center FAB creates a new note from any tab" / "Tab switching uses NavController state" / 其他非样式 Requirement
- 不引入新依赖 / 不改 strings.xml

## Decisions

### D1: 外层用 1 个 `Surface` 而非 `NavigationBar`

**选 Surface**:M3 `NavigationBar` 不支持自定义容器色 / 圆角 / 表面变体,只能"贴底"且配色受 colorScheme.NavigationBar 约束。要把 tab bar 视觉改成"surfaceVariant 圆角卡片",必须脱离 NavigationBar 走自定义 Surface。

**否决 M3 NavigationBar 改色**:`MaterialTheme.colorScheme` 没有 NavigationBar 容器色 override 入口(`NavigationBarTokens` 在 material3 1.7+ 是 internal),改不动。

**否决保留 NavigationBar + 自定义 background Modifier**:`NavigationBar` 内部 `Surface` 已经撑满,外面包任何 modifier 都会被裁。

### D2: 子卡用 `Surface(onClick = ...)` 而非 `Card(onClick = ...)`

**选 Surface**:M3 `Card` 默认 0dp elevation + 圆角,但走的是 `ElevatedCard` / `OutlinedCard` / `FilledCard` 三种语义,在本场景下"按钮化的卡片"语义不准确。`Surface(onClick = ...)` 走 `Surface` + `clickable` 组合,跟"button-like 容器"语义更匹配,且 `containerColor` / `contentColor` 显式可控。

**否决 Card + clickable**:`Card` 没有 `onClick` slot,得用 `Modifier.clickable {}` 套外面,不如 `Surface(onClick)` 干净。

### D3: 中央 FAB 容器色从 `secondary` 改为 `primary`

**选 primary**:跟 selected tab 子卡同色,视觉上"selected tab + FAB"形成主色焦点,跟"未选中 tab"形成 onSurfaceVariant 二级对比,层级更清晰。

**否决保留 secondary**:secondary 在 Material 3 默认是 tertiary 类对比色,跟 tab bar 的 2 个 tab 同台时色彩割裂(2 颜色焦点,而不是 1 颜色焦点 + 1 中性焦点)。

### D4: elevation 1dp 的 tonalElevation(非 shadowElevation)

**选 tonalElevation**:M3 `Surface` 的 `tonalElevation` 在 light theme 表现为"稍微提亮的 surface 色调",在 dark theme 表现为"稍微变暗",营造"轻微浮起"的视觉;不需要真实阴影,符合"圆角卡片在 surfaceVariant 背景上"扁平风格。

**否决 shadowElevation**:`shadowElevation` 画真实阴影,会让 tab bar 底部出现阴影线,跟【我的】tab 的扁平卡片不一致。

### D5: tab 选中态用 `primary` 容器色 + `onPrimary` 内容色

**选 primary 容器 + onPrimary 内容**:M3 标准"filled button / selected navigation item"配色,符合 Material 3 颜色系统;onPrimary 自动反白(主题墨绿 → 米白)。

**否决 primaryContainer 容器色**:primaryContainer 是 M3 3 档色阶的中间档,比 primary 浅;在 surfaceVariant 背景上对比度不够。

### D6: tab 子卡 `clickable` 整张可点,不仅是 Icon / Text

**选整卡 clickable**:用户手指在 tab 子卡任意位置都能点,触控目标 56-72dp,符合 Material 3 NavigationBarItem 默认触控目标规范。

**否决只 Icon / Text 可点**:触控目标过小(< 32dp),无障碍工具 / 大手指用户难以点中。

## Risks / Trade-offs

- **[R1] primary 容器色 + primary FAB 在选中笔记 tab 时同色相邻** → Mitigation:中央 FAB 通过 `offset(y = -20.dp)` 抬出 bar 上沿,视觉上跟 tab 子卡在 z 轴上"立体凸起",同色但有 elevation 区分
- **[R2] `Surface` 容器色覆盖了 `MaterialTheme.colorScheme.surfaceVariant`,可能跟其它 surfaceVariant 使用冲突(比如 Scaffold containerColor)** → Mitigation:`AppShell` 外层 Scaffold 用 `contentWindowInsets = WindowInsets(0)`,inner 各 Screen 自管背景(已有,见 ux-2026-06-23 反馈);tab bar 自身的 surfaceVariant 不外溢
- **[R3] `Surface(onClick)` 走 ripple effect,可能跟 NavigationBarItem 默认 ripple 表现不同** → 接受:`Surface(onClick)` 的 ripple 是 M3 标准 ripple(`LocalIndication.current`),与项目其它 Surface 按钮一致;NavigationBarItem 内部也是 Surface + clickable
- **[R4] 中央 FAB 改 primary 后,深色主题下 `primary` 跟 `onPrimary` 对比度可能比 secondary 弱** → Mitigation:M3 默认 `primary` / `onPrimary` 对比度已 ≥ 4.5:1(在 `Theme.kt` 的 `darkColorScheme` 调过),无需额外调

## Migration Plan

- 单文件改动:`app/src/main/java/com/yy/writingwithai/app/AppShell.kt` — 只动 `AppTabBar` 私有 Composable,不动 `AppShell` 公共 Composable / NavHost / NavController 逻辑
- 无新文件、无 strings.xml 改动
- 无 Nav route 改动 / 无 Hilt 模块改动 / 无 DB schema 改动
- 部署:`/opsx:apply` 实施 tasks.md → `assembleDebug` → `ktlintCheck` → `testDebugUnitTest` → 装机肉眼比对(USER-OWNED)
- 回滚:`git revert` 单 commit 即可,因改动 1 文件 1 Composable,无副作用

## Open Questions

(无 — 设计 token / 颜色 / 圆角尺寸全部从【我的】tab 的现有用法直接对齐,无新决策点)
