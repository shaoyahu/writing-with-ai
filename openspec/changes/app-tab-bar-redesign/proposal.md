## Why

当前 `app/AppShell.kt` 的 `AppTabBar` 走标准 M3 `NavigationBar`，跟【我的】tab(`feature/my/MyScreen.kt`)的"圆角 Card + primary tint icon + surface 容器 + surfaceVariant 背景"风格割裂。底部 tab 栏视觉语言跟应用主屏不一致，新用户切 tab 时会感觉换了 app。

## What Changes

- 重写 `app/AppShell.kt` 的 `AppTabBar` Composable:容器从 `NavigationBar` 改为自定义 `Surface` 容器(顶部 24dp 圆角 + surfaceVariant 背景 + 1dp elevation)，复刻【我的】tab 的 `SectionCard` 圆角语言
- 2 个 tab 槽(`Notes` / `Me`)改为内嵌 `Surface` 子卡(16dp 圆角):selected = primary 容器色 + onPrimary 图标+文案;unselected = 透明 + onSurfaceVariant 图标+文案
- 中央 FAB 容器色从 `secondary` 改为 `primary`(跟 selected tab 视觉呼应),elevation 从 8dp 提到 12dp
- 不动:`AppTabBar` 入口签名 / `onSelectTab` / `onCenterFabClick` 回调 / strings.xml(文案不变，继续用 `tab_notes` / `tab_my` / `tab_new_note_cd`)
- 不动:`AppShell` 的 NavHost / 子 NavController / popUpTo 锚点策略
- 不动:tab 数量仍是 2(笔记 / 我的)，中央 FAB 仍是凸起圆形 56dp

## Capabilities

### New Capabilities
(无 — 视觉重设计，沿用现有 `app-tab-bar` capability，不引入独立 capability)

### Modified Capabilities
- `app-tab-bar`:Requirement "Bottom tab bar with three slots and a raised center FAB" 的样式描述(圆角 / 背景色 / 选中态容器 / FAB 颜色 + elevation)需更新，行为(2 tab + 中央 FAB + 入口签名)不变

## Impact

**代码**:
- `app/src/main/java/com/yy/writingwithai/app/AppShell.kt` — 重写 `AppTabBar` Composable 内部实现(主改动，1 个文件)
- 新增 import:`Surface` / `SurfaceContainer` / `Row` / `Arrangement` / `Spacer` 等(如未引入)
- 移除 import:`NavigationBar` / `NavigationBarItem`(若彻底替换)
- `AppShell` 本身的 NavHost / NavController 逻辑不动

**Spec**:
- `openspec/specs/app-tab-bar/spec.md` 的 "Bottom tab bar with three slots and a raised center FAB" Requirement 改写样式描述(槽位 1/2/3 的容器 / 圆角 / 颜色 / elevation);"Center FAB creates a new note from any tab" / "Tab switching uses NavController state" / 其他 Requirement **不动**

**依赖**:
- 无新依赖(`Surface` / `LocalSpacing` / `LocalCornerRadius` 已在 version catalog + 项目内使用)
- strings.xml 不动

**风险**:
- 视觉改动小(2 个 tab + 1 个 FAB)，无功能性回归风险
- `Box(windowInsetsPadding).offset(y = -20.dp)` 抬出逻辑要保留，避免 FAB 被 bar Surface 遮成横线
- 中央 FAB 改 `primary` 容器色后，需确认在 `Notes`(默认 selected = 笔记)选中时，FAB 跟 selected tab 视觉不冲突(同色相邻)
