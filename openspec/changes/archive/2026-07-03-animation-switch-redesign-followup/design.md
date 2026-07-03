## Context

- 归档的 `2026-07-02-animation-switch-redesign` 给「动画风格」页塞了 2 个细分开关并修了 thumb padding,但实测用户反馈「动画风格」页的语义应是"全 APP 4 选 1 风格库",细分开关放这误导。
- 实测同时发现 `AnimationStyleCard` 选中态 `RadioButton` + `Icon(Check)` 双标志视觉冗余。
- 底层能力(`tokensFor` + `UserPrefsStore` 2 Boolean key + Theme 3 路 collect)用户没有撤回,继续保留。
- 项目路由约定(`AppNav.kt` L344-L407 + `app/AppShell.kt` L133-L149):全 `@Serializable data object / data class` 类型安全路由,Me tab → rootNavController 走 `when` exhaustive 翻译 `MeTabTarget` 枚举 → `RootRoute` 类型。
- 项目 MyScreen(`feature/my/MyScreen.kt`)全部为直达路由 ListItem,无二级入口范式 — 加新入口仍走同一种 `MyListItem` 样式。

## Goals / Non-Goals

**Goals:**

- 把 2 个 nav/tab 细分开关从「动画风格」主页迁到新页 `AnimationDetailScreen`。
- 修 `AnimationStyleCard` 双标志 bug(只保留 `RadioButton`)。
- 拆分 `AnimationStylePreviewViewModel`:原 VM 只管 `animationStyle`,新 VM `AnimationDetailViewModel` 管 `navAnimationsEnabled` + `tabAnimationsEnabled`。
- 提取 `AnimationToggleRow` 到 `feature/settings/animation/AnimationToggleRow.kt` 顶层 public Composable,供 2 页共用。

**Non-Goals:**

- 不动 `core/ui/AnimatedSwitch.kt`(thumb padding 对称修复保留)。
- 不动 `core/ui/animation/AnimationStyleTokens.kt`(`tokensFor` 保留)。
- 不动 `core/prefs/UserPrefsStore.kt`(2 Boolean key 保留,即使 UI 入口迁移,数据契约不变)。
- 不动 `app/ui/theme/Theme.kt`(3 路 collect 保留)。
- 不动已归档的 `2026-07-02-animation-switch-redesign/`(只读历史)。
- 不动 archive 主 spec `AnimationTokens supports navEnabled and tabEnabled override` requirement(`tokensFor` 契约不变)。

## Decisions

### 决策 1 — 入口位置:MyScreen Section 2「显示」下第 3 条直达

- 选项 A(采纳):MyScreen Section 2 加 `MyListItem(title=anim_detail_title, icon=Tune, onNavigate=SettingsAnimationDetail)`,跟现有「动画风格」「语言」平级。
- 选项 B:在「动画风格」页底部加跳转链接 — 缺点是「动画风格」页底部又出现"动画相关"控件,语义再次混杂。
- 选 A:符合 MyScreen 全部直达路由的项目惯例,用户认知一致(看见「显示」section,所有显示相关都在这)。

### 决策 2 — AnimationToggleRow 提取到包顶层 `animation/AnimationToggleRow.kt`

- 选项 A(采纳):顶层 public Composable,放在 `feature/settings/animation/` 同包内,跨此 2 个动画页面共享。
- 选项 B:在新页内复制实现 — 缺点是后续 2 处代码漂移。
- 选项 C:`core/ui/AnimationToggleRow.kt` 跨 feature — 缺点是 `core/ui` 留作可选(项目惯例),且目前只有 animation feature 用,提升范围太大。
- 选 A:同 feature 包内共享,与 CLAUDE.md「feature 必须自包含」的硬规则一致。

### 决策 3 — VM 拆分

- 选项 A(采纳):拆为 2 个 VM。`AnimationStylePreviewViewModel` 只管 `animationStyle`(只读 `animationStyleFlow` + `onStyleSelected`);`AnimationDetailViewModel` 只管 2 个 Boolean key(读 + 2 toggle setter)。
- 选项 B:保留单 VM,2 屏都引用同一 VM — 缺点是 2 屏对 VM 字段的依赖耦合,后续若加 token 详细粒度(例如 list item spec 独立开关)会有冲突。
- 选 A:符合"一个 feature Screen 一个 VM,VM 单一职责"的项目惯例。

### 决策 4 — AnimationStyleCard 单 RadioButton,移除 Icon(Check)

- 选项 A(采纳):只保留 `RadioButton(selected = true)` 作为 4 选 1 选中态视觉锚点。Material3 `RadioButton` 已经是充分的选中反馈,`Icon(Check)` 在这信息冗余。
- 选项 B:移除 `RadioButton`,只保留 `Icon(Check)` — 缺点是缺 radio 圆形选中锚点,与 4 选 1 单选语义不匹配。
- 选项 C:两边都保留 — 现状,正是用户反馈的 bug。
- 选 A:4 选 1 单选语义 + 标准 Material3 radio 锚点。

### 决策 5 — 路由:`SettingsAnimationDetail` 新增 + AppShell 分发

- 类型安全 `data object SettingsAnimationDetail`,在 `AppNav.kt` 紧挨 `SettingsAnimationStyle` 注册。
- `MeTabTarget` 加 `SettingsAnimationDetail` 枚举值;`AppShell.kt` `when` exhaustive 增加新分支。
- 不动根 NavHost 结构,只插入新 route。

### 决策 6 — spec delta

- `MODIFIED Requirements`:`AnimationStylePreviewScreen lists 4 styles` —— 改写为"只 4 张全局风格卡"语义,把原先"plus 2 independent toggle rows"的句子拆分出去。
- `ADDED Requirements`:`AnimationDetailScreen exposes nav/tab toggles` —— 描述 AnimationDetailScreen 的 2 toggle 行为 + ReduceMotionBanner + 路由注册。
- 不动 archive 已合并的 `NavAnimationsEnabled persisted` / `TabAnimationsEnabled persisted` / `AnimationTokens supports navEnabled and tabEnabled override`(这些契约本次 followup 没改)。

## Risks / Trade-offs

- [Risk] 拆 VM 后,旧 `AnimationStylePreviewViewModelTest` 包含 nav/tab toggle 测会编译失败 → Mitigation:把 nav/tab toggle case 迁到新 `AnimationDetailViewModelTest`。
- [Risk] "动画详细" 作为二级入口(2 层导航),用户从 MyScreen 需点 2 次才能到细分开关 → Mitigation:本 followup 接受此 UX 妥协,因为语义清晰优先于点击次数(用户的核心反馈就是"放这不对")。如果将来发现太深,promote 到 Section 2 直接显示。
- [Risk] archive 主 spec 的 `AnimationStylePreviewScreen lists 4 styles` requirement 被 MODIFIED 后,其 Scenario 列表里关于「2 toggle」的子句会被改写 → 这是预期;spec 语义需要反映"本页只剩 4 风格卡",残留的 toggle 语义搬到新 requirement。
- [Risk] `AnimationToggleRow` 提到顶层后,主功能页(AnimationDetailScreen)的预览密度可能不够(2 toggle 太单薄) → Mitigation:本页本身就只是 2 toggle + reduce-motion banner,单薄是预期 UX;必要时后续可以再加 "动画 transition duration slider" 这种扩展(留口子)。

## Migration Plan

1. **起草** OpenSpec artifacts(proposal / design / spec / tasks)— 已完成
2. **拆 VM + 提取 toggleRow**:先建顶层 `AnimationToggleRow.kt`,再拆 `AnimationStylePreviewViewModel`,再建 `AnimationDetailViewModel`
3. **修双重标志**:删 `AnimationStyleCard` 的 `Icon(Check)` 段 + 删 `Check` import
4. **简化主屏**:`AnimationStylePreviewScreen` 删 toggle item + toggle 函数
5. **新增二级页**:`AnimationDetailScreen.kt` 复用 `AnimationToggleRow` 渲染 2 toggle + banner
6. **注册路由**:`AppNav.composable<SettingsAnimationDetail>` + `MeTabTarget` + `AppShell` 翻译
7. **入口**:`MyScreen` Section 2 加 `MyListItem`
8. **i18n**:加 `anim_detail_title` zh + en 双语
9. **测试**:迁移旧 VMTest nav/tab case 到 `AnimationDetailViewModelTest`,新增 `AnimationDetailScreen` 渲染测试可选
10. **验证**:`assembleDebug` + `ktlintCheck` + `testDebugUnitTest` + `installDebug` 模拟器 review
11. **archive**:`openspec archive animation-switch-redesign-followup`,progress.md 追加
12. **Rollback**:回滚本次 followup 即可恢复「动画风格」页带 2 toggle 状态。底层代码(tokensFor / DataStore / Theme)不删,所以 revert 后 nav/tab 开关仍能工作(只是没人能进到它们)。

## Open Questions

无。本次 followup 决策点都已在 §Decisions 拍板。
