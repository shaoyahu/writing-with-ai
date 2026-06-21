# custom-prompt-template Specification

## Purpose

TBD - created by archiving change `custom-prompt-template`(2026-06-20)。定义用户可管理 3 类 AI 操作(扩写 / 润色 / 整理)的 system prompt + DataStore 持久化 + Settings 屏编辑 UI + fallback 默认 prompt 的契约。

## Requirements

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

#### Scenario: getForOp 返回 null 时由 VM 层补默认(非 store 职责)
- **WHEN** DataStore `prompt_template_expand` key 为 null
- **THEN** `PromptTemplateStore.getForOp(EXPAND) == null`(store 行为不变,空 = fallback);`PromptTemplateViewModel.init` 检测 null → 调 `DefaultPrompts.forOp(EXPAND)` → `drafts[EXPAND] = <默认 prompt 文本>`(VM 行为,store 不感知)

### Requirement: DefaultPrompts provides fallback for M3 write-dead prompts

`DefaultPrompts` MUST 是 `object`(kotlin singleton)暴露 `fun forOp(op: WritingOp): String`,返回 M3 写死的 3 类 system prompt 原内容(扩写 / 润色 / 整理)。`AiActionViewModel` MUST 在 `PromptTemplateStore.getForOp(op) == null` 时调 `DefaultPrompts.forOp(op)` 拿 system prompt。

`core/ai/prompt/{ExpandPrompt,PolishPrompt,OrganizePrompt}.kt` 3 个分散文件 MUST NOT 存在(本 change 合并到 `DefaultPrompts`);M3 写死的内容 MUST 保留原意(只是搬家,不优化 / 翻译 / 修改)。

#### Scenario: 模板为空走 fallback
- **WHEN** `AiActionViewModel.start(EXPAND, sourceText, noteId)` 调用,`PromptTemplateStore.getForOp(EXPAND) == null`
- **THEN** `systemPrompt = DefaultPrompts.forOp(EXPAND)`,返回 M3 写死的扩写 system prompt 原文

#### Scenario: 模板非空走用户配置
- **WHEN** `PromptTemplateStore.getForOp(POLISH) == "你是一位正式文风的润色助手..."`
- **THEN** `systemPrompt = "你是一位正式文风的润色助手..."`,不走 fallback

#### Scenario: M3 写死 prompt 原文保留
- **WHEN** `ls core/ai/prompt/`
- **THEN** 只剩 `DefaultPrompts.kt`(`ExpandPrompt.kt` / `PolishPrompt.kt` / `OrganizePrompt.kt` 已删)

#### Scenario: DefaultPrompts 单点访问
- **WHEN** grep `core/ai/prompt/DefaultPrompts.kt` "forOp"
- **THEN** 至少 1 个 `fun forOp(op: WritingOp): String` 定义,3 个 `when` 分支覆盖 EXPAND / POLISH / ORGANIZE

### Requirement: PromptTemplateScreen provides 3-tab edit UI

`PromptTemplateScreen` MUST 是 `@Composable`,接收 `viewModel: PromptTemplateViewModel`(Hilt 注入),UI 包含:
- `TabRow` 3 Tab:扩写 / 润色 / 整理(文案走 `R.string.prompt_op_expand` / `polish` / `organize`),每个 Tab 标题右侧 Box 红点 indicator(`pendingSave.contains(op)` 时显示)
- 当前 Tab 渲染 1 个 `OutlinedTextField`(multiline,`maxLines = 10`),value = `viewModel.uiState.value.drafts[op]`,`onValueChange = viewModel::onPromptChange(op, newValue)`(只改 drafts + 标 pendingSave,**不**立即写 store)
- 底部 `Row { Button("保存", enabled = pendingSave.contains(currentOp), onClick = { vm.save(currentOp) }) + OutlinedButton("恢复默认", onClick = vm::resetToDefault(currentOp)) }`
- 顶部说明文字 `R.string.prompt_hint_v2`:"自定义 AI 操作的 system prompt;修改后点保存生效;清空走默认"

`UiState` MUST 新增 `pendingSave: Set<WritingOp> = emptySet()`。

`onPromptChange(op, value)` MUST 只 `_uiState.update { it.copy(drafts = ..., pendingSave = it.pendingSave + op) }`,**不**调 `setForOp`。

`save(op)` MUST 调 `promptTemplateStore.setForOp(op, drafts[op]!!)` + `_uiState.update { it.copy(pendingSave = it.pendingSave - op) }`。

`resetToDefault(op)` MUST 调 `promptTemplateStore.resetToDefault(op)`(store 写空字符串)+ `_uiState.update { it.copy(drafts = it.drafts + (op to ""), pendingSave = it.pendingSave - op) }`(drafts 清空,下次 init 重新填默认)。

#### Scenario: 打开页面默认填入 DefaultPrompts 文本
- **WHEN** 用户首次进 PromptTemplateScreen,store 3 key 全 null
- **THEN** `drafts[EXPAND] = DefaultPrompts.forOp(EXPAND)`(M3 原文);`drafts[POLISH] = DefaultPrompts.forOp(POLISH)`;`drafts[ORGANIZE] = DefaultPrompts.forOp(ORGANIZE)`;UI 3 个 Tab 文本域均显示默认 prompt 内容;`pendingSave = emptySet`(默认内容不算 dirty)

#### Scenario: 切换 Tab 渲染对应 prompt
- **WHEN** 用户点"润色" Tab
- **THEN** UI 渲染"润色" prompt 草稿;`viewModel.uiState.value.drafts[POLISH]` 同步显示

#### Scenario: 编辑后 Tab 红点出现 + Save 按钮可点
- **WHEN** 用户在 EXPAND tab 改 1 个字符
- **THEN** `_state.pendingSave = {EXPAND}`;EXPAND tab 标题右侧 Box 红点 indicator 渲染;`Save` Button enabled = true;POLISH / ORGANIZE tab 无红点

#### Scenario: 切 Tab 不保存 → 切回红点仍在
- **WHEN** EXPAND tab dirty → 切 POLISH → 切回 EXPAND
- **THEN** EXPAND tab 红点仍在(drafts 保留);POLISH tab 无红点(未改);Save 按钮 enabled

#### Scenario: 点 Save 写 store + 清 dirty
- **WHEN** 用户在 EXPAND tab 改 5 字符 → 点 Save
- **THEN** `promptTemplateStore.setForOp(EXPAND, "用户输入 5 字符")` 被调;`getForOp(EXPAND) == "用户输入 5 字符"`;`_state.pendingSave = emptySet`;EXPAND tab 红点消失;下次 `AiActionViewModel.start(POLISH, ...)` 用自定义 systemPrompt

#### Scenario: 编辑 prompt 自动写 store 旧行为已废除
- **WHEN** 用户在 EXPAND tab 改 1 个字符(不点 Save)
- **THEN** `promptTemplateStore.setForOp(EXPAND, ...)` **不**被调(`onPromptChange` 仅改 drafts);`getForOp(EXPAND)` 返回原值(或 null);AI 下次调用仍走 fallback 默认

#### Scenario: 点 恢复默认 → store 清空 + drafts 清空 + dirty 清
- **WHEN** 用户在 ORGANIZE tab 点 "恢复默认"
- **THEN** `promptTemplateStore.resetToDefault(ORGANIZE)` 调用 → store 写空字符串 → `getForOp(ORGANIZE) == null`;`_state.drafts[ORGANIZE] = ""`;`_state.pendingSave` 不含 ORGANIZE;ORGANIZE tab 文本域清空(下次 init 重填默认)

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
