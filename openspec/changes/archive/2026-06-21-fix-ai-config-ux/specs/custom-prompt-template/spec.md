# custom-prompt-template Specification (delta)

## MODIFIED Requirements (fix-ai-config-ux)

### Requirement: PromptTemplateScreen provides 3-tab edit UI (delta · 大改)

继承原 Requirement 拓扑;**修改编辑 UI 行为**:`onPromptChange` 改为**只改 drafts + 标 pendingSave**,**不立即写 store**;**新增显式 "保存" 按钮**(`Button(enabled = pendingSave.contains(currentOp)) { vm.save(currentOp) }`)+ 标 dirty 红点 indicator(每个 Tab 标题右侧，`pendingSave.contains(op)` 时显示)。

`UiState` MUST 新增 `pendingSave: Set<WritingOp> = emptySet()`。

`onPromptChange(op, value)` MUST 只 `_uiState.update { it.copy(drafts = ..., pendingSave = it.pendingSave + op) }`,**不**调 `setForOp`。

新增 `save(op)` 方法 MUST 调 `promptTemplateStore.setForOp(op, drafts[op]!!)` + `_uiState.update { it.copy(pendingSave = it.pendingSave - op) }`。

`resetToDefault(op)` MUST 调 `promptTemplateStore.resetToDefault(op)`(store 写空字符串)+ `_uiState.update { it.copy(drafts = it.drafts + (op to ""), pendingSave = it.pendingSave - op) }`(drafts 清空，下次 init 重新填默认)。

底部 MUST 替换原单 OutlinedButton 为 `Row { Button("保存") + OutlinedButton("恢复默认") }`。

顶部文案 MUST 改 `R.string.prompt_hint_v2`:"自定义 AI 操作的 system prompt;修改后点保存生效;清空走默认"。

#### Scenario: 打开页面默认填入 DefaultPrompts 文本

- **WHEN** 用户首次进 PromptTemplateScreen,store 3 key 全 null
- **THEN** `drafts[EXPAND] = DefaultPrompts.forOp(EXPAND)`(M3 原文);`drafts[POLISH] = DefaultPrompts.forOp(POLISH)`;`drafts[ORGANIZE] = DefaultPrompts.forOp(ORGANIZE)`;UI 3 个 Tab 文本域均显示默认 prompt 内容;`pendingSave = emptySet`(默认内容不算 dirty)

#### Scenario: 编辑后 Tab 红点出现 + Save 按钮可点

- **WHEN** 用户在 EXPAND tab 改 1 个字符
- **THEN** `_state.pendingSave = {EXPAND}`;EXPAND tab 标题右侧 Box 红点 indicator 渲染;`Save` Button enabled = true;POLISH / ORGANIZE tab 无红点

#### Scenario: 切 Tab 不保存 → 切回红点仍在

- **WHEN** EXPAND tab dirty → 切 POLISH → 切回 EXPAND
- **THEN** EXPAND tab 红点仍在(drafts 保留);POLISH tab 无红点(未改);Save 按钮 enabled

#### Scenario: 点 Save 写 store + 清 dirty

- **WHEN** 用户在 EXPAND tab 改 5 字符 → 点 Save
- **THEN** `promptTemplateStore.setForOp(EXPAND, "用户输入 5 字符")` 被调;`getForOp(EXPAND) == "用户输入 5 字符"`;`_state.pendingSave = emptySet`;EXPAND tab 红点消失;下次 `AiActionViewModel.start(POLISH, ...)` 用自定义 systemPrompt

#### Scenario: 点 恢复默认 → store 清空 + drafts 清空 + dirty 清

- **WHEN** 用户在 ORGANIZE tab 点 "恢复默认"
- **THEN** `promptTemplateStore.resetToDefault(ORGANIZE)` 调用 → store 写空字符串 → `getForOp(ORGANIZE) == null`;`_state.drafts[ORGANIZE] = ""`;`_state.pendingSave` 不含 ORGANIZE;ORGANIZE tab 文本域清空(下次 init 重填默认)

#### Scenario: 编辑 prompt 自动写 store 旧行为已废除

- **WHEN** 用户在 EXPAND tab 改 1 个字符(不点 Save)
- **THEN** `promptTemplateStore.setForOp(EXPAND, ...)` **不**被调(`onPromptChange` 仅改 drafts);`getForOp(EXPAND)` 返回原值(或 null);AI 下次调用仍走 fallback 默认

### Requirement: PromptTemplateStore persists templates via DataStore (delta)

继承原 Requirement 不变;**新增 Scenario**:

#### Scenario: getForOp 返回 null 时由 VM 层补默认(非 store 职责)

- **WHEN** DataStore `prompt_template_expand` key 为 null
- **THEN** `PromptTemplateStore.getForOp(EXPAND) == null`(store 行为不变，空 = fallback);`PromptTemplateViewModel.init` 检测 null → 调 `DefaultPrompts.forOp(EXPAND)` → `drafts[EXPAND] = <默认 prompt 文本>`(VM 行为，store 不感知)
