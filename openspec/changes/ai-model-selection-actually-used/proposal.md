## Why

用户在「设置 → AI 模型管理」选了 deepseek pro 模型,真机 deepseek 后台却显示调用的是 flash。根因有 3 层:

1. `CoreAiGateway.streamWritingOp` 的 modelName 兜底是 `provider.supportedModels.firstOrNull()` —— 跟 `defaultModel` 无关,跟列表顺序强绑定;deepseek 的 `supportedModels = ["deepseek-v4-flash", "deepseek-v4-pro"]` 命中 flash。
2. `ModelManagementViewModel.onModelSelected` 用 `viewModelScope.launch { setSelectedModel(...) }` fire-and-forget,异常被 `catch (_: Exception) { Log.e(...) }` 静默吞。下拉选完直接返回 → ViewModel 销毁 → scope 取消 → DataStore 没落盘。`currentModel` 本地 state 已显示 pro,所以 UI 撒谎。
3. 用户从来没为这个 provider 选过 model → `getSelectedModel(providerId) == null` → 命中 1 的兜底。

`saveProvider` 路径虽然把 model 同步落盘了,但 `onModelSelected`(下拉纯切换)路径根本没接这个事务。用户从详情页下拉切 model → 写盘不可靠,模型管理卡片还能正确显示「已选 pro」(因为卡片订阅 `observeSelectedModel`),但 AI 调用时读到的还是 null → gateway 兜底 flash。

## What Changes

- **gateway 兜底改语义**:`CoreAiGateway.streamWritingOp` 当 `modelName == null` 时,fallback 走 `provider.defaultModel`,**不**用 `provider.supportedModels.firstOrNull()`。理由:`defaultModel` 是 provider 显式声明的「无用户偏好时用这个」,list[0] 是历史遗留的「开发便利」副作用。
- **持久化首次自举**:首次成功保存 apikey(任意 provider,内置 + 自定义)时,如果 `getSelectedModel(providerId) == null`,自动把 `providerPrefsStore.setSelectedModel(providerId, provider.defaultModel)` 同步落盘。消灭「从来没选过」这个 null 状态 — 真正没选过就是 default,而不是 list[0] 误中。
- **下拉写盘事务化 + UI 反馈**:`ModelManagementViewModel.onModelSelected` 改成 `suspend fun onModelSelected(...): SaveResult`,内部 await 写盘;**失败时返回 `SaveResult.Failed`,发 `_saveEvents` 事件流**;`ModelProviderDetailScreen` 收事件后用 Snackbar/Toast 提示用户写失败。重命名旧 fire-and-forget 调用点避免回归。
- **UI 显式展示实际将用 model**:模型管理卡片在 `selectedModel` 已是真值时,继续显示「已选 X」(当前行为);新增一段小字「实际调用: `<model>`」,值从「`selectedModel ?: defaultModel`」算出来,任何时候用户看到的「实际将调」= 实际被调用的 model。无歧义。
- **`AiActionViewModel` 调用前断言**:`getSelectedModel(providerId)` 返回 null 时,fallback 用 `provider.defaultModel`(同 gateway),并在流开始前发 `state = Streaming(op)` 时附上实际 model(透传到 UI 让 ai-history 落表)。双保险:即便首次自举漏了某条路径,gateway 也不会再误中 list[0]。
- **3 个新单测 + 1 个改单测**:
  - `CoreAiGatewayTest`:null modelName + 3 个 provider 各跑一次,断言 fallback = `defaultModel` 而非 list[0]。
  - `ModelManagementViewModelTest.onModelSelected_writeFailure_emitsSaveFailedEvent`:写盘模拟抛 IOException,断言 `_saveEvents` emit `Failed`。
  - `ModelManagementViewModelTest.saveProvider_firstTime_autoInitsSelectedModelToDefault`:apikey 首次保存 + 之前没选 model,断言 `getSelectedModel == defaultModel`。
  - 改 `AnthropicCompatibleAdapter` / `CoreAiGateway` 既有测试:modelName=null 的用例 fallback 期望值从 list[0] 改成 `defaultModel`。
- **不**改 `AiGateway` 接口签名(纯实现层)。
- **不**改 `providerPrefsDataStore` schema(per-provider selected_model_<id> key 继续用 stringPreferencesKey;**首次自举** 走现有 `setSelectedModel` 落,不需要 schema 迁移)。
- **不**改 provider config 文件(3 家 defaultModel 仍按真机验证过的值)。

## Capabilities

### New Capabilities

- `ai-model-selection-persistence`:每 provider 持久化用户选的 model + 首次配置自动用 defaultModel 自举 + 下拉写盘事务化 + 失败事件反馈。
- `ai-model-selection-ui-transparency`:模型管理卡片 + AI 操作前断言显示「实际将调用 model」,消除「UI 显示 X 但实际调 Y」的歧义。

### Modified Capabilities

- `ai-gateway`:改写 1 个 Requirement `AiGateway provides a single entry point for all AI calls` 的 fallback 语义 — `modelName == null` 时 fallback 走 `provider.defaultModel`,**不**走 `provider.supportedModels.firstOrNull()`。

## Impact

- 改 1 个文件:`core/ai/CoreAiGateway.kt`(1 行 fallback + 增 1 个 fallback 注释)
- 改 1 个文件:`feature/settings/model/ModelManagementViewModel.kt`(`onModelSelected` 转 suspend,新增 `setSelectedModelIfMissing(providerId, defaultModel)` helper,`saveProvider` 调一次)
- 改 1 个文件:`feature/settings/model/ModelProviderDetailScreen.kt`(`onModelSelected` 调用方改为 collect SaveResult 事件,UI Snackbar)
- 改 1 个文件:`feature/settings/model/ModelManagementScreen.kt`(ProviderInfoCard 加「实际调用」小字;依赖 `descriptor.defaultModel` 新字段)
- 改 1 个文件:`core/ai/api/ProviderDescriptor.kt`(加 `defaultModel: String` 字段,3 个 provider 静态补值,自定义从 `ProviderConfig.defaultModel` 映射)
- 改 1 个文件:`feature/aiwriting/streaming/AiActionViewModel.kt`(`getSelectedModel == null` 时 fallback 用 `provider.defaultModel`;`state = Streaming` 携带 actualModel)
- 新增 3 个 / 改 1 个单测(共 4 个文件):`core/ai/CoreAiGatewayTest.kt` / `feature/settings/model/ModelManagementViewModelTest.kt`(如无则新建)
- 改 1 个文件:`res/values/strings.xml` + `res/values-en/strings.xml`(1 个 key:`model_management_actual_call_fmt`「实际调用: %1$s」+ `model_management_dropdown_save_failed`「模型切换失败,请重试」+ `model_management_actual_call_using`「实际将使用: %1$s」,3 个 key × 2 lang = 6 条)
- 改 1 份文档:`docs/usage/open-spec.md` 协议无关,但更新「首次配置即生效」一段;不动 `docs/usage/api-*.md`(协议无变化)
- 不引入新依赖(纯 Kotlin + DataStore + Flow 事件)
- 验证:真机跑「deepseek pro」→ deepseek 后台确认 model 字段;再跑「mimo pro」反验;再跑「删 apikey 重装」确认自举路径
