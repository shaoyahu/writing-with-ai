## ADDED Requirements

### Requirement: PromptTemplateStore persists templates via DataStore

`PromptTemplateStore` MUST 走 `androidx.datastore.preferences.core.Preferences`,key 集合:

| key | 类型 | 用途 |
| --- | --- | --- |
| `prompt_template_expand` | `String?` | 用户自定义"扩写"操作 system prompt |
| `prompt_template_polish` | `String?` | 用户自定义"润色"操作 system prompt |
| `prompt_template_organize` | `String?` | 用户自定义"整理"操作 system prompt |

`PromptTemplateStore` MUST 暴露:
- `suspend fun getForOp(op: WritingOp): String?` — key 不存在 / value 为 null / value 为空字符串 均 return `null`(调用方走 fallback 默认)
- `suspend fun setForOp(op: WritingOp, prompt: String)`
- `suspend fun resetToDefault(op: WritingOp)` — 调 `setForOp(op, "")` 触发 fallback
- `fun observeAll(): Flow<PromptTemplates>` — 3 key combine + `stateIn(SharingStarted.Eagerly, PromptTemplates.EMPTY)`

DataStore 文件名 MUST 为 `prompt_template_store`(默认 preferences,与 `consent_store` 同目录,不同文件)。

#### Scenario: 首次读未设置
- **WHEN** App 首次启动,3 个 key 全部为 null
- **THEN** `getForOp(EXPAND) == null`;`observeAll().first().expand == null`;调用方走 `DefaultPrompts.forOp(EXPAND)` fallback

#### Scenario: 用户设置非空 prompt
- **WHEN** `setForOp(POLISH, "你是一位小红书爆款写手...")`
- **THEN** DataStore `prompt_template_polish` key 写入该字符串;`getForOp(POLISH)` 返回该字符串

#### Scenario: 用户清空 prompt 走 fallback
- **WHEN** `setForOp(ORGANIZE, "")` 调用
- **THEN** DataStore 写入空字符串;`getForOp(ORGANIZE) == null`(空字符串视为 fallback)

#### Scenario: resetToDefault
- **WHEN** `resetToDefault(EXPAND)` 调用
- **THEN** DataStore 写入空字符串;`getForOp(EXPAND) == null`

#### Scenario: observeAll 实时反映变更
- **WHEN** `setForOp(POLISH, "新 prompt")` 调用
- **THEN** `observeAll().first().polish == "新 prompt"`;`observeAll().first().expand` 保持原值(其他 op 不变)

### Requirement: DefaultPrompts provides fallback for M3 write-dead prompts

`DefaultPrompts` MUST 是 `object`(kotlin singleton)暴露 `fun forOp(op: WritingOp): String`,返回 M3 写死的 3 类 system prompt 原内容(扩写 / 润色 / 整理)。`AiActionViewModel` MUST 在 `PromptTemplateStore.getForOp(op) == null` 时调 `DefaultPrompts.forOp(op)` 拿 system prompt。

`core/ai/prompt/{Expand,Polish,Organize}.kt` 3 个分散文件 MUST NOT 存在(本 change 合并到 `DefaultPrompts`);M3 写死的内容 MUST 保留原意(只是搬家,不优化 / 翻译 / 修改)。

#### Scenario: 模板为空走 fallback
- **WHEN** `AiActionViewModel.start(EXPAND, sourceText, noteId)` 调用,`PromptTemplateStore.getForOp(EXPAND) == null`
- **THEN** `systemPrompt = DefaultPrompts.forOp(EXPAND)`,返回 M3 写死的扩写 system prompt 原文

#### Scenario: 模板非空走用户配置
- **WHEN** `PromptTemplateStore.getForOp(POLISH) == "你是一位正式文风的润色助手..."`
- **THEN** `systemPrompt = "你是一位正式文风的润色助手..."`,不走 fallback

#### Scenario: M3 写死 prompt 原文保留
- **WHEN** grep `core/ai/prompt/Expand.kt` / `Polish.kt` / `Organize.kt`
- **THEN** 0 个匹配(3 文件已合并到 `DefaultPrompts`)

#### Scenario: DefaultPrompts 单点访问
- **WHEN** grep `core/ai/prompt/DefaultPrompts.kt` "forOp"
- **THEN** 至少 1 个 `fun forOp(op: WritingOp): String` 定义,3 个 `when` 分支覆盖 EXPAND / POLISH / ORGANIZE

### Requirement: PromptTemplateScreen provides 3-tab edit UI

`PromptTemplateScreen` MUST 是 `@Composable`,接收 `viewModel: PromptTemplateViewModel`(Hilt 注入),UI 包含:
- `TabRow` 3 Tab:扩写 / 润色 / 整理(文案走 `R.string.prompt_op_expand` / `polish` / `organize`)
- 当前 Tab 渲染 1 个 `OutlinedTextField`(multiline,`maxLines = 10`),value = `viewModel.uiState.value[op].draft`,`onValueChange = viewModel::onPromptChange(op, newValue)`
- "恢复默认" 按钮(`OutlinedButton`,文案 `R.string.prompt_reset_default`),`onClick = viewModel::resetToDefault(op)`
- 顶部说明文字:"自定义 AI 操作的 system prompt;空时使用默认;立即对下次 AI 操作生效"

`onPromptChange` MUST 走 `viewModel` 内部 `debounce(500ms)` 写 DataStore,Tab 切换 MUST 立即 flush 一次(避免 debounce 期间切 Tab 丢字)。

#### Scenario: 切换 Tab 渲染对应 prompt
- **WHEN** 用户点"润色" Tab
- **THEN** UI 渲染"润色" prompt 草稿;`viewModel.uiState.value[POLISH].draft` 同步显示

#### Scenario: 编辑 prompt 500ms 后自动保存
- **WHEN** 用户在"扩写" Tab 输入 5 字符
- **THEN** 500ms 内 `viewModel` 内部 debounce 触发 `setForOp(EXPAND, "用户输入 5 字符")`;`observeAll().first().expand == "用户输入 5 字符"`

#### Scenario: 切 Tab 立即 flush
- **WHEN** 用户在"扩写" Tab 输入 5 字符(不到 500ms)→ 立即切到"润色" Tab
- **THEN** "扩写" prompt 立即保存(不被 debounce 吞);切到"润色" Tab 渲染润色草稿

#### Scenario: 点"恢复默认"
- **WHEN** 用户在"整理" Tab 点"恢复默认"
- **THEN** `viewModel.resetToDefault(ORGANIZE)` 调用 → DataStore 写入空字符串;Tab 文本框 value 变空(`getForOp == null`);`DefaultPrompts.forOp(ORGANIZE)` 实际生效

### Requirement: Settings screen entry in QuickNoteListScreen overflow menu

`QuickNoteListScreen` TopAppBar `actions` overflow menu MUST 新增"设置"菜单项(文案 `R.string.settings_title`),在 M4-3 "数据迁移" 项之前。点击 MUST 跳 `SettingsScreen`(`navController.navigate(Settings)`)。

`SettingsScreen` MUST 渲染 1 个 `LazyColumn` 列出"AI 提示词模板"功能项(文案 `R.string.settings_prompt_title`),点击跳 `PromptTemplateScreen`(`navController.navigate(SettingsPromptTemplate)`)。

#### Scenario: overflow menu 含设置入口
- **WHEN** grep `QuickNoteListScreen.kt` "settings_title"
- **THEN** 至少 1 个 `DropdownMenuItem(text = { Text(stringResource(R.string.settings_title)) }, onClick = { navController.navigate(Settings) })`

#### Scenario: 设置屏列出提示词模板入口
- **WHEN** 用户在 SettingsScreen 看列表
- **THEN** 列表第 1 项"AI 提示词模板"显示;点击跳 PromptTemplateScreen

#### Scenario: 设置入口不在 TopAppBar 显眼位置
- **WHEN** grep `QuickNoteListScreen.kt` "navigate(Settings)"
- **THEN** 0 个直接 `IconButton(onClick = { navController.navigate(Settings) })`(只在 overflow menu 内)— TopAppBar 不被设置按钮占据

### Requirement: feature/settings/ package is self-contained

`feature/settings/` MUST 自包含:跨 feature 引用(若有)走 `feature/settings/SettingsEntry.kt` object 暴露,不允许 `feature/aiwriting/**` / `feature/quicknote/**` / `app/**` 直接 import `PromptTemplateScreen` / `PromptTemplateViewModel` / `SettingsScreen` 等内部文件(只允许 import `SettingsEntry`)。

#### Scenario: 其他 feature 不直接 import settings 内部
- **WHEN** `grep -rE "feature.settings.(PromptTemplateScreen|PromptTemplateViewModel|SettingsScreen)" app/src/main/java/com/yy/writingwithai/feature/(aiwriting|quicknote|app)/`
- **THEN** 0 匹配(只允许 `feature.settings.SettingsEntry` 之类入口 object)
