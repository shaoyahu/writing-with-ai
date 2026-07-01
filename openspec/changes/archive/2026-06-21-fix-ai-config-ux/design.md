# fix-ai-config-ux · design

## 真因再确认

### bug 4 · 保存无反馈

`ModelProviderDetailScreen.LaunchedEffect(state.selectedProviderId, state.hasApiKeyForSelected)` 的依赖 + 触发条件过于狭窄:

- 首次配置:`saveProvider()` → `selectedProviderId = providerId` + `hasApiKeyForSelected = true` → effect 重 trigger → toast + onBack OK
- 修改已有:`selectedProviderId` 已是 providerId(没变)+ `hasApiKeyForSelected` 一直是 true → effect **不重 trigger** → 用户保存了但**完全无反馈**

这是 Compose `LaunchedEffect` 经典坑:依赖列表未变 → 不重 launch → 修复需换事件流(`StateFlow<SaveResult>`，每次 save 重置 `Idle → Success`)。

### bug 5 · 配置状态不可见

`ModelManagementUiState.hasApiKeyForSelected: Boolean` 只跟踪 selected provider,UI `ProviderInfoCard.hasApiKey = selectedProviderId == X && hasApiKeyForSelected` → 未选中的 provider 永远 false → UI 永远不显示 CheckCircle。

修复:VM 暴露 `configuredProviderIds: Set<String>`,UI 用 `contains(id)` 而非 selected 比较。

### bug 6 · 已配置不可修改

代码层 detail 页 `onClick` 不条件，逻辑上可点。但入口信号缺失(bug 5)+ detail 页 `apiKey by remember { mutableStateOf("") }` 空白 → 用户进 detail 看到空白 input + Save 按钮 "保存" → 误判"没配过" → 不敢改或误覆盖空白。

修复:detail 页 `LaunchedEffect(providerId)` 调 `has(providerId)` → 区分 "新配置" vs "已配置 · 覆盖" banner + Save 按钮文案。

### bug 7 · 模板默认未填 + 无保存按钮

`PromptTemplateViewModel.init`:`val expand = promptTemplateStore.getForOp(EXPAND) ?: ""` → 空 → drafts[EXPAND] = "" → UI 显示空白 → 用户看不到默认内容。

`onPromptChange(op, value)` 已"立即写"(spec 自带 `编辑 prompt 自动保存` Scenario)，但用户无 pendingSave 视觉信号 → 不知道写没写。

修复:init 默认填 `DefaultPrompts.forOp(op)`(只 drafts 层，store 仍空 → AiActionViewModel 仍走 fallback)+ pendingSave indicator + 显式 "保存" 按钮(`onPromptChange` 改为只改 drafts + 标 dirty，显式 save() 写 store)。

## 设计决策

### 决策 1 · bug 4 反馈机制:Toast vs Snackbar vs Inline Message

候选:
- (a) Toast(现状)→ 用户易错过
- (b) **Snackbar(底部)+ 自动 onBack**→ Material 3 推荐，4s 自动消失 + 用户可手动 dismiss
- (c) Inline error message(屏内 Text)→ 占用屏高

选 (b)。理由:M3 设计指南首推 Snackbar;`LaunchedEffect(Success) { delay(800); onBack() }` 给视觉缓冲;`Failed` 不 onBack 让用户看到原因。

### 决策 2 · bug 5 状态可视化:SuggestionChip + 颜色

候选:
- (a) 仅 CheckCircle icon(现状部分实现)
- (b) **SuggestionChip("已配置" / "未配置") + CheckCircle icon**(统一 chip 样式)
- (c) Border 边框色区分

选 (b)。理由:文字 + 图标 双信号;颜色用 M3 token(primaryContainer / surfaceVariant);chip 位置卡片右上角 → 不抢主信息(name / baseUrl)。

### 决策 3 · bug 6 detail 页 banner

候选:
- (a) 无 banner(现状)→ 用户猜
- (b) **SuggestionChip("已配置 · 点下方覆盖")顶部 banner** + Save 按钮文案改 "保存(覆盖)"
- (c) 顶部一行 Text "此 provider 已配置 apikey"

选 (b)。理由:chip 与 bug 5 一致，统一视觉语言;按钮文案区分避免歧义。

### 决策 4 · bug 7 模板默认填入策略

候选:
- (a) **填入 drafts,store 仍空** → 用户编辑后 save 才写 store → 简单
- (b) 立即写 store + drafts 都填 → 改 store 语义
- (c) 不填入，显示 "查看默认" 按钮 → 路径长

选 (a)。理由:store 语义不变(空 = fallback),drafts 层"假装填" → 用户编辑后 save 才"真正改" → 默认基线永远可恢复(`resetToDefault` 清 store → drafts 也清 → 下次 init 重填默认);契合 spec 原 `空走 fallback` 设计。

### 决策 5 · bug 7 保存按钮:onChange 立即写 vs 显式 save

候选:
- (a) onChange 立即写(现状，spec 自带 Scenario)
- (b) **onChange 只改 drafts + pendingSave，显式 save 写 store**

选 (b)。理由:用户痛点是"修改了但不知道是否生效" → 显式 save + pendingSave 红点指示器 解决;spec 原 Scenario 改写;`AiActionViewModel` 调用 `getForOp(op)` 路径不变(只 drafts 改，store 仍要 save 后才生效)。

### 决策 6 · `observeConfiguredProviders()` 数据源

候选:
- (a) VM `init { for each id { has(id) } }` 一次性快照 → 不响应后续变化
- (b) **VM `observeConfiguredProviders(): Flow<Set<String>>` 暴露 → VM collect 实时反映**

选 (b)。理由:用户保存 apikey 后，UI 应**立即**更新该卡片状态 → Flow 驱动;底层 `EncryptedSharedPreferences` 用 `OnSharedPreferenceChangeListener` 监听 → 包装成 `callbackFlow`。

## 实现路径

1. `core/prefs/SecureApiKeyStore.kt` (interface)
   - 加 `fun observeConfiguredProviders(): Flow<Set<String>>`(在 impl 走 `callbackFlow + OnSharedPreferenceChangeListener` 监听所有 `apikey_*` key)
   - impl 实现:`collectLatest` 读初始 set → 注册 listener → emit 新 set → cancel 时 unregister

2. `feature/settings/model/ModelManagementViewModel.kt`
   - `ModelManagementUiState` 加 `configuredProviderIds: Set<String> = emptySet()` + `lastSaveResult: SaveResult = SaveResult.Idle`
   - `init` 加:`viewModelScope.launch { secureApiKeyStore.observeConfiguredProviders().collect { _state.update { it.copy(configuredProviderIds = ids) } } }`
   - `saveProvider()`:
     - `_state.update { it.copy(lastSaveResult = SaveResult.InProgress) }`
     - try `secureApiKeyStore.save(providerId, apiKey)` catch(e) → `Failed(e.message ?: "未知错误")` return
     - `providerPrefsStore.setSelectedProviderId(providerId)`
     - `_state.update { it.copy(selectedProviderId = providerId, hasApiKeyForSelected = true, lastSaveResult = SaveResult.Success, pingResult = PingResult.Idle) }`
   - 加 `resetSaveResult()` 方法

3. `feature/settings/model/ModelProviderDetailScreen.kt`
   - VM 加 `isExistingConfig: StateFlow<Boolean>`(`providerId` + `configuredProviderIds` combine)
   - 顶部 banner:`SuggestionChip(if (isExisting) "已配置 · 点下方覆盖" else "新配置", containerColor = secondaryContainer / surfaceVariant)`
   - Save Button 文案:`if (isExisting) "保存(覆盖)" else "保存"`
   - SnackbarHost 绑 `state.lastSaveResult`:`Success` → Snackbar + `LaunchedEffect(Success) { delay(800); onBack() }`;`Failed` → Snackbar 留屏

4. `feature/settings/model/ModelManagementScreen.kt`
   - `ProviderInfoCard` 签名改 `hasApiKey: Boolean`(从 `state.configuredProviderIds.contains(id)`)
   - 卡片右上角:`Row { if (hasApiKey) SuggestionChip("已配置", containerColor = primaryContainer, leadingIcon = Check) else SuggestionChip("未配置", containerColor = surfaceVariant) }`
   - 选中态卡片:`border = if (isSelected) BorderStroke(2.dp, primary) else null`

5. `feature/settings/prompt/PromptTemplateViewModel.kt`
   - `UiState` 加 `pendingSave: Set<WritingOp> = emptySet()`
   - `init` 改:`val expand = promptTemplateStore.getForOp(EXPAND) ?: DefaultPrompts.forOp(EXPAND)`(其他 op 同)
   - `onPromptChange(op, value)`:**不**调 setForOp，只改 drafts + 标 pendingSave
   - 新增 `save(op)`:`promptTemplateStore.setForOp(op, drafts[op]!!)` + `_uiState.update { it.copy(pendingSave = it.pendingSave - op) }`
   - `resetToDefault(op)`:`promptTemplateStore.resetToDefault(op)` + `drafts[op] = ""` + 清 pendingSave

6. `feature/settings/prompt/PromptTemplateScreen.kt`
   - Tab 标题:`Row { Text(...); if (pendingSave.contains(op)) Box(Modifier.size(6.dp).background(error)) }`
   - 底部:`Row { Button("保存", enabled = pendingSave.contains(currentOp)) { vm.save(currentOp) }; Spacer; OutlinedButton("恢复默认") { vm.resetToDefault(currentOp) } }`
   - 顶部文案改 `R.string.prompt_hint_v2`

7. `res/values/strings.xml` + `values-en/strings.xml`
   - 新增 ~10 个 key

## 风险与回退

- 风险 1:`saveProvider` 失败时显式反馈 → 用户首遇 Keystore 损坏场景可能频繁;缓解:失败 Snackbar 文案友好，不 log 技术细节
- 风险 2:`PromptTemplateViewModel.init` 默认填 → 老用户打开看到"默认内容" 困惑(以为被改了);缓解:文案明示"点保存后生效"
- 风险 3:`observeConfiguredProviders()` 监听 EncryptedSharedPreferences 在某些国产 ROM 上 listener 不稳定;缓解:VM init 兜底 `take(1).first()` 拿初值 + `collect` 后续，listener 失效时 user 保存走 `saveProvider` 主动 emit 触发 state 更新
- 风险 4:bug 7 改 spec 原"编辑 prompt 自动保存" Scenario → 既有测试需更新;缓解:旧 Scenario 改写为"onPromptChange 标 dirty,save() 写 store"

回退:`git revert <commit>` 即可，数据层 0 改动。
