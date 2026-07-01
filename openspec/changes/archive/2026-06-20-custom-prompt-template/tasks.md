## 1. DataStore:PromptTemplateStore

- [x] 1.1 建 `core/prefs/PromptTemplateStore.kt`:interface + `PromptTemplateStoreImpl` + `PromptTemplates` data class
- [x] 1.2 DataStore name = `prompt_template_store`;3 key 集合
- [x] 1.3 `getForOp(op)` fallback 规则(null / "" → return null)
- [x] 1.4 `setForOp(op, prompt)` 写 DataStore
- [x] 1.5 `resetToDefault(op)` 触发 fallback
- [x] 1.6 `observeAll()` stateIn(Eagerly, EMPTY)

## 2. Hilt module

- [ ] 2.1 扩 `core/prefs/PrefsModule.kt`:`@Provides @Singleton fun providePromptTemplateStore(...)`
- [x] 2.2 加 Fake:`core/prefs/FakePromptTemplateStore.kt`(in-memory + seed hook)

## 3. core/ai/prompt/ 合并

- [x] 3.1 建 `core/ai/prompt/DefaultPrompts.kt`
- [x] 3.2 搬 M3 原文(原样保留)
- [x] 3.3 删 `core/ai/prompt/{ExpandPrompt, PolishPrompt, OrganizePrompt}.kt`(原文件名是 XxxPrompt.kt 不是 .kt，已删)

## 4. feature/settings/ 模块

- [ ] 4.1 建 `feature/settings/SettingsEntry.kt`:`object SettingsEntry { ROUTE_SETTINGS = "settings"; ROUTE_PROMPT_TEMPLATE = "settings/prompt-template"; fun SettingsRoute(navController, ...); fun PromptTemplateRoute(onBack) }`
- [ ] 4.2 建 `feature/settings/SettingsScreen.kt`:`@Composable fun SettingsScreen(navController, onConsented = {})` 渲染 1 项 LazyColumn"AI 提示词模板" → 跳 `SettingsPromptTemplate`
- [ ] 4.3 建 `feature/settings/prompt/PromptTemplateScreen.kt`:TabRow 3 Tab + OutlinedTextField + "恢复默认" 按钮
- [ ] 4.4 建 `feature/settings/prompt/PromptTemplateViewModel.kt`:`@HiltViewModel`，注入 `PromptTemplateStore`，暴露 `uiState: StateFlow<Map<WritingOp, PromptDraft>>`;`onPromptChange(op, value)` debounce 500ms 写;`onTabSwitch(op)` 立即 flush;`resetToDefault(op)` 调 store
- [ ] 4.5 验证:UiState 含 `currentOp`(当前 Tab)+ `drafts: Map<WritingOp, String>`(3 op 各自草稿)

## 5. AppNav + overflow menu

- [ ] 5.1 `app/AppNav.kt` 加 `@Serializable data object Settings` + `@Serializable data object SettingsPromptTemplate` + 2 个 `composable<...>` block
- [ ] 5.2 `feature/quicknote/list/QuickNoteListScreen.kt` overflow menu 加"设置"项(在"数据迁移"前),`onClick = navController.navigate(Settings)`

## 6. AiActionViewModel 集成模板

- [ ] 6.1 `feature/aiwriting/streaming/AiActionViewModel.kt` 构造函数加 `promptTemplateStore: PromptTemplateStore` + `secureApiKeyStore: SecureApiKeyStore` 依赖
- [ ] 6.2 `start(op, sourceText, noteId)`:`val providerId = secureApiKeyStore.resolveProviderId()`(suspend，同步)+ `val systemPrompt = promptTemplateStore.getForOp(op) ?: DefaultPrompts.forOp(op)`(suspend)
- [ ] 6.3 既有 `AiActionViewModelTest` 5 tests 补 mock `promptTemplateStore` / `secureApiKeyStore` 形参

## 7. i18n

- [ ] 7.1 `res/values/strings.xml` +12 个 key:
  - `settings_title` = "设置"
  - `settings_prompt_title` = "AI 提示词模板"
  - `prompt_op_expand` = "扩写"
  - `prompt_op_polish` = "润色"
  - `prompt_op_organize` = "整理"
  - `prompt_reset_default` = "恢复默认"
  - `prompt_hint` = "自定义 AI 操作的 system prompt;空时使用默认;立即对下次 AI 操作生效"
  - `prompt_saved_toast` = "已保存"(可选)
- [ ] 7.2 `res/values-en/strings.xml` 同步 12 个 `TODO(en):` 占位

## 8. 测试

- [ ] 8.1 建 `app/src/test/java/com/yy/writingwithai/core/prefs/PromptTemplateStoreTest.kt`:5 tests(首次 null / set 后 get / 空字符串 fallback / resetToDefault / observeAll 实时)
- [ ] 8.2 建 `app/src/test/java/com/yy/writingwithai/feature/settings/PromptTemplateViewModelTest.kt`:3 tests(草稿初始化 / onPromptChange debounce / onTabSwitch 立即 flush)
- [ ] 8.3 跑 `./gradlew :app:compileDebugUnitTestKotlin` 验证编译通过

## 9. 验收

- [ ] 9.1 `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- [ ] 9.2 `./gradlew :app:ktlintCheck` → BUILD SUCCESSFUL
- [ ] 9.3 `./gradlew :app:lintDebug` → BUILD SUCCESSFUL
- [ ] 9.4 `grep -rE "feature.settings.(PromptTemplateScreen|PromptTemplateViewModel|SettingsScreen)" app/src/main/java/com/yy/writingwithai/feature/(aiwriting|quicknote|app)/` → 0 匹配(self-containment)
- [ ] 9.5 `grep -rE "Expand.kt|Polish.kt|Organize.kt" app/src/main/java/com/yy/writingwithai/core/ai/prompt/` → 0 匹配(M3 文件已合并)
- [ ] 9.6 更新 `docs/progress.md` 加 custom-prompt-template entry

## 10. 归档

- [ ] 10.1 跑 `/opsx:archive custom-prompt-template` 收口
