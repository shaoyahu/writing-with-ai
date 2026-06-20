## Why

M3 AI 操作(扩写 / 润色 / 整理)的 system prompt 在 `core/ai/prompt/*.kt` 写死,用户无法调整(想让 AI 用更"严肃"语气润色、或用"小红书爆款"风格扩写,无路径)。v1 用户群小,做 Settings 屏让用户配 3 类操作的 system prompt + 留一份默认 fallback,实现"低代码可玩性"。

## What Changes

- **新增** `core/prefs/PromptTemplateStore`(DataStore Preferences,key 集合 `prompt_template_expand/polish/organize`):存用户自定义 3 类操作 system prompt(可空,空时走 M3 写死默认)
- **新增** `core/prefs/PromptTemplate.kt` 数据类(`WritingOp` + `systemPrompt: String?`)和 `PromptTemplates(expanded/polish/organize: PromptTemplate)` 聚合
- **新增** `core/ai/prompt/DefaultPrompts.kt`:把 M3 写死的 3 个 system prompt 集中到 `object DefaultPrompts { fun forOp(op: WritingOp): String }`(供模板空时 fallback)
- **新增** `feature/settings/prompt/PromptTemplateScreen.kt` + `PromptTemplateViewModel`:Settings 屏新增"AI 提示词模板"入口,3 个 `TextField` 可编辑;提供"恢复默认"按钮
- **新增** `feature/settings/SettingsScreen.kt` 主屏(目前只有 1 个入口"AI 提示词模板" + 后续"数据迁移"由 M4-3 已落地)
- **修改** `feature/aiwriting/streaming/AiActionViewModel.start()`:`providerId` 仍走 `secureApiKeyStore` 解析;`systemPrompt` 从 `PromptTemplateStore.observeCurrent().first()` 取(`runBlocking` 同步,跟 `isConsented` 同一模式)
- **修改** `feature/aiwriting/streaming/AiActionViewModel` 删 M3 写死的 providerId 写死逻辑,改走 `secureApiKeyStore.resolveProviderId() → "deepseek" / "fake"`(M4-4 已加,本 change 复用)
- **修改** `feature/aiwriting/AiwritingEntry.kt`:暴露 `requestPromptTemplateSettings(navController)` 跳转入口
- **修改** `app/AppNav.kt` + `quick-note/spec.md` AppNav 段:加 `@Serializable data object Settings` + `data object SettingsPromptTemplate` 两个 Nav 路由
- **新增** Hilt module `core/prefs/PrefsModule`(M4-4 已有,扩 `providePromptTemplateStore`)
- **修改** `res/values/strings.xml` + `values-en/strings.xml`:+12 个 i18n key(`settings_*` / `prompt_*`)
- **新增** 测试:`core/prefs/PromptTemplateStoreTest.kt`(5 tests)+ `feature/settings/PromptTemplateViewModelTest.kt`(3 tests)
- **修改** `core/ai/prompt/DefaultPrompts.kt` 暴露给 `AiActionViewModel`(删 `core/ai/prompt/Expand.kt` / `Polish.kt` / `Organize.kt` 写死,合并到 `DefaultPrompts`)

## Capabilities

### New Capabilities

- `custom-prompt-template`: 用户可管理 3 类 AI 操作(扩写 / 润色 / 整理)的 system prompt;模板持久化到 DataStore;Settings 屏提供编辑 + 恢复默认;AiActionViewModel.start() 走模板(空时 fallback 默认)

### Modified Capabilities

- `ai-actions`: `AiActionViewModel.start()` 改走 `PromptTemplateStore` 取 system prompt(空时走 `DefaultPrompts.forOp(op)`);`providerId` 复用 M4-4 `secureApiKeyStore.resolveProviderId()` 路径
- `app-shell`: AppNav 加 `Settings` + `SettingsPromptTemplate` 两个 Nav route;QuickNoteListScreen overflow menu(M4-3)增"设置"入口跳 Settings;跨过 widget 入口

## Impact

**新文件**:
- `app/src/main/java/com/yy/writingwithai/core/prefs/PromptTemplateStore.kt`
- `app/src/main/java/com/yy/writingwithai/core/ai/prompt/DefaultPrompts.kt`
- `app/src/main/java/com/yy/writingwithai/feature/settings/SettingsEntry.kt`
- `app/src/main/java/com/yy/writingwithai/feature/settings/SettingsScreen.kt`
- `app/src/main/java/com/yy/writingwithai/feature/settings/prompt/PromptTemplateScreen.kt`
- `app/src/main/java/com/yy/writingwithai/feature/settings/prompt/PromptTemplateViewModel.kt`
- 测试:`core/prefs/PromptTemplateStoreTest.kt` / `feature/settings/PromptTemplateViewModelTest.kt`

**修改文件**:
- `core/ai/prompt/{Expand,Polish,Organize}.kt` 合并到 `DefaultPrompts`(可能删)
- `feature/aiwriting/streaming/AiActionViewModel.kt` start() 改走模板
- `feature/aiwriting/AiwritingEntry.kt` + `requestPromptTemplateSettings` 入口
- `app/AppNav.kt` 加 2 个 route
- `feature/quicknote/list/QuickNoteListScreen.kt` overflow menu 加"设置"入口
- `core/prefs/PrefsModule.kt` 扩 `providePromptTemplateStore`
- `res/values/strings.xml` + `values-en/strings.xml` +12 i18n key

**依赖**:
- 无新第三方依赖
- 复用 M4-4 `ConsentStore` + `SecureApiKeyStore`

**回归风险**:
- 用户写空 system prompt → 走 fallback 默认(空字符串 fallback 不行,要 `null` 触发 fallback)
- DataStore 冷启动 read + `runBlocking` 跟 M4-4 同意门同模式,主线程阻塞 ~30ms(可接受)
- 用户在 Settings 屏改 prompt 后,下个 AI 操作生效;当前正在 streaming 的不重做(用户可"再生成"拿新 prompt)
- system prompt 是用户文本,仍走 CLAUDE.md "prompt 注入防御"规则:不进 system 段拼接(本 change 仅替换 system 段内容,不拼接 user 文本)
