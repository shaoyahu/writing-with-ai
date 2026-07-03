## Why

项目中所有 DropdownMenu / ExposedDropdownMenu 均使用 Material3 默认参数（4dp 圆角、3dp 阴影、surface 背景色），与 APP 整体设计语言（12-16dp 圆角、低阴影+色差分层、surfaceContainerHigh 浮起色）严重不协调。此外 3 处 ExposedDropdownMenuBox 代码几乎重复，选择型菜单无选中态标记。需要创建统一封装组件，改造全部 8 处使用点，使下拉框风格与 ActionSheet / SectionCard 等已有组件对齐。

## What Changes

- 新建 `core/ui/dropdown/AppDropdownMenu.kt`，包含两个统一下拉框组件：
  - `AppActionDropdown`：操作菜单（三点菜单 / 长按菜单），声明式 `items` 列表，支持 `isDestructive` 标记破坏性操作
  - `AppSelectionDropdown`：选择菜单（ExposedDropdownMenuBox 封装），泛型 `<T>`，自动管理 expanded 状态，选中项 trailing Check icon
- 统一样式参数：`surfaceContainerHigh` 容器色 + `RoundedCornerShape(cornerRadius.md)` = 12dp + `shadowElevation = 2dp`
- 改造 4 处操作菜单（A 类）：ModelManagementScreen × 2、QuickNoteDetailScreen × 1、QuickNoteListScreen × 1
- 改造 3 处选择菜单（B 类）：CustomProviderEditScreen × 2、ModelProviderDetailScreen × 1
- 改造 1 处按钮触发选择（C 类）：AliasManagementScreen × 1
- 删除各 Screen 中重复的 private composable（ApiFormatDropdown / AuthStyleDropdown / ModelDropdown）

## Capabilities

### New Capabilities
- `app-dropdown-menu`: 统一下拉框组件（AppActionDropdown + AppSelectionDropdown），封装样式规范和交互模式

### Modified Capabilities
- `design-system-v2`: 下拉框组件纳入设计系统 token 体系（cornerRadius.md / surfaceContainerHigh / shadowElevation）

## Impact

- **代码**：`core/ui/dropdown/` 新增 1 文件；`feature/settings/model/` 3 个 Screen 修改；`feature/quicknote/` 2 个 Screen 修改；`feature/settings/alias/` 1 个 Screen 修改
- **依赖**：无新外部依赖，仅使用现有 Material3 + 项目内 design token
- **API**：无公开 API 变更，纯 UI 层重构
- **测试**：现有 UI 测试可能需要更新 import 路径（DropdownMenu → AppActionDropdown），功能逻辑不变
