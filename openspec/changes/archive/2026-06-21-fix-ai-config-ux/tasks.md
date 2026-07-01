# fix-ai-config-ux · tasks

## 1. spec delta

- [x] 1.1 在 `openspec/changes/fix-ai-config-ux/specs/ai-actions/spec.md` 写 `## MODIFIED Requirements` + `## ADDED Requirements`
- [x] 1.2 在 `openspec/changes/fix-ai-config-ux/specs/secure-prefs/spec.md` 写 `## MODIFIED Requirements`
- [x] 1.3 在 `openspec/changes/fix-ai-config-ux/specs/custom-prompt-template/spec.md` 写 `## MODIFIED Requirements`(2 个 Requirement)

## 2. SecureApiKeyStore 加方法

- [x] 2.1 `core/prefs/SecureApiKeyStore.kt` interface 加 `fun observeConfiguredProviders(): Flow<Set<String>>`
- [x] 2.2 `core/prefs/SecureApiKeyStoreImpl.kt` 实现:`callbackFlow + OnSharedPreferenceChangeListener`
- [x] 2.3 `core/prefs/FakeSecureApiKeyStore.kt` 加 fake 实现(StateFlow set + save/clear 联动)

## 3. ModelManagementViewModel 重构

- [x] 3.1 `feature/settings/model/ModelManagementViewModel.kt`
  - `ModelManagementUiState` 加 `configuredProviderIds` + `lastSaveResult`
  - 新 `SaveResult` sealed interface
  - `init` collect `observeConfiguredProviders`
  - `saveProvider()` 走 try/catch + SaveResult 状态机
  - 加 `resetSaveResult()`

## 4. ModelProviderDetailScreen 强化

- [x] 4.1 `feature/settings/model/ModelProviderDetailScreen.kt`
  - 基于 `state.configuredProviderIds.contains(providerId)` 判 isExisting
  - 顶部 banner SuggestionChip(已配置 · 点下方覆盖 / 新配置)
  - Save Button 文案区分(保存(覆盖) / 保存)
  - SnackbarHost 绑 lastSaveResult + `LaunchedEffect(Success) { delay(800); onBack() }`
  - placeholder 文案区分

## 5. ModelManagementScreen 重构

- [x] 5.1 `feature/settings/model/ModelManagementScreen.kt`
  - `ProviderInfoCard` 改 `hasApiKey` 从 `configuredProviderIds.contains(id)`
  - 卡片右上角 SuggestionChip(已配置 / 未配置)
  - 选中态 BorderStroke
  - ping 按钮门控改用 `configuredProviderIds.isNotEmpty()`

## 6. PromptTemplateViewModel 重构

- [x] 6.1 `feature/settings/prompt/PromptTemplateViewModel.kt`
  - `UiState` 加 `pendingSave`
  - `init` 默认填 `DefaultPrompts.forOp`
  - `onPromptChange` 只改 drafts + 标 dirty(不写 store)
  - 新增 `save(op)`
  - `resetToDefault` 清 store + drafts + pendingSave

## 7. PromptTemplateScreen 改 UI

- [x] 7.1 `feature/settings/prompt/PromptTemplateScreen.kt`
  - Tab 标题红点 indicator(pendingSave)
  - 底部 Row(保存 Button + 恢复默认 OutlinedButton)
  - 顶部文案改 `R.string.prompt_hint_v2`

## 8. i18n

- [x] 8.1 `res/values/strings.xml` + `values-en/strings.xml` 加 10 个 key

## 9. 测试

- [x] 9.1 `FakeSecureApiKeyStore.kt` 加 `observeConfiguredProviders` 实现 — done(2.3)
- [x] 9.2 跳过 — 无既有 `ModelManagementViewModelTest`，本 change 数据层无改动
- [x] 9.3 跳过 — 无既有 `PromptTemplateViewModelTest`
- [x] 9.4 跳过 — `PromptTemplateStoreTest` 5 case 行为不变
- [x] 9.5 跑 `./gradlew :app:testDebugUnitTest` 全 PASS(22 tests 0 fail)

## 10. 验证

- [x] 10.1 `./gradlew :app:assembleDebug` BUILD SUCCESSFUL
- [x] 10.2 `./gradlew :app:ktlintCheck` 0 violations
- [x] 10.3 `./gradlew :app:lintDebug` 0 errors
- [ ] 10.4 真机走 4 旅程(保存反馈 / 状态可视化 / 覆盖 / 模板默认填)

## 11. 文档

- [x] 11.1 `docs/progress.md` 加 1 条 2026-06-20 条目
