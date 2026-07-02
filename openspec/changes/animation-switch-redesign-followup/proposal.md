# Proposal: animation-switch-redesign-followup

## Why

归档的 `2026-07-02-animation-switch-redesign` change 给「动画风格」页加了 2 个细分开关(导航动画 / 标签动画),并修复了 `AnimatedSwitch` 的 thumb padding 对称性。

上线后用户实测提出两点反馈:

1. **RadioButton 与 Icon(Check)重叠**:选中态每张风格卡片同时画左边的 `RadioButton(selected = true)` 实心圆和右侧的 `Icons.Filled.Check` 对勾,两个选中标志在同一卡片的视觉锚点区域同时出现,信息冗余、视觉冗余。
2. **细分开关位置错了**:用户原话「这里的动画风格是整个 APP 整体的动画风格,不是单独配置动画风格」。细分到 nav/tab 的开关不应该挂在「动画风格」主页面,因为本页语义是「全 APP 4 选 1 风格库」,把 nav/tab 开关放这会误导用户以为「动画风格」=「局部动画细调」。

底层能力(`AnimationTokens.tokensFor` + `UserPrefsStore` 2 个 Boolean key + `WritingAppTheme` 3 路 collect + nav/tab None / snap 退化)继续保留,因为它是真有用的能力;只把 UI 入口迁出本页,挪到一个二级页面。

## What Changes

- **新增** `feature/settings/animation/AnimationDetailScreen.kt` + `AnimationDetailViewModel.kt`,承载 2 个 nav/tab toggle。
- **新增** `feature/settings/animation/AnimationToggleRow.kt` 顶层(非 private),供两个动画页共用。
- **修复** `AnimationStylePreviewScreen.kt` `AnimationStyleCard` 的双标志 bug,只保留 `RadioButton`,移除右侧 `Icon(Check)`。
- **简化** `AnimationStylePreviewScreen.kt`,移除 2 个 toggle item + `AnimationToggleRow` 私有函数,改回 `AnimationStylePreviewViewModel` 只管 `animationStyle`(拆 VM)。
- **新增** 路由类型安全 `SettingsAnimationDetail` + `AppNav.composable<SettingsAnimationDetail>` 注册。
- **新增** `MeTabTarget.SettingsAnimationDetail` 枚举 + `AppShell` Me 路由表 exhaustive 分支。
- **新增** MyScreen Section 2「显示」下「动画详细」直达 ListItem。

## Impact

- 受影响 capability:`animation-system`
- 受影响文件:
  - `app/src/main/java/com/yy/writingwithai/feature/settings/animation/AnimationStylePreviewScreen.kt`(删除 2 toggle + 删 Check icon)
  - `app/src/main/java/com/yy/writingwithai/feature/settings/animation/AnimationStylePreviewViewModel.kt`(拆:删除 nav/tab 2 个 StateFlow + 2 个 toggle 方法)
  - `app/src/main/java/com/yy/writingwithai/feature/settings/animation/AnimationToggleRow.kt`(**新增**)
  - `app/src/main/java/com/yy/writingwithai/feature/settings/animation/AnimationDetailScreen.kt`(**新增**)
  - `app/src/main/java/com/yy/writingwithai/feature/settings/animation/AnimationDetailViewModel.kt`(**新增**)
  - `app/src/main/java/com/yy/writingwithai/feature/my/MyScreen.kt`(在 Section 2 加 ListItem)
  - `app/src/main/java/com/yy/writingwithai/feature/my/MeTabTarget.kt`(加新常量)
  - `app/src/main/java/com/yy/writingwithai/app/AppNav.kt`(新 route + composable 注册)
  - `app/src/main/java/com/yy/writingwithai/app/AppShell.kt`(Me 路由 exhaustive when 新分支)
  - `app/src/main/java/com/yy/writingwithai/feature/settings/animation/AnimationStylePreviewViewModelTest.kt`(拆:删除 nav/tab case)
  - `app/src/main/res/values/strings.xml` + `values-en/strings.xml`(加 `anim_detail_title` 1 对)
  - `openspec/specs/animation-system/spec.md`:`AnimationStylePreviewScreen lists 4 styles` requirement 改写为"只 4 风格卡" + 新增 `AnimationDetailScreen exposes nav/tab toggles` requirement

- **不动**:
  - `core/ui/AnimatedSwitch.kt`(thumb padding 对称修复保留)
  - `core/ui/animation/AnimationStyleTokens.kt`(`tokensFor` 函数保留)
  - `core/prefs/UserPrefsStore.kt`(2 个 Boolean key 保留)
  - `app/ui/theme/Theme.kt`(3 路 collect 保留)
  - 已归档的 `openspec/changes/archive/2026-07-02-animation-switch-redesign/`(只读历史)
