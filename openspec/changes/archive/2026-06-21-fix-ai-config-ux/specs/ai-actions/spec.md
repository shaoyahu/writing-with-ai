# ai-actions Specification (delta)

## MODIFIED Requirements (fix-ai-config-ux)

### Requirement: ModelManagementViewModel passes real apikey to gateway.ping (delta)

继承原 Requirement 不变;**新增 Scenario**:

#### Scenario: saveProvider 失败时 UI 显式 Failed 反馈 + 不切 selected

- **WHEN** `secureApiKeyStore.save(providerId, apiKey)` 抛 `GeneralSecurityException`(模拟 keystore 损坏)
- **THEN** `lastSaveResult = Failed("KeyStore 损坏")`;`selectedProviderId` 与 `hasApiKeyForSelected` **不变**(不切 selected,不冒进);UI 显示 Snackbar `"保存失败:KeyStore 损坏"` + 不触发 onBack

## ADDED Requirements (fix-ai-config-ux)

### Requirement: ModelManagementViewModel exposes configuredProviderIds

`ModelManagementViewModel` MUST 暴露 `configuredProviderIds: StateFlow<Set<String>>`,数据源来自 `SecureApiKeyStore.observeConfiguredProviders()`(底层 `EncryptedSharedPreferences` 的 `OnSharedPreferenceChangeListener` 监听所有 `apikey_*` key)。

`ModelManagementUiState` MUST 新增字段 `configuredProviderIds: Set<String> = emptySet()`;`init { }` MUST collect 该 Flow → `_state.update { it.copy(configuredProviderIds = ids) }`。

`SaveResult` MUST 是 `sealed interface { data object Idle; data object InProgress; data object Success; data class Failed(val reason: String) }`;`ModelManagementUiState.lastSaveResult: SaveResult` 默认 `Idle`。

#### Scenario: save 成功后 configuredProviderIds 实时 emit

- **WHEN** `saveProvider("deepseek", "sk-xxx")` 调用成功
- **THEN** `observeConfiguredProviders()` 1 次新 emit 含 `"deepseek"`;VM `_state.configuredProviderIds` 同步更新为 `{"deepseek"}`;`ModelManagementScreen` deepseek 卡片右上角 SuggestionChip 由 "未配置"(灰)变 "已配置"(蓝)+ CheckCircle

#### Scenario: clear 后 configuredProviderIds 同步移除

- **WHEN** `secureApiKeyStore.clear("deepseek")` 调用
- **THEN** `observeConfiguredProviders()` 1 次新 emit 不含 `"deepseek"`;VM `_state.configuredProviderIds` 同步从 set 移除;deepseek 卡片回到 "未配置" 灰 chip

#### Scenario: 多个 provider 并存配置

- **WHEN** 同时配置 deepseek + minimax
- **THEN** `_state.configuredProviderIds = {"deepseek", "minimax"}`;两张卡片同时显示 "已配置" 蓝 chip(不互斥)

#### Scenario: 切换 selected 不影响其他 provider 已配置状态

- **WHEN** deepseek 已配 → 选 mimo 为 selected
- **THEN** `_state.configuredProviderIds` 仍含 `"deepseek"`(selected 切换是 prefs 行为,不删除 apikey);deepseek 卡片 "已配置" 蓝 chip 仍显示
