## MODIFIED Requirements

### Requirement: Privacy policy rendered as Markdown with scroll-to-bottom unlock

`OnboardingScreen` MUST 从 `assets/privacy_policy_<lang>.md` 读取条款文本(系统语言为英文时读 `en`,其他读 `zh`),用 `parseGroupedMarkdown` 按 H2 分组后渲染为 `ConsentSectionCard` 卡片列表(每张卡片对应一个 H2 section),并在卡片列表上方显示 `ConsentProgressBar`(滚动比例 0→1),底部显示 `ConsentBottomBar`;"同意"按钮在用户**未实际滚动过内容** 时 MUST 处于 `enabled = false` 状态(`firstVisible > 0` + `lastVisible >= total - 1` 双条件,或滚动比例 `progress >= 0.999f`,OR 关系,避免短文一键同意);滚动到底部时启用。

#### Scenario: 加载中文条款 — 5 张卡片
- **WHEN** 系统语言为中文,`OnboardingScreen` 初始化
- **THEN** 读 `assets/privacy_policy_zh.md`;调 `parseGroupedMarkdown(text, summaryResolver)` 产出 5 个 `ConsentSection`(`数据存储` / `AI 功能与数据流` / `第三方 AI provider 列表` / `如何撤回同意` / `联系方式`);渲染 5 张 `ConsentSectionCard`,首张 `expanded=true` 其余 `expanded=false`;`ConsentProgressBar` 显示 0 进度;`ConsentBottomBar` accept 按钮 disabled

#### Scenario: 加载英文条款 — 5 张卡片
- **WHEN** 系统语言为英文,`OnboardingScreen` 初始化
- **THEN** 读 `assets/privacy_policy_en.md`;调 `parseGroupedMarkdown` 产出 5 个 `ConsentSection`;渲染 5 张 `ConsentSectionCard`;`ConsentProgressBar` 显示 0 进度;`ConsentBottomBar` accept 按钮文案 `stringResource(R.string.consent_accept)`,disabled

#### Scenario: 滚动到底部解锁(双条件 OR)
- **WHEN** 用户滚动 `LazyColumn` 且 (`firstVisibleItemIndex > 0` 且 `lastVisibleItemIndex >= totalItemsCount - 1`) 或 `scrollProgress >= 0.999f`
- **THEN** "同意"按钮 `enabled = true`,`ConsentBottomBar` 走 `tween(300)` containerColor 过渡到 `colorScheme.primary`;`ConsentProgressBar` 进度 1.0
- **AND** 否则 "同意"按钮 `enabled = false`(无法点击)

#### Scenario: 卡片展开/折叠
- **WHEN** 用户点击某张 collapsed `ConsentSectionCard` 头部
- **THEN** 该卡片 `expanded=true`,内容区用 `AnimatedVisibility` + `LocalAnimationTokens.current.expandSpec` 平滑展开
- **AND** 再次点击切换回 `expanded=false`,用 `collapseSpec` 折叠

#### Scenario: 同意后写入 ConsentStore
- **WHEN** 用户点击"同意"按钮(`enabled = true` 时)
- **THEN** `OnboardingViewModel.accept()` 调用 `ConsentStore.setAccepted(version=CURRENT_CONSENT_VERSION, at=now)`;`AppNav` 监听到 `consentAccepted = true` → `popUpTo(0) { inclusive = true }` + navigate 主路由(若 MainActivity.pendingRoute 存在,优先 navigate widget route)

#### Scenario: 政策加载失败回退
- **WHEN** `assets/privacy_policy_<lang>.md` 加载抛 IOException
- **THEN** `OnboardingScreen` 显示 `stringResource(R.string.onboarding_policy_load_failed)` 单段 Text;`ConsentBottomBar` 仍渲染(accept disabled, decline enabled,合规拒绝流程不可被灰显阻塞)
