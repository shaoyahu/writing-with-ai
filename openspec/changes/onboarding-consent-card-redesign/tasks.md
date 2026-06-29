## 1. OnboardingScreen UI 改写

- [x] 1.1 `feature/onboarding/OnboardingScreen.kt`:`loadPrivacyPolicy` 旁加 `loadPrivacyPolicyOrNull(context): String?`,失败返回 null(替换原 `getOrElse { e.javaClass.simpleName + ... }` 假成功路径)
- [x] 1.2 主体改写:`val policy = remember { loadPrivacyPolicyOrNull(context) }`;`policy == null` 走 fallback 路径(只显示 `R.string.onboarding_policy_load_failed` + 旧 `ConsentBottomBar` 仍渲染);非 null 走新版卡片路径
- [x] 1.3 新版卡片路径:`val sections = remember(policy) { parseGroupedMarkdown(policy, summaryResolver) }`;`summaryResolver` 闭包按 H2 关键词匹配 5 个 `consent_section_*_summary` stringRes
- [x] 1.4 展开状态:`val expandedSet = remember { mutableStateOf(setOf(0)) }`;首张默认 expanded
- [x] 1.5 滚动比例:`val scrollProgress by produceState(0f, listState) { snapshotFlow { ... }.collect { ... } }`;公式见 design D3
- [x] 1.6 LazyColumn 内部 `items(sections)` 渲染 `ConsentSectionCard(section, expanded = section.id in expandedSet, onToggle = { ... })`
- [x] 1.7 进度条挂载:`ConsentProgressBar(progress = scrollProgress)` 放在品牌头部下、卡片列表上
- [x] 1.8 底部栏挂载:`ConsentBottomBar(scrolledToBottom, onAccept, onDecline)` 替换旧 `Button` + `OutlinedButton` 拼装
- [x] 1.9 滚动解锁逻辑保留:`canAccept` 双条件 OR(`firstVisible > 0 && lastVisible >= total - 1` ∥ `scrollProgress >= 0.999f`);`LaunchedEffect(canAccept.value)` 调 `onScrolledToBottomChange` 通知 ViewModel

## 2. 失败回退路径

- [x] 2.1 `policy == null` 时,`OnboardingScreen` 主体 `Column` 仍渲染品牌头部 + `ConsentProgressBar(0f)` + 单段 `Text(stringResource(R.string.onboarding_policy_load_failed))` + `ConsentBottomBar(scrolledToBottom=false, onAccept, onDecline)`;accept 按钮 disabled,decline 按钮 enabled
- [x] 2.2 验证 fallback 路径下,decline 仍触发 `OnboardingViewModel.reject()` → `Action.ExitApp`(`OnboardingScreen` 把 `onDecline` 透传给 `ConsentBottomBar`,decline 始终 enabled;`OnboardingViewModel.reject()` 路径不动)

## 3. JVM 单测

- [x] 3.1 新建 `app/src/test/java/com/yy/writingwithai/feature/onboarding/OnboardingScreenIntegrationTest.kt`
- [x] 3.2 测 1:`parseGroupedMarkdown(policy_zh, summaryResolver)` 返回 5 个 `ConsentSection`,`sectionId` 0~4
- [x] 3.3 测 2:`summaryResolver` 命中 5 个 stringRes:数据/AI/第三方/撤回/联系
- [x] 3.4 测 3:`computeScrollProgress(firstVisibleItemIndex=4, offset=0, avgItemSize=200f, totalItems=5)` 返回 1.0
- [x] 3.5 测 4:`computeScrollProgress(firstVisibleItemIndex=0, offset=0, avgItemSize=200f, totalItems=5)` 返回 0.0
- [x] 3.6 测 5:`computeScrollProgress` totalItems=1 返回 0.0(分母 0 保护)
- [x] 3.7 测 6:`loadPrivacyPolicyOrNull(contextWithNoAssets)` 返回 null(用 mockk 模拟 assets.open 抛 IOException)

## 4. 收口

- [x] 4.1 `./gradlew :app:assembleDebug` 全绿
- [x] 4.2 `./gradlew :app:ktlintCheck` 全绿
- [x] 4.3 `./gradlew :app:testDebugUnitTest` 全绿(含新增 7 个 OnboardingScreenIntegrationTest 用例,含 en policy 额外 case)
- [x] 4.4 验证 `res/values/strings.xml` 与 `res/values-en/strings.xml` 的 `consent_*` / `consent_section_*` / `onboarding_policy_load_failed` key 集合双侧完全一致(实际 10 key,设计预估 14,双侧 diff 空)
- [x] 4.5 `openspec/changes/animation-system-and-consent-redesign/tasks.md` 9.1 标记从 "[x]" 改为 "[x]"(确认完整),或在本 change 归档时同时关闭该 task
- [x] 4.6 `docs/progress.md` 顶部追加本 change 收口条目
