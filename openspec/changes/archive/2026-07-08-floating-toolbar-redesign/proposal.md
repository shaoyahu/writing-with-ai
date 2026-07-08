# floating-toolbar-redesign

## Why

笔记详情屏的浮选工具栏 `SelectionFloatingToolbar`(`feature/quicknote/detail/SelectionFloatingToolbar.kt`)目前配色被用户反馈为"丑":

- 背景硬编码 `#FFE0B2`(琥珀浅)
- 边框硬编码 `#E65100`(深橙)
- "加入实体" 已添加态用 `primaryContainer` 浅绿——但跟主背景琥珀对比度过大,色块割裂
- 整体跟 App 主题(墨绿 `#1B6B4A` + 琥珀 `#D4940A`)和 `design-system-v2` 的"border-card 风格"不统一

虽然上次改动已经走通 `MaterialTheme.colorScheme.*` 引用,但视觉仍然不达预期(用户截图反馈):选区文字是高亮蓝/绿,但工具栏还是橙底深橙框,显得"不像同一个 App"。需要一次正经的视觉重做。

**不做**这次改动的后果:工具栏继续被认为是"从别的 App 借来的",影响整体品质感和"AI 辅助写作"的产品定位。

## What Changes

- **`SelectionFloatingToolbar` 整体视觉重做**:从"两个 FilledTonalButton 塞 Row"重构为更克制、信息层级更清晰的 Material 3 组件组合。
- 配色统一走 `design-system-v2` 已有的 token:背景 `surfaceContainerHigh`、边框 `outline`(去掉,改用 `tonalElevation`)、已添加态用 `secondaryContainer`(跟现有选中态文字色保持色系连贯)。
- 操作图标区不直接画 Row + Button,改用 IconButton 风格 + label 副文本(降低视觉噪音)。
- 顶角加一个细线 "AI" 角标(类似 M3 Chip 的 assist 风格),替代当前 dropdown 触发——更轻量。
- DropdownMenu 项视觉同步:每项左侧 icon + 右侧 label + 右侧 `Cmd/Enter` hint 占位(空 string 不渲染,留扩展)。

**非 BREAKING**:`SelectionFloatingToolbar(...)` 函数签名已支持 `isEntityAdded` 新参数,新改动只动内部实现 + 颜色引用 + 子组件排版;`QuickNoteDetailScreen` 调用点不动。

## Capabilities

### Modified Capabilities

- `quick-note`:浮选工具栏视觉规则(`SelectionFloatingToolbar` 配色 / 形状 / 排版)
- `design-system-v2`:新增"surface overlay 组件"语义(浮层式 surface 用 `surfaceContainerHigh` + `tonalElevation`)

## Impact

- **新代码**:无,全部在现有 `SelectionFloatingToolbar.kt` 内重构
- **复用**:`MaterialTheme.colorScheme.surfaceContainerHigh` / `secondaryContainer` / `onSurface` / `onSurfaceVariant` / `outline`(M3 标准 ColorScheme 都有,无需新增 token)
- **依赖**:无新增依赖
- **测试**:现有 `assembleDebug` + `ktlintCheck` 不破;真机走详情屏选词流程肉眼确认
- **i18n**:不动 strings.xml(`selection_toolbar_add_entity` 等文案已有)
