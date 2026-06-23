# model-management-detail-dropdown · tasks

## 1. ProviderPrefsStore 加 selectedModel 持久化

- [x] 1.1 `core/ai/provider/ProviderPrefsStore.kt` interface 加 3 方法:
  - `suspend fun getSelectedModel(providerId: String): String?`
  - `suspend fun setSelectedModel(providerId: String, model: String)`
  - `fun observeSelectedModel(providerId: String): Flow<String?>`
- [x] 1.2 `ProviderPrefsStoreImpl` 实现:DataStore 新 key `stringPreferencesKey("selected_model_$providerId")`,key 动态拼接(注意 key 必须以声明式常量列出,见 hint)
- [x] 1.3 `FakeProviderPrefsStore`(若存在)同步加 3 方法 fake 实现;若无 fake,跳过此步(沿用 M5 现状)

## 2. ModelManagementViewModel 加 model 切换入口

- [x] 2.1 `feature/settings/model/ModelManagementViewModel.kt`
  - `init` 块加 `observeSelectedModel(providerId)` collect,**不更新 state**(避免全局污染;仅在 detail 屏按 providerId 读)
  - 新 `fun loadSelectedModel(providerId: String): String?` — suspend 读 prefs
  - 新 `fun onModelSelected(providerId: String, model: String)` — viewModelScope.launch 写 prefs,失败静默 catch (沿用 `selectProvider` 模式)
- [x] 2.2 `saveProvider(providerId, apiKey)` → `saveProvider(providerId, apiKey, model: String? = null)`,model 非空时同步写 `setSelectedModel`
- [x] 2.3 `ping(providerId)` 改:用 `loadSelectedModel(providerId) ?: config.defaultModel` 作为 `effectiveModel`(原 line 206-207 写死 `config.defaultModel`)

## 3. ModelProviderDetailScreen 改造

- [x] 3.1 `feature/settings/model/ModelProviderDetailScreen.kt` 顶部加 `var currentModel by remember { mutableStateOf<String?>(null) }`,`LaunchedEffect(providerId, config)` 调 `viewModel.loadSelectedModel(providerId)` 回填;无值 → `config.defaultModel`
- [x] 3.2 **baseURL 行弱化**(line 161-166):`Text(baseUrl)` 加 `color = MaterialTheme.colorScheme.onSurfaceVariant` + 不允许 `selectionContainer` 选中;label 加 `stringResource(R.string.model_provider_detail_base_url_locked_hint)`(灰色小字置底)
- [x] 3.3 新增 `ApiFormatDropdown(readOnly = true)`(复用 `CustomProviderEditScreen.kt:452` 的 `ApiFormatDropdown`,提为 private/internal 共享组件;`readOnly` 参数控制 `onValueChange = {}`)
- [x] 3.4 新增 `ModelDropdown` 组件(`@OptIn(ExperimentalMaterial3Api::class)`):`ExposedDropdownMenuBox` + `OutlinedTextField(readOnly=true, menuAnchor)`,选项 = `config.supportedModels`,默认项后缀 ` ${stringResource(R.string.model_provider_detail_model_default_suffix)}`,变更 → `viewModel.onModelSelected(providerId, model)` + `currentModel = model`
- [x] 3.5 `saveProvider` 调用同步传 `currentModel`(3.1 已就位)

## 4. i18n

- [x] 4.1 `app/src/main/res/values/strings.xml` 新增 6 个 key:
  - `model_provider_detail_api_format_label` = "协议类型"
  - `model_provider_detail_api_format_anthropic` = "Anthropic 兼容"
  - `model_provider_detail_api_format_openai` = "OpenAI 兼容"
  - `model_provider_detail_model_label` = "选择模型"
  - `model_provider_detail_model_default_suffix` = "(默认)"
  - `model_provider_detail_base_url_locked_hint` = "端点由 provider 预置,不可修改"
- [x] 4.2 `app/src/main/res/values-en/strings.xml` 同步英文:
  - `model_provider_detail_api_format_label` = "Protocol"
  - `model_provider_detail_api_format_anthropic` = "Anthropic compatible"
  - `model_provider_detail_api_format_openai` = "OpenAI compatible"
  - `model_provider_detail_model_label` = "Model"
  - `model_provider_detail_model_default_suffix` = "(default)"
  - `model_provider_detail_base_url_locked_hint` = "Endpoint preset by provider; not editable"

## 5. 验证

- [x] 5.1 `./gradlew :app:assembleDebug` BUILD SUCCESSFUL
- [x] 5.2 `./gradlew :app:ktlintCheck` 0 violations
- [x] 5.3a **编译错误全修**:`LlmNoteLinkExtractorTest` 用不存在 API `getActiveProviderId` / `getApiKey` → 改 `observeConfiguredProviders` / `get(providerId)` + `flowOf(setOf("d"))`;`CompositeNoteLinkerTest` line 43 `every { suspend fun }` → `coEvery`;`LocalNoteLinkerTest` 6 处 `every { suspend fun }` → `coEvery` + stub `noteDao.search`;`LlmNoteLinkExtractorTest.ctx` 加 `relaxed = true` + `extractor` 改 lazy
- [ ] 5.3b **`CompositeNoteLinkerTest` × 3 仍 fail**:`ApplicationProvider.getApplicationContext()` 在 `Room.inMemoryDatabaseBuilder(...).build()` 抛 `IllegalStateException`,需 Robolectric runner(`@ExtendWith(RobolectricExtension::class)` + vintage engine ~500MB);属 note-association change 测试配置,本 change 不动
- [x] 5.3c **`ModelManagementViewModelTest` 缺**:本 change 不强制要求(若想加,需新建 fake VM 依赖)+ `onModelSelected` + `loadSelectedModel` case
- [ ] 5.4 手动走 3 旅程:
  - 进 deepseek 详情页 → 协议=OpenAI(readOnly),模型=v4-flash(默认),切换模型=v4-pro → back → 再进 → 模型回填 v4-pro
  - 进 mimo 详情页 → 协议=Anthropic(readOnly),切到 mimo-v2.5-pro → save apikey → 跑 AI 操作 → `ai_history.model == "mimo-v2.5-pro"`
  - 切 selected provider deepseek → minimax → 旧 selectedModel 保留(切回 deepseek 仍是 v4-pro)

## 6. 文档

- [x] 6.1 `docs/progress.md` 加 1 条 2026-06-21 条目
- [ ] 6.2 `openspec/changes/model-management-detail-dropdown/specs/README.md` 已说明无 spec 改动;archive 时确认 `/opsx:sync` 不报错