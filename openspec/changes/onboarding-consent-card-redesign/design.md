## Context

`animation-system-and-consent-redesign` 设计的"卡片式条款页"已具备全部组件和解析器,但 OnboardingScreen.kt 仍走老路径(`parseSimpleMarkdown` + `MarkdownBlockView` 平铺),新组件只落盘未被消费。task 9.1 在原 change 任务列表里被误标 [x],实际工作未做。本 change 兑现该 task,把"卡片式 + 进度条 + 动画底部栏"端到端接进 `OnboardingScreen`。

CLAUDE.md "v1 备份策略" + AndroidManifest `allowBackup="false"` 硬规则与本 change 无关;但 CLAUDE.md "字符串一律走 strings.xml" 硬规则需遵守:5 张卡片标题来自 privacy policy md H2(动态文本),摘要走 5 个 `consent_section_*_summary` stringRes。

CLAUDE.md §"包结构": `feature/` 必须自包含,跨 feature 不互相 import;`OnboardingScreen` 只引用本 feature 的 `SimpleMarkdown.kt` / `ConsentSectionCard.kt` / `ConsentProgressBar.kt` / `ConsentBottomBar.kt` + `R.string.*`,不跨 feature 引用。

## Goals / Non-Goals

**Goals:**
- `OnboardingScreen.kt` 内部重写为卡片式 UI,函数签名 0 改动(`scrolledToBottom` / `onScrolledToBottomChange` / `onAccept` / `onReject`)
- 滚动解锁逻辑保留:`listState.layoutInfo` 检查 `firstVisible > 0 && lastVisible >= total - 1` 双条件(防短文一键同意),与 `animation-system-and-consent-redesign` 9.2 对齐
- 首张卡片默认 expanded,其余 collapsed;支持多张同时展开(用户可比较不同 section)
- 进度条 `ConsentProgressBar(progress)` 接收 0→1 滚动比例,内部 `animateFloatAsState` 接 `LocalAnimationTokens.current.listItemSpec`
- 底部栏 `ConsentBottomBar` 接 `scrolledToBottom` 控制 accept 按钮的 `tween(300)` containerColor 过渡
- 政策加载失败回退到 `R.string.onboarding_policy_load_failed`(即使加载失败也保留可滚/可拒绝,合规拒绝流程不可被灰显阻塞 — 拒绝按钮始终 enabled)
- 1 个新增 JVM 单测文件覆盖端到端集成路径

**Non-Goals:**
- 不改 `OnboardingViewModel` / `OnboardingRoute` / `ConsentStore` / 隐私 policy md
- 不改 `OnboardingScreen` 函数签名(防 Route 链断)
- 不重新设计 `ConsentSectionCard` / `ConsentProgressBar` / `ConsentBottomBar` / `parseGroupedMarkdown`(组件已稳定)
- 不引入新依赖(纯 Compose 已有 API)
- 不动 ktlint 配置 / 不动 release preflight

## Decisions

### D1: 卡片展开状态用 `remember { mutableStateOf(setOf(0)) }` (Set<Int>),非 `mutableStateOf(0)`

**选 Set**:支持多卡片同时展开(用户可比对 "数据" 和 "AI" 两段),符合 spec §8.1 "整卡可点击切换展开" 描述。

**否决单个 Int**:用户体验受限;UI 层复杂度无显著增加(`sectionId in expandedSet` 一行)。

### D2: `summaryResolver` 闭包放在 `OnboardingScreen` 内部,不上 ViewModel

**选 Composable 内部闭包**:摘要 stringRes 解析与 UI 强耦合(关键字匹配是 i18n 文案决定的,不是业务逻辑),放 ViewModel 等于把 string key → resId 映射从 UI 层搬到非 UI 层,反而要新增 `resId: Int` 类型穿透,得不偿失。

**替代方案**:在 `OnboardingViewModel` 暴露 `Map<String, Int> sectionSummaries`。否决:ViewModel 不应依赖 `R.string.*`(测试桩 + 模块边界);`OnboardingViewModel` 已存在且稳定,本 change 不动它。

### D3: 滚动比例导出走 `produceState` + `snapshotFlow` 双层

**选双层**:
- 内层:`remember(listState) { snapshotFlow { listState.layoutInfo } }` 监听 layout 变化
- 外层:`produceState(0f, listState) { snapshotFlow { listState.firstVisibleItemScrollOffset }.collect { ... } }` 计算比例
- 计算公式:`progress = (firstVisibleItemIndex + offset / avgItemSize) / max(total - 1, 1).toFloat()`

**替代方案**:直接 `derivedStateOf { ... }` + `LaunchedEffect` 推 `MutableStateFlow<Float>`。否决:重复样板多,`produceState` 已经是 `snapshotFlow → State` 官方范式。

### D4: 失败回退用 `null` 标志位而非异常路径

**选 null**:在 `loadPrivacyPolicy` 旁加 `loadPrivacyPolicyOrNull(context): String?`,失败返回 null;`OnboardingScreen` 用 `val policy = remember { loadPrivacyPolicyOrNull(context) }`,`policy == null` 走 fallback 路径(纯 Text 显示 `onboarding_policy_load_failed`,按钮仍可点拒绝)。

**替代方案**:保留 `loadPrivacyPolicy` 返回 `getOrElse { e.javaClass.simpleName + ... }` 的"假成功"路径(原 174-178 行)。否决:旧路径把异常当内容返回,用户看到 "IOException: ..." 误导,新路径显式区分"成功" / "失败"。

### D5: progressBar 进度到 0.999 视为 1.0,触发 scrolledToBottom

**阈值**:`progress >= 0.999f`(与 `lastVisible >= total - 1` 二选一,OR 关系;两者任一为真即视为到底)。

**选双条件 OR**:LazyColumn 内部 items 数 ≤ 1 时进度公式分母为 0,纯走 `lastVisible` 路径;items 数多时进度公式更准(用户停在倒数第二项但 offset 滚到底也算到底)。

## Risks / Trade-offs

- **[R1] `produceState` 在 Robolectric 测不稳定** → 单测不直接挂 Composable,改用纯函数测试 `parseGroupedMarkdown` + `summaryResolver` + 滚动比例计算逻辑(抽 `internal fun computeScrollProgress(layoutInfo, itemSize): Float`)。
- **[R2] 新版 LazyColumn items 数少(只有 5 张) → progress 公式平均 itemSize 误差大** → D5 走 OR 逻辑,`lastVisible >= total - 1` 主判,`progress` 仅作可视化;短文(只有 1 张) 时 `lastVisible >= total - 1` 直接 true。
- **[R3] Compose `produceState` key 列表漏 `listState` 会漏更新** → task 设计时把 `listState` 作为 `produceState` 第二参数传入,key 变化时重启 effect。
- **[R4] `parseGroupedMarkdown` 当前 summary==null 时跳过整段** → D2 闭包命中 5 个关键词,运行时不会有 null,但保留 null 路径(失败时该 section 不渲染,符合"摘要缺失就不显示该卡片"语义)。
