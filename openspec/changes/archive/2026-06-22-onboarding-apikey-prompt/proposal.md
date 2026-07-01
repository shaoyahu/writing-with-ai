## Why

首次启动只让用户勾「同意隐私条款」太浅。用户不知道为什么要填 apikey、不填能用哪些能力、填了消耗多少 token/钱。本 change 把首启引导页升级为「隐私 + apikey 教育 + 能力清单 + 成本说明」一站式弹窗，所有 AI 能力(扩写/润色/整理/实体抽取/语义兜底)首次启用前必须经此教育。

## What Changes

- **新增** 首启全屏页 `ApikeyPromptScreen`:三段结构(隐私提醒 / 能力清单 / 成本说明 / 输入引导)
- **新增** `ApikeyPromptViewModel`:`hasUserAckedApikeyNote` 状态;从 DataStore 读;用户勾「我已知晓」后写
- **新增** 持久化键 `ack_apikey_prompt_v1`(`UserPrefsStore`,**非**敏感，普通 DataStore)
- **新增** 跳转锚点:任何 AI 操作入口(扩写/润色/整理/实体抽取)首次触发时，若 `ack_apikey_prompt_v1 == false` → 拦截 → 弹教育页 → 用户确认后才放行
- **新增** 「AI 能力 + token 成本参考」表(写死常量，显示给用户):
  - 扩写 / 润色 / 整理:每次 ~500-1500 input tokens, ~1000-3000 output tokens
  - 实体抽取:每篇 ~300-600 input tokens, ~100-300 output tokens
  - 语义兜底:每次 ~2000-4000 input tokens(20 条候选 + 触发判断)
  - 折算人民币:约 ¥0.001-0.05 / 次(按 deepseek/MiniMax-M2.7/mimo 现价区间，**不承诺准确，以 provider 实际账单为准**)
- **修改** 现有 `ConsentScreen` / 首次启动流程:同意页后**追加** `ApikeyPromptScreen` 作为第二页;同意状态分别持久化(隐私同意 + apikey 教育分别记录，可独立重置)
- **新增** 设置页「重置 apikey 教育提示」入口(测试 / 重学用)，点击清 `ack_apikey_prompt_v1`
- **新增** i18n key:`apikey_prompt_*`(标题 / 段落 / 能力 / 成本 / 确认按钮 / 跳过按钮)

## Capabilities

### New Capabilities

无独立新 capability。能力清单展示 + 教育流程是 `onboarding-consent` 的扩展(Modified)。

### Modified Capabilities

- `onboarding-consent`:从「单页隐私勾选」升级为「隐私 + apikey 教育 + 能力清单 + 成本说明」;新增 `ack_apikey_prompt_v1` 持久化键;新增「apikey 教育拦截」行为(任何 AI 能力首次调用前必弹)

## Impact

- **代码**:`feature/onboarding/` 新增 `ApikeyPromptScreen` / `ApikeyPromptViewModel`;`feature/quicknote/detail/QuickNoteDetailScreen.kt` 等 AI 入口加 `if (!ack) showApikeyPrompt()` 守卫;`core/prefs/UserPrefsStore.kt` 增 key
- **数据**:`UserPrefsStore` 新增一个 boolean key,DataStore 增量加，无 schema 迁移
- **依赖**:无新增三方库
- **UI**:Material 3 既有 token;新 1 个全屏 Composable + 1 个拦截 dialog(给已装老用户)
- **测试**:`ApikeyPromptViewModel` 单测覆盖 ack 状态 + 拦截行为;`ApikeyPromptScreen` Compose test 覆盖首屏渲染 + 确认按钮
- **不在范围**:实时 token 成本显示(provider 计费口径不同);本 change 只给「参考范围」常量
- **不在范围**:apikey 实际输入 UI(已由 `model-management-detail-dropdown` change 覆盖)