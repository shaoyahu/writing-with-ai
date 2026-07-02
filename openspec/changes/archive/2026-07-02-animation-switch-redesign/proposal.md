## Why

`AnimationStylePreviewScreen` 当前用 4 个 RadioButton 单选(MINIMAL / FLUID / IMMERSIVE / NONE)决定整套动画风格,4 个卡片里嵌入的 `AnimatedSwitch` 仅是 thumb 演示器——拨它不会改任何持久化状态,也不会影响 nav/tab 切换动画,用户从交互上完全感知不到它的作用,等于"摆设"。同时 `core/ui/AnimatedSwitch` 存在视觉缺陷:关闭态 thumb 离左边过近、打开态离右边过远(track 内 padding 不对称),所有引用方(目前 `SettingsScreen`、`NoteAssociationSettingsScreen` 等)都受影响。

本次改动:让 4 个卡片里的 switch 真正接管"导航动画"和"标签动画"两个独立开关,取代 4 选 1 的"整组风格"模型;同时修 `AnimatedSwitch` 的 thumb padding,所有引用方统一受惠。

## What Changes

- **`core/ui/AnimatedSwitch.kt`**:修复 track 内部 thumb 水平 padding 不对称的问题(关闭态 thumbX 起点 0、打开态 thumbX 终点 = trackWidth,中间 thumbSize 两端各有不等量空隙),统一改为 track 宽度 - thumbSize 后居中对称分布。
- **`core/prefs/UserPrefsStore.kt`**:新增两个 DataStore 持久化 key:
  - `nav_animations_enabled_v1: Boolean`(默认 `true`,控制 nav 切换是否带动画)
  - `tab_animations_enabled_v1: Boolean`(默认 `true`,控制 tab 切换是否带动画)
  - 暴露 `navAnimationsEnabledFlow: Flow<Boolean>`、`tabAnimationsEnabledFlow: Flow<Boolean>` 以及 `setNavAnimationsEnabled(Boolean)` / `setTabAnimationsEnabled(Boolean)`。
- **`core/ui/animation/AnimationTokens.kt`**:保持现有 nav/tab 风格 spec 体系不变,**新增** `tokensFor(style, navEnabled, tabEnabled)`,允许用 `Boolean` 覆盖:当 `navEnabled == false` 时返回的 `navEnter / navExit / navPopEnter / navPopExit` 全部退化为 `EnterTransition.None / ExitTransition.None`(无动画);`tabEnabled == false` 时 `tabContentSpec` 退化为 `snap()`。
- **`app/ui/theme/Theme.kt`**:`WritingAppTheme` 同时 collect `animationStyleFlow` + `navAnimationsEnabledFlow` + `tabAnimationsEnabledFlow`,reduce-motion 仍按现有 spec 强制 NONE。
- **`feature/settings/animation/AnimationStylePreviewScreen.kt` + `ViewModel`**:
  - 取消"4 选 1 RadioButton"模型,改为 **2 个独立开关行**:
    - 第一行 "导航动画" + `AnimatedSwitch`,控制 `navAnimationsEnabled`
    - 第二行 "标签动画" + `AnimatedSwitch`,控制 `tabAnimationsEnabled`
  - 保留"4 种风格(极简/弹性/沉浸/无)"作为**视觉参考 / 选中后生效**的次级选项(由现有 `RadioButton` 实现),仍写入 `animation_style_v1`。
  - 顶部 4 张"风格预览卡片"保留,仅作"风格库"参考——点选只改风格枚举、不影响两个开关的布尔。
- **i18n**:`values/strings.xml` + `values-en/strings.xml` 同步新增"导航动画"/"标签动画"两个开关的 label + description 字符串(key 名 `anim_toggle_nav_*` / `anim_toggle_tab_*`),保证 i18n parity。

**BREAKING**:本 change 修改 `animation-system` 现有 spec 中 `AnimationStylePreviewScreen lists 4 styles` 条款下的"4 个独立卡片各代表一种风格"的语义,需走 delta spec。

## Capabilities

### New Capabilities
无

### Modified Capabilities
- `animation-system`:调整 `AnimatedSwitch encapsulates Switch animation`(thumb padding 对称化)、`AnimationStylePreviewScreen lists 4 styles`(改为 2 个独立开关 + 4 张风格参考卡)、新增 `nav_animations_enabled_v1` / `tab_animations_enabled_v1` DataStore 契约、扩展 `AnimationTokens` 支持 `navEnabled/tabEnabled` override。

## Impact

- **核心 UI 组件**:`core/ui/AnimatedSwitch.kt`(所有调用方视觉统一)
- **持久化**:`core/prefs/UserPrefsStore.kt`(新增 2 个 key,现有 `animation_style_v1` 不破坏)
- **动画令牌**:`core/ui/animation/AnimationTokens.kt`(函数签名扩展,需更新所有调用 `tokens()` 的位置)
- **主题**:`app/ui/theme/Theme.kt`(collect 三路 Flow + reduce-motion 处理)
- **设置页**:`feature/settings/animation/AnimationStylePreviewScreen.kt` + `AnimationStylePreviewViewModel.kt`(交互重设计)
- **i18n**:`app/src/main/res/values/strings.xml` + `values-en/strings.xml`
- **既有 spec**:`openspec/specs/animation-system/spec.md` 走 delta,旧 requirement 中"4 选 1"语义需替换
- **测试**:`AnimationStylePreviewViewModelTest` / `UserPrefsStoreTest` 需新增/调整 case
- **风险**:`AnimatedSwitch` 是基础设施,所有引用方会因 padding 修改而视觉变化,需逐个 review 适配
- **不影响**:导航/标签/组件库 spec、其它 feature 的具体 UI
