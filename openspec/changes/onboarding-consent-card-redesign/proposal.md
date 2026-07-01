## Why

`animation-system-and-consent-redesign` 落地了 `ConsentSectionCard` / `ConsentProgressBar` / `ConsentBottomBar` 三个新组件和 `parseGroupedMarkdown` 解析器(`feature/onboarding/SimpleMarkdown.kt` 已含 `ConsentSection` 数据类)，但 task 9.1 当时打的是 "[x]"(实际未做 — 写 plan 时以"完成"标记)，导致 `OnboardingScreen.kt` 仍用旧的 `parseSimpleMarkdown + MarkdownBlockView` 平铺 LazyColumn，新组件只落盘未被消费。这是 M5 polish 的可见 UI 缺口:用户看到的还是旧条款页，看不到卡片分组 / 进度条 / 动画底部栏。

视觉差距:
- 旧版:头部 + 大段 LazyColumn + 两按钮，信息密度平铺，无分组
- 新版(计划):头部 + 进度条 + 5 张可折叠卡片(数据/AI/第三方/撤回/联系，首张默认展开)+ 动画底部栏(滚动到底前灰显+滚动到底过渡颜色)

`animation-system-and-consent-redesign` 任务列表里 13.4 真机场景也以"卸载重装 → onboarding 卡片式"为验收项之一，本 change 是该验收项的前置。

## What Changes

- `feature/onboarding/OnboardingScreen.kt` 改写 UI 内部，对外签名(`scrolledToBottom` / `onScrolledToBottomChange` / `onAccept` / `onReject`)保持不变
- 把旧 `parseSimpleMarkdown + MarkdownBlockView` 平铺换成 `parseGroupedMarkdown + ConsentSectionCard` 列表
- 头部下方挂 `ConsentProgressBar`，根据 LazyColumn 滚动比例 0→1 平滑动画
- 底部两个全宽按钮换成 `ConsentBottomBar`，启用/灰显走 `tween(300)` containerColor 过渡
- 首张卡片(`consent_section_data_summary`)默认 expanded，其余 collapsed
- `summaryResolver` 闭包按 H2 关键词匹配到 5 个 `consent_section_*_summary` stringRes;未匹配返回 null 时该 section 仍渲染但不挂摘要(`parseGroupedMarkdown` 当前实现是:summary==null 时跳过整段)。本 change 保持该行为(参见 design D2)
- 政策加载失败 fallback 文案 `R.string.onboarding_policy_load_failed` 保留

## Capabilities

### Modified Capabilities

- `onboarding-consent`:增加 1 个 Requirement "OnboardingScreen uses grouped ConsentSectionCard UI"，覆盖 `animation-system-and-consent-redesign/specs/consent-page-redesign/spec.md` 的落地(把 task 9.1 真正实现进 OnboardingScreen)
- `consent-page-redesign`:无新 Requirement(原 spec 已定义卡片式/进度条/底部栏/分组解析器/双语键，本 change 是实现层)，但用新 Scenario 钉住"OnboardingScreen 实际接 3 新组件"端到端验证点(2 个新 Scenario:`OnboardingScreen composes ConsentSectionCard list` / `OnboardingScreen mounts ConsentProgressBar + ConsentBottomBar`)

## Impact

- `feature/onboarding/OnboardingScreen.kt` — 内部 UI 重写，签名不变;`OnboardingRoute` 不用改
- `feature/onboarding/SimpleMarkdown.kt` — 已有 `parseGroupedMarkdown` / `ConsentSection`，本 change 不动
- `feature/onboarding/ConsentSectionCard.kt` / `ConsentProgressBar.kt` / `ConsentBottomBar.kt` — 已落盘，本 change 只 import 用，不动
- `res/values/strings.xml` + `res/values-en/strings.xml` — 5 个 `consent_section_*_summary` + 3 个 `consent_*` + 1 个 `onboarding_policy_load_failed` 已就位，本 change 0 新增 key
- 1 个新增 JVM 单测文件 `feature/onboarding/OnboardingScreenIntegrationTest`，覆盖:
  - 5 卡片渲染(`parseGroupedMarkdown` 5 H2 → 5 段)
  - 进度条 0→1 路径
  - 加载失败 fallback
  - 概要 resolver 命中 5 个 stringRes
- 不改 `OnboardingViewModel` / `OnboardingRoute` / `ConsentStore` / 隐私 policy md
