# ai-decompose-implementation Specification

## Purpose

`QuickNoteDetailScreen`「拆解」菜单项的 AI 实现:调用 LLM 抽取新实体 + 匹配已有实体 + 持久化关联。

Synced from OpenSpec change `entity-management-and-ai-decompose`(2026-07-08)。

## Requirements

### Requirement: AI decompose extracts new entities

The system SHALL call AI to analyze note content and extract entities not already present in the database. The AI SHALL return a JSON array of entities, each containing `entityType` (in Chinese) and `surfaceForm`.

#### Scenario: AI returns valid entity list
- **WHEN** user clicks "拆解" and AI returns `[{"type":"人物","surface":"张三"},{"type":"作品","surface":"《红楼梦》"}]`
- **THEN** system creates new `NoteEntityRow` records with `source = "AI_EXTRACTED"` for each new entity

#### Scenario: AI returns empty list
- **WHEN** user clicks "拆解" and AI returns `[]`
- **THEN** system shows Snackbar "未发现实体"

### Requirement: AI decompose matches existing entities

The system SHALL match note content against existing entities in the database (case-insensitive) and create `note_entities` associations for matches.

#### Scenario: Note contains existing entity
- **WHEN** note content contains "张三" and database has entity `person::zhangsan` with `surfaceForm = "张三"`
- **THEN** system creates `note_entities` association for this note and entity without calling AI

#### Scenario: Case-insensitive matching
- **WHEN** note content contains "zhang san" and database has entity `person::zhangsan` with `surfaceForm = "张三"`
- **THEN** system does NOT match (Chinese and English are different surface forms)

#### Scenario: Multiple occurrences of same entity
- **WHEN** note content contains "张三" three times
- **THEN** system creates only ONE `note_entities` record but renders all three occurrences with blue font + cross-star highlight

### Requirement: Re-decompose confirmation

The system SHALL show a confirmation dialog before re-decomposing a note that already has entities.

#### Scenario: Re-decompose confirmed
- **WHEN** user clicks "重新拆解" on a note with existing entities and confirms the dialog
- **THEN** system deletes existing `note_entities` for this note and runs full AI decompose

#### Scenario: Re-decompose cancelled
- **WHEN** user clicks "重新拆解" but cancels the dialog
- **THEN** system does nothing

### Requirement: Decompose loading state

The system SHALL show a full-screen loading indicator during AI decompose that blocks user interaction.

#### Scenario: Decompose in progress
- **WHEN** user clicks "拆解" and AI call is in progress
- **THEN** system shows full-screen loading with "正在拆解..." text and menu is disabled

#### Scenario: Decompose completes
- **WHEN** AI call completes successfully
- **THEN** loading dismisses and entity highlights (blue font + cross-star) appear immediately

### Requirement: Pre-decompose API key check

The system SHALL check if AI provider is configured before allowing decompose. If not configured, clicking the menu shows error and navigates to settings.

#### Scenario: No API key configured
- **WHEN** user clicks "拆解" without configured API key
- **THEN** system shows error dialog "请先配置 AI 模型" with "去设置" button that navigates to AI settings

#### Scenario: API key configured but test failed
- **WHEN** user clicks "拆解" with API key that failed connectivity test
- **THEN** system shows same error dialog as "No API key configured"

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