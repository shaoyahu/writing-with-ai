## 1. UI — ApikeyPromptScreen

- [ ] 1.1 新建 `feature/onboarding/ApikeyPromptScreen.kt`:三段滚动页(隐私提醒 / AI 能力清单 / 成本说明)
- [ ] 1.2 新建 `feature/onboarding/ApikeyPromptViewModel.kt`:读 / 写 `ack_apikey_prompt_v1`
- [ ] 1.3 `AppNav` 路由表加 `onboarding/apikey-prompt`,同意页完成 → navigate apikey-prompt → 用户点确认或跳过 → navigate main
- [ ] 1.4 新建 `ApikeyPromptDialog`(复用 ApikeyPromptScreen 内容)给已装老用户拦截 AI 入口用

## 2. DataStore — UserPrefsStore

- [ ] 2.1 `core/prefs/UserPrefsStore.kt` 增 key `ack_apikey_prompt_v1: Boolean`(default false)
- [ ] 2.2 暴露 `setAckApikeyPrompt(ack: Boolean): suspend` / `ackApikeyPromptFlow(): Flow<Boolean>`

## 3. AI 入口拦截

- [ ] 3.1 `feature/aiwriting/` 下所有 AI 操作 ViewModel(扩写 / 润色 / 整理)开头加守卫:若 `!ackApikeyPrompt` → 弹 ApikeyPromptDialog → 用户确认后才执行
- [ ] 3.2 `core/note/extractor/LlmEntityExtractor.kt` 加同样的守卫(实体抽取也属于 AI 能力)
- [ ] 3.3 `core/note/impl/SemanticNoteLinker.kt` 同上(语义兜底也是 AI 调用)

## 4. 成本参考常量

- [ ] 4.1 新建 `core/prefs/AiCostReference.kt`(object):写死 4 类 AI 能力 + input / output token 区间 + 折算人民币区间(¥0.001-0.05/次)
- [ ] 4.2 ApikeyPromptScreen 渲染这张表 + 每行末加「以 provider 实际账单为准」

## 5. 设置页 — 重置入口

- [ ] 5.1 设置页「隐私 / 数据」section 加「重新显示 API Key 说明」按钮 → 调 `UserPrefsStore.setAckApikeyPrompt(false)` → 提示「下次使用 AI 功能时会再次显示」

## 6. i18n

- [ ] 6.1 `values/strings.xml` 增:`apikey_prompt_title` / `_intro` / `_capability_expand` / `_capability_entity_extract` / `_capability_semantic_link` / `_cost_disclaimer` / `_btn_ack` / `_btn_skip` / `_reset_label`
- [ ] 6.2 `values-en/strings.xml` 同步英文

## 7. 测试

- [ ] 7.1 `ApikeyPromptViewModelTest`:ack 写入 / 读取
- [ ] 7.2 `ApikeyPromptScreen` Compose test:首屏渲染「我知道了」「稍后设置」两按钮可点
- [ ] 7.3 AI 入口拦截测试(扩写 ViewModel):`ackApikeyPrompt=false` 时不发起 AI 调用、弹 dialog 信号

## 8. 编译 + ktlint

- [ ] 8.1 `./gradlew :app:assembleDebug` 通过
- [ ] 8.2 `./gradlew :app:ktlintCheck` 通过
- [ ] 8.3 `./gradlew :app:testDebugUnitTest` 全绿
- [ ] 8.4 `./gradlew :app:check` 全绿