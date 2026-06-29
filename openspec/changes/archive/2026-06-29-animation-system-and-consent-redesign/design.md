## Context

当前项目动画效果散落:`expandVertically()` / `shrinkVertically()` 裸用、NavHost 无 enter/exit 过渡、Switch 用 Material3 默认 spring、Tab 切换直接重绘;`UserPrefsStore` 不感知任何 UI 节奏;使用条款页是 `Column` + `LazyColumn` + `MarkdownBlockView` 平铺,用户反馈"读完前不知道还剩多少 / 同意按钮何时可点"。

R7 review 给出 scope leak 报告(CRITICAL):动画系统 + 条款页重设计两件事在 plan mode 阶段写出实现后,直接落到 main 分支,跳过了 OpenSpec 流程。本 change 走 Option A 路径——把现有 7 untracked 业务代码文件 + 12+ M 文件 + 1 plan 文件 + ~52 i18n key 当作"已实现"接入 change,补齐 proposal / design / specs / tasks 文档化,保留代码,后续 `/opsx:archive` 收口。

约束(摘自 CLAUDE.md):
- AnimationStyle 仅持久化枚举名(String),DataStore key `animation_style_v1`,未知值 MUST 回退 MINIMAL(防 schema 演化崩设置)。
- Theme 不反向依赖 core(单向),reduce-motion 检测走 `AccessibilityManager`,不引入额外依赖。
- 字符串 MUST 双语对齐(key 集合 `values/strings.xml` == `values-en/strings.xml`)。
- 不引入新外部库(只用现有 Compose Animation + DataStore + AccessibilityManager)。

## Goals / Non-Goals

**Goals:**
- 1 个数据类 `AnimationTokens` 覆盖所有切换场景(nav × 4 方向 / Switch / Tab / 展开折叠 / Dialog)。
- 4 套风格(MINIMAL/FLUID/IMMERSIVE/NONE)用户可在设置页选;reduce-motion 开启时强制 NONE。
- AnimationStyle 跨进程持久化,启动读到正确风格,运行时切换不丢状态。
- 条款页改成"头部 + 进度条 + 卡片列表 + 动画底部栏",滚动到底部解锁同意按钮(逻辑不变)。
- `SimpleMarkdown` 扩展向后兼容:旧 `parseSimpleMarkdown` + `MarkdownBlockView` 不动。
- 24 个 scope key 双语对齐,key 集合零差异。

**Non-Goals:**
- 不引入第三方动画库(Motion / Accompanist / Lottie);只用 Compose Animation。
- 不动 AI 集成、apikey 加密存储、release 通道、M3-M6 业务流。
- 不实现跨屏同步动画风格(只在单设备持久化)。
- 不做"动画风格云端备份"(v1 backup 关闭,CLAUDE.md 硬规则)。
- 不重写 OnboardingScreen 的 ViewModel / state 逻辑,只重写 UI。

## Decisions

### D1. CompositionLocal 用 `compositionLocalOf` 而非 `staticCompositionLocalOf`

**选择**:`compositionLocalOf<AnimationTokens>`。

**理由**:用户切换动画风格时值变化,需要触发 Composition 重组读取新值;`compositionLocalOf` 在值变化时只重组读取者,粒度比 `staticCompositionLocalOf`(整棵子树重组)细。设置页切换风格 → 实时预览卡片动画刷新 → 主页 NavHost 重新读 enter/exit lambda,这都依赖 compositionLocalOf 的细粒度追踪。

**替代**:staticCompositionLocalOf — 在切换瞬间整棵 Composition 重组,卡顿;否决。

### D2. AnimationStyle 持久化用 `stringPreferencesKey`,不存 ordinal

**选择**:DataStore `stringPreferencesKey("animation_style_v1")` 存枚举名(`"MINIMAL"` / `"FLUID"` / `"IMMERSIVE"` / `"NONE"`);读取时 `enumValueOf<AnimationStyle>()`,失败回退 MINIMAL 并 LOG warn。

**理由**:枚举 ordinal 在 reorder / insert 时崩;枚举名在编译器改名时崩但 IDE 重构更友好;两者都有风险,但 enum 名 + 失败回退 + LOG 比 ordinal 静默错位更可控。`_v1` 后缀为未来 schema 演化留位(`animation_style_v2` 加字段时并存过渡)。

**替代**:ordinal 持久化 — 否决,reorder 灾难。

### D3. reduce-motion 检测用 `AccessibilityManager`,不引入 BuildConfig flag

**选择**:`val am = LocalContext.current.getSystemService(AccessibilityManager::class.java); am?.isReduceMotionEnabled ?: false`;Theme 在 Composition 中 `LaunchedEffect(am)` 监听变化。

**理由**:Android 9+ (API 28) 系统设置"减少动画"开关,用户改完即刻生效;Compose 端可观察,无需自己存 flag。BuildConfig flag 会脱钩系统设置。

**替代**:DataStore 存用户手动开关 — 否决,与系统设置重复。

### D4. NavHost 过渡走 lambda,不入 SettingsScreen preview

**选择**:`NavHost(startDestination = ..., enterTransition = { navAnimTokens.navEnter }, ...)`;tokens 从 `LocalAnimationTokens.current` 在 lambda 里取。

**理由**:NavHost enter/exit 是 `NavGraphBuilder` 的 lambda,执行时已经在 Composition 里能取到 LocalAnimationTokens。设置页预览里 nav 过渡用 `AnimatedContent` + 同样 spec 模拟,避免真 navigate(会污染回退栈)。

### D5. Switch 用 token 化封装而非 `LocalAnimationTokens.current` 直读

**选择**:新增 `core/ui/AnimatedSwitch.kt` 暴露 `AnimatedSwitch(checked, onCheckedChange, modifier)`;内部读 `LocalAnimationTokens.current.switchSpec` 控制 thumbPosition 动画。

**理由**:每个 Switch 调用点直读 CompositionLocal 会散落 4+ 处,封装后换 spec 1 处搞定;且封装点可加 `TrackRecomposition` 调试 hook,业务侧感知不到。`NoteAssociationSettingsScreen` + `SettingsScreen` 共 2 处替换,纯机械 edit。

### D6. `parseGroupedMarkdown` 不破坏现有 API

**选择**:`fun parseGroupedMarkdown(text: String): List<ConsentSection>` 新增,与 `parseSimpleMarkdown` 并存;内部复调用现有 `parseSimpleMarkdown`,然后按 H2(`## `)split 重组 blocks。

**理由**:旧 onboarding 的 LazyColumn 直接消费 `MarkdownBlock`;新卡片化 UI 才消费 `ConsentSection`。两套并存,旧 API 不动 → 旧 VM/Preview 不破;`MarkdownBlockView` Composable 不动。

### D7. ConsentSectionCard 用 `AnimatedVisibility` + token

**选择**:卡片展开/折叠 `AnimatedVisibility(visible = expanded, enter = LocalAnimationTokens.current.expandEnter, exit = collapseExit)`;同意按钮 disabled→enabled 用 `animateColorAsState` + `animateFloatAsState` 过渡 alpha(0.38→1.0)+ containerColor。

**理由**:`expandEnter`/`collapseExit` 已包含 4 套风格的 spring/tween 参数,卡片直接消费;按钮颜色+alpha 过渡不依赖 token(系统常量 tween(300))。

### D8. 不写新 OpenSpec change 给"reduce-motion 适配"

**理由**:reduce-motion 是 AnimationStyle 子规则(强制 NONE),不构成独立 capability;纳入 `animation-system` spec 即可。

## Risks / Trade-offs

- **R1**:Setting 切换 AnimationStyle 时,如果当前正在 navigate,新 lambda 应用存在 1 frame 延迟(CompositionLocal 重组需时)→ 接受,人眼难感知;Mitigation:不在导航中切风格(设置页是 leaf route,返回后下次 navigate 才生效)。
- **R2**:`UserPrefsStore` 扩展 Flow 会让所有 `WritingAppTheme` 调用方多 collect 一个 flow → 现有 Splash / Preview 路径多 1 个轻量 collect;Mitigation:`UserPrefsStore.animationStyleFlow` 用 `stateIn(scope, SharingStarted.Eagerly, AnimationStyle.MINIMAL)` 缓存,避免每次重组重读 DataStore。
- **R3**:`parseGroupedMarkdown` 按 H2 split 简单,但条款 markdown 可能含 H2 内嵌代码块 / 嵌套列表 → 当前 markdown 子集稳定(条款页是手写,无嵌套),接受;Mitigation:加 1 个单元测试覆盖 "H2 + 内嵌代码块" 案例,如未来扩展再升级 parser。
- **R4**:动画风格 4 选 1 增加首启动 init 时间(读 DataStore + 监听 AccessibilityManager)→ 预计 <50ms,可接受;Mitigation:`AnimationStyle.MINIMAL` 是 default,DataStore 第一次读无值时直接返,不等 IO。
- **R5**:`OnboardingScreen` 重写 UI 可能引入新 bug(滚动解锁逻辑被改坏)→ Mitigate:重写前后都用现有 `listState.layoutInfo` 检查,加 golden test / 手测 5 步覆盖;VM 接口不变,旧测试套件若覆盖 VM 则不破。
- **R6**:24 个新 string key 拼写不一致风险(中英文符号 / 大小写)→ Mitigation:`openspec change lint` + 手动 `diff <(grep -oE 'name="..."' zh) <(grep -oE 'name="..."' en)` 验证 key 集合完全一致。
- **R7**:Theme 注入 `UserPrefsStore` 增加 `core/prefs` 对 `app/ui/theme` 的依赖(原本 Theme.kt 不依赖 core)→ 与 CLAUDE.md "Theme 单向不依赖业务" 略有冲突,但 `UserPrefsStore` 是基础设施(core/prefs 是 core);Mitigation:UserPrefsStore 接口留在 `core/prefs`,Theme.kt 用 Hilt EntryPoint 拿(参考 R6 SettingsScreen.kt 的 EntryPointAccessor pattern);不走构造参数注入。

## Migration Plan

无外部 schema 迁移。`UserPrefsStore` 是 app-private DataStore,无云端、无导出格式;新增 `animation_style_v1` key 不影响老用户(读不到 key 时回退 MINIMAL)。

部署步骤:
1. Apply change 走 `/opsx:apply animation-system-and-consent-redesign`。
2. `ktlintCheck` + `testDebugUnitTest` + `assembleDebug` 全绿。
3. 真机跑 4 个场景:(a) 切换 4 种风格后 NavHost 过渡节奏对得上;(b) 开启系统 reduce-motion → 风格强制 NONE;(c) 卸载重装 → 首启动走 onboarding 卡片式;(d) 同意页滚到底部 → 同意按钮从 disabled → enabled 有 alpha 过渡。

回滚:`git revert` 该 change 对应 commit,DataStore key 残留不影响下次启动(MINIMAL 是 default)。

## Open Questions

- OQ1:`AnimationStyle` 命名是否要本地化(英文 MINIMAL → 中文"精简")?当前 design 用英文 enum + 双语 string key 映射,符合代码惯例,确认不动。
- OQ2:`parseGroupedMarkdown` 的 `ConsentSection.icon` 映射用关键词硬编码(数据存储 → 📦)还是抽到 `ConsentSectionKind` 枚举 → 当前 design 用关键词硬编码 + `when` 表达式,接受;若未来条款 markdown 大改,再升级。
- OQ3:`AnimationStylePreviewScreen` 的"迷你实时预览"是否要支持 reduce-motion 模式单独预览 NONE → 当前 design 只在 Banner 提示 + 直接切 NONE,接受(简化)。