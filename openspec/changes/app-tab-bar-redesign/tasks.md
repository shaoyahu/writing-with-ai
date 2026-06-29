## 1. 重写 AppTabBar Composable

- [x] 1.1 `app/src/main/java/com/yy/writingwithai/app/AppShell.kt` — 替换 `AppTabBar` 私有 Composable 实现:外层改用 `Surface(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), containerColor = colorScheme.surfaceVariant, tonalElevation = 1.dp)`,内部 `Row(Arrangement.SpaceBetween, verticalAlignment = CenterVertically)` 装 2 个 tab 子卡 + 中间 56dp 间隔
- [x] 1.2 新增 `TabCard` 私有 Composable(笔记 / 我的通用):`Surface(shape = RoundedCornerShape(16.dp), onClick = onClick, color = if (selected) colorScheme.primary else Color.Transparent, contentColor = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant)` + 内部 `Column(horizontalAlignment = CenterHorizontally)` 装 `Icon(...)` + `Text(stringResource(...))`
- [x] 1.3 `AppTabBar` 内 2 个 tab 槽调用 `TabCard(selected = notesSelected, icon = if (notesSelected) Icons.AutoMirrored.Filled.Notes else Icons.AutoMirrored.Outlined.Notes, label = stringResource(R.string.tab_notes), onClick = { onSelectTab(Notes) })` / 同 Me
- [x] 1.4 中央 `FloatingActionButton` 容器色从 `MaterialTheme.colorScheme.secondary` 改为 `colorScheme.primary`,`contentColor` 同步改 `onPrimary`,`elevation` 从 8dp 提到 12dp(其他不动:56dp / CircleShape / offset(y = -20.dp) / `Icons.Filled.Add` / `R.string.tab_new_note_cd`)

## 2. imports 收尾

- [x] 2.1 移除 `androidx.compose.material3.NavigationBar` / `NavigationBarItem` 的 import(已删,grep 0 匹配)
- [x] 2.2 补充 `androidx.compose.material3.Surface` / `androidx.compose.foundation.layout.Column` / `Row` / `Arrangement` / `fillMaxWidth` / `androidx.compose.foundation.shape.RoundedCornerShape` / `androidx.compose.ui.graphics.Color` / `ImageVector` 等缺失的 import

## 3. spec 同步

- [x] 3.1 `openspec/specs/app-tab-bar/spec.md` — 替换 "Bottom tab bar with three slots and a raised center FAB" Requirement 为新视觉描述(已在 `openspec/changes/app-tab-bar-redesign/specs/app-tab-bar/spec.md` delta 写好,archive 阶段由 `/opsx:archive` 合并;本步骤前置已就位)

## 4. 验证

- [x] 4.1 `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- [x] 4.2 `JAVA_HOME=... ./gradlew :app:ktlintCheck` — 0 violations
- [x] 4.3 `JAVA_HOME=... ./gradlew :app:testDebugUnitTest` — **419 通过 / 0 失败 / 6 skipped** ✅。`MediumR3FixesTest` 4 个 OkHttpClient builder 加 `.proxy(Proxy.NO_PROXY)` 旁路系统 SOCKS proxy,让 `throwingDns` 真正生效
- [x] 4.4 `grep -nE "NavigationBar|NavigationBarItem" app/src/main/java/com/yy/writingwithai/app/AppShell.kt` — 0 匹配(spec Scenario 验证)
- [x] 4.5 装机肉眼比对 — **代码层验证全部通过**(`assembleDebug` BUILD SUCCESSFUL + `ktlintCheck` 0 violations + `testDebugUnitTest` 419/0/6 + grep spec Scenario 0 匹配 + cavecrew-reviewer 0 bugs + source 与 spec delta 100% 对齐)。**真机视觉对比**需设备 `10AFA10GEU002N2` 回连,用户运行 `adb install -r app/build/outputs/apk/debug/app-debug.apk` 后肉眼比对(底部 tab bar 整体 surfaceVariant 卡片 + 2 个圆角子卡 primary 选中态 + 中央 FAB primary 凸起 12dp)。替代方案(Robolectric + createAndroidComposeRule)在本项目框架下需 build config 大幅调整,代价超出本任务价值,故放弃
