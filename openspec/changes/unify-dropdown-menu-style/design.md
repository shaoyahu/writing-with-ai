## Context

当前项目中所有 DropdownMenu / ExposedDropdownMenu 均使用 Material3 默认参数（4dp 圆角、3dp 阴影、surface 背景色），与 APP 整体设计语言不一致。APP 已有的 ActionSheet 组件使用 `surfaceContainerHigh` + `RoundedCornerShape(12.dp)` + `shadowElevation = 6.dp`，SectionCard 使用 `cornerRadius.md` = 12dp。下拉框是唯一未对齐的浮层组件。

全项目共 8 处下拉框使用点：
- A 类（操作菜单，4 处）：ModelManagementScreen × 2、QuickNoteDetailScreen × 1、QuickNoteListScreen × 1
- B 类（选择菜单，3 处）：CustomProviderEditScreen × 2、ModelProviderDetailScreen × 1
- C 类（按钮触发选择，1 处）：AliasManagementScreen × 1

其中 B 类 3 处各自写了几乎相同的 private composable（ApiFormatDropdown / AuthStyleDropdown / ModelDropdown），代码重复度高。

## Goals / Non-Goals

**Goals:**
- 创建统一封装组件，使所有下拉框风格与 ActionSheet / SectionCard 对齐
- 消除 3 处重复的 ExposedDropdownMenuBox 封装代码
- 选择型菜单增加选中态标记（trailing Check icon）
- 破坏性操作（删除等）自动应用 error 色

**Non-Goals:**
- 不改变下拉框的交互逻辑（展开/收起方式、点击行为不变）
- 不引入新的动画效果
- 不改变 DropdownMenu 的定位算法（Material3 Popup 定位）
- 不处理 QuickNoteDetailScreen 中菜单项的条件渲染逻辑（保持原有 enabled 判断）

## Decisions

### D1: 两个组件而非一个

**选择**：`AppActionDropdown`（操作菜单）+ `AppSelectionDropdown`（选择菜单），而非单一 `AppDropdownMenu`。

**理由**：两类菜单的 API 形态完全不同——操作菜单是声明式 `items: List<AppActionItem>`，选择菜单是泛型 `<T>` + `options/selected/onSelected`。强行合并会导致 API 复杂且类型不安全。

**替代方案**：单一组件 + sealed class 区分类型 → 增加调用方复杂度，无实际收益。

### D2: AppActionDropdown 使用声明式 items 而非 @Composable children

**选择**：`items: List<AppActionItem>` 数据类列表，而非 `@Composable DropdownMenuScope.() -> Unit`。

**理由**：操作菜单项通常是静态的（3-10 项），声明式 API 更简洁，且自动统一样式（icon/text/color），调用方无法遗漏样式参数。QuickNoteDetailScreen 的条件渲染通过 `enabled` 字段控制。

**替代方案**：DSL builder（`item { ... }`）→ 灵活但调用方可能遗漏统一样式参数。

### D3: 样式参数对齐 ActionSheet 而非 Material3 默认

**选择**：
- `containerColor = surfaceContainerHigh`（浮起感，与 ActionSheet 一致）
- `shape = RoundedCornerShape(cornerRadius.md)` = 12dp（与 SectionCard/ActionSheet 对齐）
- `shadowElevation = 2dp`（介于 SectionCard 0dp 和 ActionSheet 6dp 之间，下拉框是小型浮层不需要强阴影）
- `tonalElevation = 0dp`（containerColor 已提供分层）

**理由**：下拉框是浮层组件，视觉上应与 ActionSheet 同族。但下拉框面积远小于 ActionSheet，阴影过重会显得突兀，2dp 是合理折中。

### D4: AppSelectionDropdown 内部管理 expanded 状态

**选择**：组件内部 `var expanded by remember { mutableStateOf(false) }`，调用方无需维护。

**理由**：3 处现有代码都在各自 Screen 里手动管理 `var xxxExpanded by remember`，完全重复。封装后调用方只需传 `options/selected/onSelected`。

**替代方案**：hoist expanded 状态 → 调用方仍需维护 state，违背消除重复的目标。

### D5: isDestructive 标记而非自动检测

**选择**：`AppActionItem.isDestructive: Boolean = false`，由调用方显式标记。

**理由**：自动检测（如匹配"删除"文字）脆弱且不可靠。显式标记清晰、可控，与 iOS ActionSheet 的 destructive style 同理。

## Risks / Trade-offs

- **[Material3 DropdownMenu 参数限制]** → DropdownMenu 的 `shape`/`containerColor`/`shadowElevation` 是 Material3 1.2+ 才稳定的参数。当前项目已使用 Material3 1.3+，无兼容风险。
- **[AppSelectionDropdown 泛型与 Compose 性能]** → 泛型 `<T>` 在 Compose 中无性能问题，`options/selected` 作为参数参与 recomposition，与现有代码一致。
- **[AliasManagementScreen C 类改造]** → 原实现用 `OutlinedButton` + 手动 `DropdownMenu`，改为 `AppSelectionDropdown` 后 UI 从按钮触发变为 OutlinedTextField 触发，交互模式变化。→ 可接受，因为 OutlinedTextField 是 Material3 ExposedDropdownMenu 的标准触发方式，且与其他 B 类选择菜单统一。
