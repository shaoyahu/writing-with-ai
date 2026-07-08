## MODIFIED Requirements

### Requirement: Entity 抽取必须使用真 AI provider

`LlmEntityExtractor.extractAndPersist` MUST 仅在用户配置了真实 AI provider apikey 时调用 LLM。`BuildConfig.DEBUG` **不**再作为「无 apikey 时回退 fake」的理由:

- 无任何真实 provider apikey → `extractAndPersist` 立即返回 0,**不**调 AI Gateway,**不**走 fake 路径
- 上层 `QuickNoteDetailViewModel.decompose` 检测 `secureApiKeyStore.observeConfiguredProviders().first().isEmpty()` → 直接 emit `DecomposeState.ApiKeyMissing`,UI 弹「请先配置 AI 模型」错误对话框 + 「去设置」按钮
- `LlmEntityExtractor` 内部 MUST NOT 持有 `BuildConfig.DEBUG` 兜底分支,删 `?: if (BuildConfig.DEBUG) "fake" else return@withContext 0` 与 `if (providerId == "fake") ""` 旧模式

#### Scenario: debug 包无 apikey 抽取立即返 0 + ApiKeyMissing
- **WHEN** debug 包跑在真机/模拟器,用户未配置任何 AI provider apikey,触发拆解
- **THEN** `LlmEntityExtractor.extractAndPersist` 立即返 0(不调 AI Gateway);`QuickNoteDetailViewModel.decompose` 检测 providers 为空 → emit `DecomposeState.ApiKeyMissing`;UI 弹错误对话框「请先配置 AI 模型」 + 「去设置」按钮;**不**出现「未发现实体」Snackbar 误导用户

#### Scenario: debug 包有 apikey 调真 provider 抽实体
- **WHEN** debug 包跑在真机/模拟器,用户已配置 deepseek apikey,触发拆解
- **THEN** `LlmEntityExtractor` 调真 provider HTTP,LLM 返回 JSON 数组 → 走 `parseJsonEntities` + `entityDao.upsertAll` 落库,UI 显示蓝色字体 + 十字星高亮

#### Scenario: 拆解失败 AI 返回空数组 → 提示「未发现实体」
- **WHEN** 真 provider 调通,LLM 返回 `[]`(文本中确实无有意义实体)
- **THEN** `extractAndPersist` 返 0,VM emit `DecomposeState.Decomposed(0)`,UI 弹「未发现实体」Snackbar 走通

#### Scenario: grep 验证 LlmEntityExtractor 无 BuildConfig.DEBUG 引用
- **WHEN** `grep "BuildConfig.DEBUG" app/src/main/java/com/yy/writingwithai/core/note/entity/LlmEntityExtractor.kt`
- **THEN** 0 匹配