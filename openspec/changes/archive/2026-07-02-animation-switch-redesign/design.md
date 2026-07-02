## Context

**现状**:`core/ui/AnimatedSwitch` 是基础设施组件,所有 toggle 行都走它,当前 thumb 水平 padding 在 track 内不对称(关闭时偏左、打开时偏右),用户和团队成员都已经肉眼观察到。`AnimationStylePreviewScreen` 沿用 M3 时代的 4 选 1 设计——`RadioButton` + 4 张嵌入 `AnimatedSwitch` 的预览卡片;卡片里的 `AnimatedSwitch` 仅是 thumb 演示器(本地 `remember { mutableStateOf(...) }`),拨它不会改任何持久化、不影响 nav/tab 切换动画;卡片底部"导航/标签"两个圆点是静态 `Box`,完全无交互。

**约束**:
- `AnimatedSwitch` 是基础设施,改 padding 必然影响所有引用方(目前主要是 `SettingsScreen` / `NoteAssociationSettingsScreen`),需要在所有调用点 review
- 现有 DataStore key `animation_style_v1`(String,枚举名)已经在线,新加 `nav_animations_enabled_v1` / `tab_animations_enabled_v1`(Boolean)不能破坏既有契约
- 现有 reduce-motion 强制 NONE 的语义(spec `### Requirement: Reduce-motion forces NONE`)必须保留
- i18n 必须 parity(`values/strings.xml` + `values-en/strings.xml` key 集一致)
- ktlint + 编译要过

**Stakeholder**:日常调试 APP 的"我"(开发者)+ 安装 APP 真实用户。

## Goals / Non-Goals

**Goals**:
- 修 `AnimatedSwitch` thumb padding 在 track 内的对称性,所有调用方视觉一致
- 用 2 个 `AnimatedSwitch` 真正接管"导航动画"和"标签动画"的开关,落 DataStore 持久化
- 让 4 张"风格预览卡片"回归"风格库"角色——点选只决定风格枚举,不影响 2 个布尔开关
- reduce-motion 开启时,2 个开关 UI 上显示为 disabled(只读 OFF)且 token 强制 NONE

**Non-Goals**:
- 不重做整张 `AnimationStylePreviewScreen` 的视觉风格(继续沿用 M3 卡片 + 主题色)
- 不动 `AppNav.kt` / `AppShell.kt` 现有 transition 调用方——它们已经读 `LocalAnimationTokens`,token 被"覆盖"后无需改代码
- 不引入新依赖
- 不改 `SwitchableSwitchSpec`(thumb 动画 spec)以外的动画时长常量
- 不动 `app/lint-baseline.xml`

## Decisions

### Decision 1: thumb padding 修复 = 让 `offsetX` 落在 `(trackWidth - thumbSize) / 2` 而非 0 / trackWidth

**Why**:`AnimatedSwitch.kt` 当前用 `Modifier.offset { IntOffset(thumbX, 0) }` 直接把 thumb 顶到 track 左右两边,所以 thumbSize 一侧的 padding 是 0,另一侧是 `trackWidth - thumbSize`。改成:
- 关闭态 `offsetX = (trackWidth - thumbSize) / 2`
- 打开态 `offsetX = (trackWidth - thumbSize) / 2`(`checked = true` 时不偏移,或者通过 `RoundedCornerShape` 让 thumb 在 track 中心始终保持 `(trackWidth - thumbSize) / 2` 的对称 padding)

**Material 3 真实行为参考**:`androidx.compose.material3.Switch` 的内部实现正是用 `thumbPosition = (trackWidth - thumbSize) / 2 + (trackWidth - thumbSize) * checkedProgress` 让 thumb 在两个对称端点之间滑动(两端各留 `(trackWidth - thumbSize) / 2` 的 padding)。我们直接对齐 M3 内部算法。

**Alternatives considered**:
- 在 track 上加 `.padding(horizontal = Xdp)` —— 影响点击区域,且不会改变 thumb 内部 padding,问题没解
- 改用 `BoxWithConstraints` + 自绘 track/thumb —— 改动太大,影响 M3 默认颜色/动画曲线

### Decision 2: 持久化 2 个 Boolean key,而不是 1 个 enum 扩展

**Why**:用户的心智模型是"我只想关 nav 动画,保留 tab 动画"——这种独立控制**必须用 2 个 Boolean 表达**。如果塞进 `animation_style_v1` 这种 enum,会变成"再开 4 个 nav-only / tab-only 子枚举",但状态空间是 2×2 = 4 种 nav/tab 组合,枚举里需要 4 个 style + 4 个 nav-only + ... 不可持续。

**DataStore key 设计**:
- `nav_animations_enabled_v1: BooleanPreferencesKey`(默认 `true`)
- `tab_animations_enabled_v1: BooleanPreferencesKey`(默认 `true`)
- `stringPreferencesKey("animation_style_v1")` 保留(枚举名,不变)

**API 形状**(`UserPrefsStore.kt`):
```kotlin
val navAnimationsEnabledFlow: Flow<Boolean>
val tabAnimationsEnabledFlow: Flow<Boolean>
suspend fun setNavAnimationsEnabled(enabled: Boolean)
suspend fun setTabAnimationsEnabled(enabled: Boolean)
```

**Alternatives considered**:
- 用 `Set<String>` 存多个开关的"enabled key 集合"——过度抽象,2 个独立 key 更直白
- 用 JSON 存整个 `AnimationSettings` 对象——schema 演进困难,DataStore 不擅长存复杂对象

### Decision 3: `AnimationStyle.tokens()` 保留,新增 `tokensFor(style, navEnabled, tabEnabled)`

**Why**:`AnimationTokens` 的生产侧是 `WritingAppTheme`(`Theme.kt:98`),它从 3 路 Flow collect 后算 tokens。其它代码可能也会读 `LocalAnimationTokens.current` 拿到 nav/tab 各自的 spec。两种 API 共存:
- `tokensFor(IMMERSIVE, navEnabled = false, tabEnabled = true)`:Theme 主调用,新代码统一用
- `AnimationStyle.IMMERSIVE.tokens()`:历史调用方(如单测、preview)可以保留,实现为 `tokensFor(this, true, true)`

**实现要点**:
```kotlin
fun tokensFor(style: AnimationStyle, navEnabled: Boolean, tabEnabled: Boolean): AnimationTokens {
    val base = style.toBaseTokens()
    val navSpec = if (navEnabled) base.navEnter else EnterTransition.None
    val navExit = if (navEnabled) base.navExit else ExitTransition.None
    val navPopEnter = if (navEnabled) base.navPopEnter else EnterTransition.None
    val navPopExit = if (navEnabled) base.navPopExit else ExitTransition.None
    val tabSpec = if (tabEnabled) base.tabContentSpec else snap<Int>()
    return base.copy(
        navEnter = navSpec,
        navExit = navExit,
        navPopEnter = navPopEnter,
        navPopExit = navPopExit,
        tabContentSpec = tabSpec
    )
}
```

**Alternatives considered**:
- 让 `LocalAnimationTokens` 直接带 `navEnabled/tabEnabled` 字段,所有 reader 自己判断 —— 污染面太大,违反"token 是终态"的原则
- 用 `CompositionLocal` 嵌套(`LocalAnimationTokens` 套 `LocalAnimationEnabled`)—— 同样增加 reader 心智负担

### Decision 4: `WritingAppTheme` 3 路 Flow 同时 collect + reduce-motion 保持 NONE

**Why**:`Theme.kt` 现在 `collectAsStateWithLifecycle(userPrefsStore.animationStyleFlow)` 拿到 style。改成:
```kotlin
val style by userPrefsStore.animationStyleFlow.collectAsStateWithLifecycle(AnimationStyle.MINIMAL)
val navEnabled by userPrefsStore.navAnimationsEnabledFlow.collectAsStateWithLifecycle(true)
val tabEnabled by userPrefsStore.tabAnimationsEnabledFlow.collectAsStateWithLifecycle(true)
val reduceMotion = isReduceMotionEnabled()
val effectiveStyle = if (reduceMotion) AnimationStyle.NONE else style
val tokens = remember(effectiveStyle, navEnabled, tabEnabled, reduceMotion) {
    if (reduceMotion) AnimationStyle.NONE.tokens()
    else tokensFor(effectiveStyle, navEnabled, tabEnabled)
}
```

reduce-motion 仍是最高优先级(spec 不变),它会**强制 style=NONE + 不影响 navEnabled/tabEnabled 持久化值**(用户关掉 reduce-motion 后还能恢复自己的偏好)。

**Alternatives considered**:
- reduce-motion 强制把 2 个开关也写盘改成 false —— 违反 spec "SHALL be preserved (not overwritten)"
- 用 derivedStateOf 包 3 路 —— 没用,只是包装层,真正的状态变化需要 remember keys

### Decision 5: 设置页 = 2 个开关行 + 4 张风格卡片,卡片降级为"风格库"

**Why**:用户原话"用 switch 真的控制 nav/tab 动画",所以 2 个开关是主控;4 个 style(MINIMAL/FLUID/IMMERSIVE/NONE)仍是有意义的"动画风格库",只是降级为"选中后改 transition 曲线"。

**布局**:
```
TopAppBar: 动画风格
─────────────────────────────────
[ Card: 导航动画 ]  [AnimatedSwitch(checked = navEnabled) ]   ← 真正控制
[ Card: 标签动画 ]  [AnimatedSwitch(checked = tabEnabled) ]   ← 真正控制
─────────────────────────────────
"动画风格库" (小标题)
[ Card MINIMAL ]  [ Card FLUID ]  [ Card IMMERSIVE ]  [ Card NONE ]
   4 张保留,点选只改 style 枚举
─────────────────────────────────
[ reduce-motion banner(条件渲染) ]
```

**MVVM 影响**:
- `AnimationStylePreviewViewModel` 暴露:
  - `animationStyle: StateFlow<AnimationStyle>`(既有)
  - `navAnimationsEnabled: StateFlow<Boolean>`(新增)
  - `tabAnimationsEnabled: StateFlow<Boolean>`(新增)
  - `onStyleSelected(style)`(既有)
  - `onNavAnimationsToggled(enabled: Boolean)`(新增)
  - `onTabAnimationsToggled(enabled: Boolean)`(新增)
- 内部收集 3 路 Flow(注意:`animationStyle` 既有 → 复用)

**Alternatives considered**:
- 把 4 张卡片完全砍掉,只留 2 个开关 —— 失去"风格参考"价值,IMMERSIVE/FLUID 这些"风格曲线"再也选不到
- 把 2 个开关塞进 4 张卡片的"预览位" —— 行为混乱,卡片里 thumb 是 1 个具体风格的具体表现,跟"全局开关"是不同概念

### Decision 6: i18n 字符串命名走 `anim_toggle_nav_*` / `anim_toggle_tab_*`

**Why**:
- `anim_style_*` 已经在线(10 个 key),本次新加 4 个:
  - `anim_toggle_nav_title` = "导航动画"
  - `anim_toggle_nav_description` = "页面之间切换的过渡动画"
  - `anim_toggle_tab_title` = "标签动画"
  - `anim_toggle_tab_description` = "Tab 切换时的内容过渡"
- en 版本同步:Navigation animations / Tab animations + 描述

**i18n parity check**:`grep -oE 'name="anim_(style|toggle)_[a-z_]+"'` 在两个 strings.xml 输出 key 集必须完全相同(10 + 4 = 14 个 key)。

## Risks / Trade-offs

- **[Risk] `AnimatedSwitch` padding 修改影响所有调用方视觉** → **Mitigation**:先全仓 grep 出所有 `AnimatedSwitch(` 调用点(预计 2-4 处),逐个目视 review;在 `MainActivity` 跑一次 Compose Preview 或在 debug APK 跑一次截图比对。
- **[Risk] `AnimationStyle.tokens()` 历史调用方(可能存在于单测)误以为没改** → **Mitigation**:`tokens()` 内部实现指向 `tokensFor(this, true, true)`,行为完全一致,纯加新方法;不删旧 API。
- **[Risk] 2 个 Boolean key 升级路径**:DataStore 已有 `animation_style_v1` 但没这 2 个新 key,首次启动会走 `true` 默认值(spec 已声明) → **Mitigation**:`Flow` 端用 `.map { it ?: true }` 兜底,无需迁移代码。
- **[Risk] reduce-motion 开启时 UI 看到开关是 OFF 但用户改不了** → **Mitigation**:在 `AnimatedSwitch` 外面包一个 `if (reduceMotion) ... else Interactive`,且 tooltip/banner 明确说明"系统无障碍设置已强制关闭"。
- **[Risk] 测试用例需要新增/调整** → **Mitigation**:`AnimationStylePreviewViewModelTest` 加 4 个 case(2 个 toggle 持久化、reduce-motion 下只读、风格卡片仍可点);`UserPrefsStoreTest` 加 2 个 key 的"default true / corrupt fallback true"测试;`AnimationTokensTest` 加 `tokensFor(IMMERSIVE, false, false) → 全部 None / snap` 测试。
- **[Risk] `tokensFor` 影响其它非 nav/tab 字段** → **Mitigation**:实现只覆盖 `navEnter / navExit / navPopEnter / navPopExit / tabContentSpec` 5 个字段,其它(switchSpec / expandEnter / collapseExit)直接透传。
- **[Risk] Compose `remember(effectiveStyle, navEnabled, tabEnabled, reduceMotion)` 在 reduce-motion 变化瞬间与用户偏好切换竞态** → **Mitigation**:把 reduce-motion 作为 CompositionLocal 注入时统一读取,不与 DataStore 抢占;同时 collectAsStateWithLifecycle 默认在 STARTED 状态,减少后台时改值。

## Migration Plan

1. **Phase 1 - 基础设施**:`AnimatedSwitch` padding 修 + `AnimationTokens.tokensFor` 加 + `UserPrefsStore` 加 2 个 key
2. **Phase 2 - Theme 接线**:`Theme.kt` 改 3 路 collect + reduce-motion
3. **Phase 3 - 设置页**:`AnimationStylePreviewScreen` 重构(2 个开关行 + 4 张风格卡)+ VM 加 2 个 toggle 方法
4. **Phase 4 - i18n**:values/strings.xml + values-en/strings.xml 同步加 4 个 key
5. **Phase 5 - 测试**:单测加 8+ case(VM / PrefsStore / AnimationTokens)
6. **Phase 6 - 验证**:`./gradlew :app:installDebug` 装到模拟器,目视 review 设置页 + 全局 nav/tab 切换效果

**回滚策略**:本次是纯 UI + 持久化增量,无网络协议变更;若线上发现回退,只需 `git revert` 单 commit,DataStore 新 key 在旧代码中会被忽略(因为旧代码不读这 2 个 key),不会破坏 `animation_style_v1`。

## Open Questions

- **Q1**:`nav_animations_enabled_v1` / `tab_animations_enabled_v1` 的默认值是 `true` 还是 `false`?——倾向 `true`(尊重现状;reduce-motion 走 NONE 仍保 fallback)。
- **Q2**:reduce-motion 开启时,2 个开关是否要显示为 disabled(只读 OFF)还是直接隐藏?——倾向 **disabled 显示**(保持布局稳定,用户能看到"我有这个选项,只是被系统关了")。
- **Q3**:4 张风格卡片的"点选 style" 是否也受 reduce-motion 影响?——倾向 **不影响**:reduce-motion 只强制 token,style 枚举仍可改,关掉 reduce-motion 后立即生效。
- **Q4**:`tokensFor` 这个名字是否要改成 `tokensWithOverride` 之类更直白?——倾向 **`tokensFor`**(短、与既有 `tokens()` 一致)。

待 review 时由用户最终拍板 Q1-Q4。
