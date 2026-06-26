## ADDED Requirements

### Requirement: AppNav startDestination is AppShell with My tab

`AppNav.kt` MUST 把 `startDestination` 由 `QuicknoteList` 改为新引入的 `AppShell` route;`AppShell` 是承载底部 tab bar 的容器 Composable(`app/AppShell.kt`),内部嵌入子 NavHost 渲染 `Notes` / `Me` 两个 tab 根屏。

#### Scenario: AppNav startDestination 已切到 AppShell
- **WHEN** grep `AppNav.kt` `startDestination`
- **THEN** 值为 `AppShell`(不是 `QuicknoteList`);`composable<AppShell>` block 存在并渲染 `AppShell(...)`

#### Scenario: Settings 入口迁移到"我的" tab
- **WHEN** grep `AppNav.kt` "navigate(Settings" / `navigate(SettingsData`
- **THEN** 所有 navigate 调用来自 `feature/my/MyScreen.kt`(经 `onNavigate` lambda),**不**来自 `feature/quicknote/list/QuickNoteListScreen.kt`

#### Scenario: widget pending route 回放 popUpTo 锚点已切到 AppShell
- **WHEN** grep `AppNav.kt` `popUpTo(QuicknoteList)`
- **THEN** 0 匹配;`popUpTo(AppShell)` 至少 1 匹配(用于 widget 启动回放与 tab 切换)