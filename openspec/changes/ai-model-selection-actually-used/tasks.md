## 1. Gateway fallback 改语义

- [ ] 1.1 改 `core/ai/CoreAiGateway.kt` line 122,`modelName ?: provider.supportedModels.firstOrNull()` → `modelName ?: provider.defaultModel`;若 `defaultModel` 为 null/blank,emit `AiStreamEvent.Failed(AiError.ProviderNotConfigured, recoverable=false)` 而非调 provider
- [ ] 1.2 `core/ai/api/AiError.kt` 确认 `ProviderNotConfigured` 错误码已存在，缺失则新增并附 `summary()` 文案

## 2. 持久化 + 自举

- [ ] 2.1 `core/prefs/ProviderPrefsStore.kt` 新增 `suspend fun setSelectedModelIfMissing(providerId, defaultModel)`(单 `DataStore.edit` 块，`if (selectedModelKey not present) put(it, default)`)
- [ ] 2.2 `feature/settings/model/ModelManagementViewModel.kt` 改 `onModelSelected` 为 `suspend fun`，内部 `try { setSelectedModel(...) } catch (e: CancellationException) { throw } catch (e: Exception) { _saveEvents.tryEmit(SaveResult.Failed(operationKind=MODEL_SELECT, ...)) }`;调用方 `viewModelScope.launch` 包装
- [ ] 2.3 `SaveResult` sealed interface 增 `OperationKind` enum(`SAVE` / `MODEL_SELECT`);`SaveResult.Failed` 加 `operationKind: OperationKind = SAVE` 字段
- [ ] 2.4 `ModelManagementViewModel.saveProvider` 成功落 apikey + selectedProviderId 后，同步调 `providerPrefsStore.setSelectedModelIfMissing(providerId, getProviderConfig(providerId).defaultModel)`
- [ ] 2.5 `ModelManagementViewModel.init` 块启动时扫所有 provider,`secureApiKeyStore.has(id) && getSelectedModel(id) == null` 触发 `setSelectedModelIfMissing(id, config.defaultModel)`,try/catch Log.e 不阻塞启动
- [ ] 2.6 `feature/settings/model/ModelProviderDetailScreen.kt` 收 `_saveEvents.collect` 事件，`OperationKind.MODEL_SELECT` 走「模型切换失败，请重试」Snackbar(独立 `LaunchedEffect` 不与现有 save Snackbar 冲突)

## 3. UI 透明度

- [ ] 3.1 `core/ai/api/ProviderDescriptor.kt` 加 `defaultModel: String` 字段;3 个 provider 静态补值(`deepseek-v4-flash` / `mimo-...` / `minimax-...`)
- [ ] 3.2 `ModelManagementViewModel.providerDescriptors()` 把 `ProviderConfig.defaultModel` 映射到 `descriptor.defaultModel`(`ModelManagementViewModel.kt`)
- [ ] 3.3 新增 `core/ai/api/ModelSelection.kt` `internal fun resolveActualModel(selectedModel: String?, defaultModel: String): String = selectedModel?.takeIf { it.isNotBlank() } ?: defaultModel`
- [ ] 3.4 `feature/settings/model/ModelManagementScreen.kt` 的 `ProviderInfoCard` 在「已选 X」/「X 个模型」下方加一行小字「实际调用: Y」，Y = `resolveActualModel(selectedModel, descriptor.defaultModel)`
- [ ] 3.5 `feature/aiwriting/streaming/AiActionViewModel.kt` `Streaming(op)` 状态加 `actualModel: String` 字段;`start()` 内 `actualModel = resolveActualModel(getSelectedModel(providerId), getProviderConfig(providerId).defaultModel)`，调 `aiGateway.streamWritingOp(..., modelName = actualModel, ...)`(不再依赖 gateway fallback)
- [ ] 3.6 `ai-history` 落表字段用 `actualModel` 替换 `modelName`(`AiActionViewModel` 内对应 insert 路径)

## 4. 字符串

- [ ] 4.1 `res/values/strings.xml` 加 3 个 key:`model_management_actual_call_fmt` 「实际调用: %1$s」、`model_management_dropdown_save_failed` 「模型切换失败，请重试」、`model_management_actual_call_using` 「实际将使用: %1$s」
- [ ] 4.2 `res/values-en/strings.xml` 英文对应 3 条

## 5. 测试

- [ ] 5.1 `core/ai/CoreAiGatewayTest.kt` 新增 1 个 case:`modelName null + provider with defaultModel="X"` → 断言 `provider.stream(AiRequest(model="X", ...))` 被调，非 `model=supportedModels[0]`
- [ ] 5.2 `core/ai/CoreAiGatewayTest.kt` 新增 1 个 case:`modelName null + provider.defaultModel == null` → 断言 Flow 首个事件为 `AiStreamEvent.Failed(AiError.ProviderNotConfigured, recoverable=false)`,`provider.stream` 0 次调用
- [ ] 5.3 `feature/settings/model/ModelManagementViewModelTest.kt` 新增 `onModelSelected_writeFailure_emitsSaveFailedEvent`:`setSelectedModel` 抛 `IOException("disk full")`，断言 `_saveEvents.first()` 为 `SaveResult.Failed(operationKind=MODEL_SELECT, ...)`
- [ ] 5.4 `feature/settings/model/ModelManagementViewModelTest.kt` 新增 `saveProvider_firstTime_autoInitsSelectedModelToDefault`:无 `getSelectedModel` 值时调 saveProvider，断言 `getSelectedModel(providerId) == defaultModel`
- [ ] 5.5 改既有 `AnthropicCompatibleAdapter` / `CoreAiGateway` 单测:modelName=null 的 fixture 把期望 model 从 list[0] 改成 `defaultModel`

## 6. 验证

- [ ] 6.1 `export JAVA_HOME=/opt/homebrew/opt/openjdk@17 && ./gradlew :app:assembleDebug` 编译通过
- [ ] 6.2 `./gradlew :app:ktlintCheck` 静态检查通过
- [ ] 6.3 `./gradlew :app:testDebugUnitTest` 单测全绿
- [ ] 6.4 真机验证(用户执行，本 change AI 不做):选 deepseek pro → 后台确认 model=deepseek-v4-pro;选 mimo pro → 反验;删 apikey 重装 → 启动 init 块补 defaultModel;下拉选完立即返回 → 卡片仍显示「已选 X」且重进详情页持久;Mock ProviderPrefsStore 抛 IOException → 详情页 Snackbar「模型切换失败」
