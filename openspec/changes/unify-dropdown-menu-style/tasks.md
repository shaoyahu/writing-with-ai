## 1. 统一组件创建

- [x] 1.1 新建 `core/ui/dropdown/AppDropdownMenu.kt`，包含 `AppActionItem` data class + `AppActionDropdown` composable + `AppSelectionDropdown` composable
- [x] 1.2 确认组件样式参数：containerColor=surfaceContainerHigh, shape=cornerRadius.md=12dp, shadowElevation=2dp, tonalElevation=0dp

## 2. A 类改造：操作菜单（4 处 → AppActionDropdown）

- [x] 2.1 改造 `ModelManagementScreen` TopAppBar 三点菜单（1 项：添加）→ AppActionDropdown
- [x] 2.2 改造 `ModelManagementScreen` Provider 卡片三点菜单（2 项：编辑/删除，删除标 destructive）→ AppActionDropdown
- [x] 2.3 改造 `QuickNoteListScreen` 列表长按菜单（3 项：置顶/添加标签/删除，删除标 destructive）→ AppActionDropdown
- [x] 2.4 改造 `QuickNoteDetailScreen` 详情三点菜单（8-10 项，删除标 destructive，同步项 enabled 绑定 syncLoading）→ AppActionDropdown

## 3. B 类改造：选择菜单（3 处 → AppSelectionDropdown）

- [x] 3.1 改造 `CustomProviderEditScreen` ApiFormat 选择 → AppSelectionDropdown，删除 ApiFormatDropdown private composable
- [x] 3.2 改造 `CustomProviderEditScreen` AuthStyle 选择 → AppSelectionDropdown，删除 AuthStyleDropdown private composable，过滤 CUSTOM_HEADER
- [x] 3.3 改造 `ModelProviderDetailScreen` 模型选择 → AppSelectionDropdown，删除 ModelDropdown private composable，默认项 optionLabel 加"(默认)"后缀

## 4. C 类改造：按钮触发选择（1 处 → AppSelectionDropdown）

- [x] 4.1 改造 `AliasManagementScreen` EntityType 选择：OutlinedButton + DropdownMenu → AppSelectionDropdown，删除 typeMenuOpen state
- [x] 4.2 新增 string resource `entity_alias_type_label`（"实体类型"）

## 5. 编译验证

- [x] 5.1 运行 `assembleDebug` 确认编译通过
- [x] 5.2 运行 `ktlintCheck` 确认代码风格通过
- [x] 5.3 运行 `testDebugUnitTest` 确认单测通过
