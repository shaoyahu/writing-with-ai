# ai-model-selection-ui-transparency Specification (Delta)

## Purpose

消除「UI 显示已选 X model,实际调用 Y model」的歧义。所有用户能看到的「实际将调用的 model」必须 = 实际被调用的 model。

## ADDED Requirements

### Requirement: ProviderDescriptor 携带 defaultModel

`ProviderDescriptor` MUST 新增 `defaultModel: String` 字段。`ModelManagementViewModel.providerDescriptors()` 构造 descriptor 时 MUST 把对应 `ProviderConfig.defaultModel` 映射进去(builtin 走 `DeepseekConfig.config.defaultModel` 等,自定义走 `customProviderStore.getById(id).defaultModel`)。

#### Scenario: 内置 provider descriptor 含 defaultModel
- **WHEN** `ModelManagementViewModel.providerDescriptors()` 调,且 `aiGateway.listProviders()` 返回 `ProviderDescriptor(id="deepseek", displayName="DeepSeek", models=["deepseek-v4-flash", "deepseek-v4-pro"])`
- **THEN** 返回的 descriptor 列表中 `deepseek` 那个 descriptor 的 `defaultModel == "deepseek-v4-flash"`

#### Scenario: 自定义 provider descriptor 含 defaultModel
- **WHEN** `customProviderStore.getAll()` 返回 `ProviderConfig(id="my-cust", defaultModel="claude-3-5-sonnet", supportedModels=[...])`
- **THEN** `providerDescriptors()` 返回的列表中 `my-cust` 那个 descriptor 的 `defaultModel == "claude-3-5-sonnet"`

### Requirement: 模型管理卡片显示「实际调用 model」

`ModelManagementScreen` 的 `ProviderInfoCard` MUST 在「已选 X / X 个模型」小字下方新增一行「实际调用: Y」,Y MUST 由 `resolveActualModel(providerId, selectedModel, descriptor.defaultModel)` 计算:`selectedModel?.takeIf { it.isNotBlank() } ?: defaultModel`。

#### Scenario: 已选 model 时卡片显示
- **WHEN** `ProviderInfoCard` 渲染 deepseek 卡片,`selectedModel == "deepseek-v4-pro"`,`defaultModel == "deepseek-v4-flash"`
- **THEN** 卡片显示「已选 deepseek-v4-pro」(现有文案)+「实际调用: deepseek-v4-pro」(新文案)

#### Scenario: 未选 model 时卡片显示
- **WHEN** `ProviderInfoCard` 渲染 deepseek 卡片,`selectedModel == null`,`defaultModel == "deepseek-v4-flash"`
- **THEN** 卡片显示「2 个模型」(现有文案)+「实际调用: deepseek-v4-flash」(新文案)

### Requirement: AiActionViewModel 透传 actualModel 给 UI

`AiActionViewModel.Streaming(op)` 状态 MUST 新增 `actualModel: String` 字段,值 MUST 由 `resolveActualModel(providerId, selectedModel, getProviderConfig(providerId).defaultModel)` 计算。

`AiActionViewModel` 调 `aiGateway.streamWritingOp(...)` 时 MUST 用 `actualModel`(而非 `selectedModel`)作为 `modelName` 参数透传,**不**依赖 gateway fallback;这样 AI 调用时实际用的 model = `state.actualModel`,无歧义。

#### Scenario: Streaming 状态带 actualModel
- **WHEN** `AiActionViewModel.start(op=EXPAND, sourceText="hi")` 调,`getSelectedModel("deepseek") == "deepseek-v4-pro"`,`defaultModel == "deepseek-v4-flash"`
- **THEN** 首个 `_state.value` 为 `AiActionUiState.Streaming(op=EXPAND, actualModel="deepseek-v4-pro")`

#### Scenario: 未选 model 用 defaultModel
- **WHEN** `AiActionViewModel.start(op=EXPAND)` 调,`getSelectedModel("deepseek") == null`,`defaultModel == "deepseek-v4-flash"`
- **THEN** `_state.value` 为 `AiActionUiState.Streaming(op=EXPAND, actualModel="deepseek-v4-flash")`,且 `aiGateway.streamWritingOp(..., modelName="deepseek-v4-flash", ...)` 被调

### Requirement: resolveActualModel 工具函数

`core/ai/api/` MUST 新增 `internal fun resolveActualModel(selectedModel: String?, defaultModel: String): String = selectedModel?.takeIf { it.isNotBlank() } ?: defaultModel`。

#### Scenario: 空字符串 fallback
- **WHEN** `resolveActualModel(selectedModel="", defaultModel="deepseek-v4-flash")` 调
- **THEN** 返回 `"deepseek-v4-flash"`

#### Scenario: 空白字符串 fallback
- **WHEN** `resolveActualModel(selectedModel="   ", defaultModel="deepseek-v4-flash")` 调
- **THEN** 返回 `"deepseek-v4-flash"`

#### Scenario: 有效 selectedModel
- **WHEN** `resolveActualModel(selectedModel="deepseek-v4-pro", defaultModel="deepseek-v4-flash")` 调
- **THEN** 返回 `"deepseek-v4-pro"`
