# ai-model-selection-persistence Specification (Delta)

## Purpose

为每 provider 持久化用户选中的 model，确保 AI 调用时实际用的 model 跟用户选的一致。

## ADDED Requirements

### Requirement: Per-provider selected model is persisted with write-failure feedback

`ProviderPrefsStore` MUST 暴露 `suspend fun setSelectedModel(providerId: String, model: String)` 与 `suspend fun getSelectedModel(providerId: String): String?`，以及新增 `suspend fun setSelectedModelIfMissing(providerId: String, defaultModel: String)`，后者 MUST 在 `selected_model_<providerId>` key 不存在时落 `defaultModel`，已存在时**不**覆盖。

`ModelManagementViewModel.onModelSelected(providerId, model)` MUST 改为 `suspend fun`，内部 await `setSelectedModel(...)`;**写盘失败 MUST throw**，由调用方(`viewModelScope.launch`)捕 `CancellationException` 重新抛、捕其它 `Exception` 走 `_saveEvents` SharedFlow emit `SaveResult.Failed(operationKind=MODEL_SELECT, messageRes=..., rawDetail=e.message)`。

`SaveResult.Failed` MUST 扩展 `operationKind: OperationKind = OperationKind.SAVE` 字段，UI 据此区分「apikey 保存失败」与「model 切换失败」文案。

#### Scenario: 下拉选 model 成功
- **WHEN** `ModelManagementViewModel.onModelSelected(providerId="deepseek", model="deepseek-v4-pro")` 调，且 DataStore 写盘成功
- **THEN** `providerPrefsStore.getSelectedModel("deepseek")` 返回 `"deepseek-v4-pro"`，且**不** emit `_saveEvents`(纯本地切换，无 save 事件;UI 静默)

#### Scenario: 下拉选 model 写盘失败
- **WHEN** `ModelManagementViewModel.onModelSelected(providerId="deepseek", model="deepseek-v4-pro")` 调，`setSelectedModel` 抛 `IOException("disk full")`
- **THEN** `_saveEvents` emit `SaveResult.Failed(operationKind=MODEL_SELECT, messageRes=R.string.model_management_dropdown_save_failed, rawDetail="disk full")`,UI(`ModelProviderDetailScreen`)收事件后弹 Snackbar 显示「模型切换失败，请重试」

#### Scenario: setSelectedModelIfMissing 写 defaultModel
- **WHEN** `setSelectedModelIfMissing(providerId="deepseek", defaultModel="deepseek-v4-flash")` 调，且 `selected_model_deepseek` key 之前不存在
- **THEN** DataStore 写入 `selected_model_deepseek = "deepseek-v4-flash"`,`getSelectedModel("deepseek")` 返回 `"deepseek-v4-flash"`

#### Scenario: setSelectedModelIfMissing 不覆盖
- **WHEN** `setSelectedModelIfMissing(providerId="deepseek", defaultModel="deepseek-v4-flash")` 调，且 `selected_model_deepseek` 之前已存在为 `"deepseek-v4-pro"`
- **THEN** DataStore **不**改写，`getSelectedModel("deepseek")` 仍返回 `"deepseek-v4-pro"`

### Requirement: 首次成功保存 apikey 同步自举 selectedModel

`ModelManagementViewModel.saveProvider` 成功落 apikey + 落 `selectedProviderId` 之后，**同步**调 `providerPrefsStore.setSelectedModelIfMissing(providerId, getProviderConfig(providerId).defaultModel)`。语义:apikey 已落 → 必须有 selectedModel 是 save 流程的不变式。

`ModelManagementViewModel.init` 块启动时 MUST 扫一遍:凡是有 apikey(`secureApiKeyStore.has(providerId) == true`)且 `getSelectedModel(providerId) == null` 的 provider，补写 `providerPrefsStore.setSelectedModelIfMissing(providerId, getProviderConfig(providerId)?.defaultModel ?: return@forEach)`。处理存量用户(改 change 前已设 apikey 但没选过 model)。

#### Scenario: 首次保存 apikey 触发自举
- **WHEN** `ModelManagementViewModel.saveProvider(providerId="deepseek", apiKey="sk-test", model=null)` 调，且之前 `getSelectedModel("deepseek") == null`
- **THEN** apikey 落盘 + selectedProviderId 落盘 + `setSelectedModelIfMissing("deepseek", "deepseek-v4-flash")` 同步落盘;`getSelectedModel("deepseek")` 返回 `"deepseek-v4-flash"`

#### Scenario: 启动 lazy init 补齐存量
- **WHEN** `ModelManagementViewModel.init` 块执行，且 `secureApiKeyStore.has("deepseek") == true` 但 `getSelectedModel("deepseek") == null`
- **THEN** `setSelectedModelIfMissing("deepseek", DeepseekConfig.config.defaultModel)` 落盘;`getSelectedModel("deepseek")` 返回 `"deepseek-v4-flash"`

#### Scenario: 用户已显式选过 model，自举不覆盖
- **WHEN** `saveProvider(providerId="deepseek", apiKey="sk-test", model="deepseek-v4-pro")` 调，且 `getSelectedModel("deepseek") == "deepseek-v4-pro"`
- **THEN** `setSelectedModelIfMissing` 跳过，`getSelectedModel("deepseek")` 仍为 `"deepseek-v4-pro"`

### Requirement: OperationKind 区分 save 与 model select 事件

`SaveResult` sealed interface MUST 新增 `OperationKind` 枚举，值含 `SAVE` 与 `MODEL_SELECT`。`SaveResult.Failed` MUST 含 `operationKind: OperationKind` 字段，默认 `OperationKind.SAVE`(向后兼容老调用方)。

`ModelProviderDetailScreen` 收 `_saveEvents.collect` 事件流时，MUST 按 `operationKind` 选文案:
- `OperationKind.SAVE` → 现有「保存失败」Snackbar
- `OperationKind.MODEL_SELECT` → 新「模型切换失败，请重试」Snackbar

#### Scenario: SAVE 失败用 SAVE 文案
- **WHEN** `_saveEvents` emit `SaveResult.Failed(operationKind=SAVE, messageRes=R.string.model_management_error_unknown, rawDetail="...")`
- **THEN** `ModelProviderDetailScreen` 弹「保存失败」Snackbar

#### Scenario: MODEL_SELECT 失败用 MODEL_SELECT 文案
- **WHEN** `_saveEvents` emit `SaveResult.Failed(operationKind=MODEL_SELECT, messageRes=R.string.model_management_dropdown_save_failed, rawDetail="disk full")`
- **THEN** `ModelProviderDetailScreen` 弹「模型切换失败，请重试」Snackbar
