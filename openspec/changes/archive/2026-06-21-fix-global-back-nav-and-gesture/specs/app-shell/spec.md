# app-shell Specification (delta)

## MODIFIED Requirements (fix-global-back-nav-and-gesture)

### Requirement: AppNav defines an empty NavHost (delta)

继承原 Requirement 不变;**新增 Scenario**:

#### Scenario: 所有非主页 destination TopAppBar 含 navigationIcon = ArrowBack

- **WHEN** 任何非 `QuicknoteList`(主页)的 Screen Composable 使用 `Scaffold(topBar = { TopAppBar(...) })`
- **THEN** `TopAppBar` MUST 含 `navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } }`;`onBack` MUST 是由 `AppNav` 传入的 `() -> Unit = { navController.popBackStack() }`(或等价)
- 当前覆盖清单:`QuickNoteDetailScreen` / `QuickNoteEditorScreen` / `SettingsDataScreen` / `SettingsScreen` / `ModelManagementScreen` / `ModelProviderDetailScreen` / `PromptTemplateScreen`;`OnboardingRoute` 全屏滚动无 TopAppBar 豁免;`QuickNoteListScreen` 主页豁免(无上一页)
- 自动化校验:`grep -rE "topBar = \{" app/src/main/java/com/yy/writingwithai/feature/ | wc -l` 应等于含 ArrowBack 的屏数;`grep -rE "SettingsScreen\.kt" "navigationIcon" app/src/main/java/com/yy/writingwithai/feature/settings/` 至少 1 匹配
