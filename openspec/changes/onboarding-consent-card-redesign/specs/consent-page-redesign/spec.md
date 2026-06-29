## MODIFIED Requirements

### Requirement: OnboardingScreen composes ConsentSectionCard list

`OnboardingScreen` MUST 使用 `parseGroupedMarkdown` 把 privacy policy 按 H2 切片,并对每段渲染一个 `ConsentSectionCard`(不是裸 `MarkdownBlockView` 平铺);卡片首张默认 expanded,其余 collapsed;支持用户点击切换任一卡片 expanded 状态(多张可同时展开)。

#### Scenario: 5 H2 sections render 5 ConsentSectionCard items
- **WHEN** `parseGroupedMarkdown` 产出 5 个 `ConsentSection`
- **THEN** `OnboardingScreen` 渲染 5 个 `ConsentSectionCard`,layout 顺序与 policy md 中 H2 出现顺序一致

#### Scenario: First card default expanded
- **WHEN** `OnboardingScreen` 首次 compose
- **THEN** `expandedSet = setOf(0)`,第 0 张 `ConsentSectionCard(expanded=true)`,第 1~4 张 `expanded=false`

#### Scenario: Card tap toggles expand
- **WHEN** 用户点击第 2 张 collapsed 卡片头部
- **THEN** `expandedSet` 增 2;`ConsentSectionCard(id=2, expanded=true)` 渲染展开内容;第 0 张保持 expanded(支持多张同展)

### Requirement: OnboardingScreen mounts ConsentProgressBar + ConsentBottomBar

`OnboardingScreen` MUST 在卡片列表上方挂 `ConsentProgressBar(progress: Float)`,在卡片列表下方挂 `ConsentBottomBar(scrolledToBottom: Boolean, onAccept, onDecline)`;不再使用裸 `Button` + `OutlinedButton` + `Text` 拼装的旧底部栏。

#### Scenario: ConsentProgressBar mount point
- **WHEN** `OnboardingScreen` compose
- **THEN** `ConsentProgressBar` 渲染位置 = 品牌头部下方、卡片列表上方(`spacer + progress`);progress 值来自 `listState.firstVisibleItemIndex + offset / avgItemSize / max(total - 1, 1)` 计算

#### Scenario: ConsentBottomBar mount point
- **WHEN** `OnboardingScreen` compose
- **THEN** `ConsentBottomBar` 渲染位置 = 卡片列表下方、screen 底部(走 `navigationBarsPadding`);`scrolledToBottom` 参数与旧版 `scrolledToBottom` state 同源
