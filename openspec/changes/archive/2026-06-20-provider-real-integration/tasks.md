## 1. ProviderPrefsStore + DI

- [ ] 1.1 新建 `app/src/main/java/com/yy/writingwithai/core/ai/provider/ProviderPrefsStore.kt` —— interface + `ProviderPrefsStoreImpl` 走 DataStore Preferences,key = `selected_provider_id`,默认 `"fake"`
- [ ] 1.2 `core/prefs/PrefsModule.kt`(已有)加 `provideProviderPrefsStore` Hilt provider
- [ ] 1.3 新建 `app/src/test/java/com/yy/writingwithai/core/ai/provider/FakeProviderPrefsStore.kt` + `ProviderPrefsStoreTest.kt`(round-trip 2 个 test)

## 2. AiError 加 ProviderNotConfigured

- [ ] 2.1 `app/src/main/java/com/yy/writingwithai/core/ai/api/AiError.kt` 加 `data object ProviderNotConfigured : AiError`

## 3. AiActionViewModel 接真实 providerId

- [ ] 3.1 `feature/aiwriting/streaming/AiActionViewModel.kt` 构造加 `providerPrefsStore: ProviderPrefsStore` 形参
- [ ] 3.2 `streamWritingOp(...)` 用 `providerPrefsStore.getSelectedProviderId()` 替换 `"fake"`
- [ ] 3.3 `start(...)` 前置 check `secureApiKeyStore.hasApiKey(providerId) == false` → emit `AiError.ProviderNotConfigured`(走现有 Failed UiState 通道)
- [ ] 3.4 `AiActionViewModelConsentTest` + `AiActionViewModelTest` 扩 providerId 切换 case(6 个 VM 构造调用加 `providerPrefsStore` 参数)

## 4. ModelManagement 包 + 2 个 Nav route

- [ ] 4.1 新建 `app/src/main/java/com/yy/writingwithai/feature/settings/model/ModelManagementEntry.kt` —— `ModelManagementRoute` + `ModelProviderDetailRoute` Composable 入口
- [ ] 4.2 新建 `ModelManagementScreen.kt` —— 3 个 provider Card + 当前选中 + "测试连通"按钮
- [ ] 4.3 新建 `ModelManagementViewModel.kt` —— `@HiltViewModel`,接 `SecureApiKeyStore` + `ProviderPrefsStore` + `AiGateway`,提供 `setProvider(id, apiKey)` + `ping()`
- [ ] 4.4 新建 `ModelProviderDetailScreen.kt` —— 表单 + "显示" toggle + "保存"按钮
- [ ] 4.5 `app/AppNav.kt` 加 `@Serializable data object SettingsModelManagement` + `@Serializable data class SettingsModelProviderDetail(val providerId: String)` + 2 个 `composable<>` block

## 5. Settings 主屏入口

- [ ] 5.1 `feature/settings/SettingsScreen.kt` 加 ListItem"AI 模型管理",onClick `onModelManagementClick`
- [ ] 5.2 `SettingsEntry.SettingsRoute` 加 `onModelManagementClick: () -> Unit` 形参
- [ ] 5.3 `AppNav.kt` `composable<Settings>` 块传 `{ navController.navigate(SettingsModelManagement) }`

## 6. i18n

- [ ] 6.1 `values/strings.xml` 加:`model_management_title` / `model_management_subtitle` / `model_management_current` / `model_management_test_ping` / `model_provider_detail_base_url` / `model_provider_detail_save` / `model_provider_detail_show_key` / `model_provider_detail_saved_toast` / `ai_provider_not_configured` 等(预估 8-10 个 key)
- [ ] 6.2 `values-en/strings.xml` 同步 TODO 占位

## 7. 编译验证

- [ ] 7.1 `./gradlew :app:assembleDebug` 通过
- [ ] 7.2 `./gradlew :app:testDebugUnitTest` 通过(新 test 落地)
- [ ] 7.3 `./gradlew :app:lintDebug` 0 errors
- [ ] 7.4 `./gradlew :app:ktlintCheck` 无新增 violation
- [ ] 7.5 `grep -rE "feature.(quicknote|aiwriting.streaming|onboarding)" app/src/main/java/com/yy/writingwithai/feature/settings/model/` → 0 匹配

## 8. 真机验证

- [ ] 8.1 `./gradlew :app:installDebug` 到 PGU110
- [ ] 8.2 设置 → 模型管理 → 点 deepseek Card
- [ ] 8.3 填真实 apikey + 保存 → back 主屏显示"当前 = deepseek"
- [ ] 8.4 主屏点"测试连通" → 显示"可用 · 123ms"(或真实延迟)
- [ ] 8.5 详情页选中文本 → ✨ → 润色 → 真 SSE 流式输出(非 "FakeAIresponsefortesting")
- [ ] 8.6 接受替换正文 + 落 `ai_history`

## 9. spec 同步 + 归档

- [ ] 9.1 跑 `/opsx:sync provider-real-integration` 合入主 spec
- [ ] 9.2 跑 `/opsx:archive provider-real-integration` 收口
- [ ] 9.3 `docs/progress.md` 加 1 条"M5 + provider 接入" 进度条目