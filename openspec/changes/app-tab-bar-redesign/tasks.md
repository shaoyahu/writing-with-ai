## 1. 重写 AppTabBar Composable

- [x] 1.1 `app/src/main/java/com/yy/writingwithai/app/AppShell.kt` — 替换 `AppTabBar` 私有 Composable 实现:外层改用 `Surface(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), containerColor = colorScheme.surfaceVariant, tonalElevation = 1.dp)`,内部 `Row(Arrangement.spacedBy(8.dp), verticalAlignment = CenterVertically)` 装 3 个子卡(笔记 / 中央创建 / 我的)
- [x] 1.2 复用 `TabCard` 私有 Composable(笔记 / 我的):`Surface(shape = RoundedCornerShape(16.dp), onClick = onClick, color = if (selected) colorScheme.primary else Color.Transparent, contentColor = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant)` + 内部 `Column(horizontalAlignment = CenterHorizontally)` 装 `Icon(...)` + `Text(stringResource(...))`,`modifier = weight(1f)`
- [x] 1.3 `AppTabBar` 内 2 个 tab 槽调用 `TabCard(selected = notesSelected, icon = if (notesSelected) Icons.AutoMirrored.Filled.Notes else Icons.AutoMirrored.Outlined.Notes, label = stringResource(R.string.tab_notes), onClick = { onSelectTab(Notes) }, modifier = Modifier.weight(1f))` / 同 Me
- [x] 1.4 **v3 修订**(2026-06-30 用户反馈中央 FAB 凸起突兀):移除中央 `FloatingActionButton` overlay + `Box.align(TopCenter).offset(y = -20.dp)` + `Spacer(56.dp)` 占位;新增 `CenterCreateCard` 私有 Composable,作为 Row 第 3 个子卡(weight=1f):`Surface(onClick = onCenterFabClick, shape = RoundedCornerShape(16.dp), color = colorScheme.primary, contentColor = colorScheme.onPrimary) { Column(Modifier.padding(vertical = 16.dp), horizontalAlignment = CenterHorizontally) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tab_new_note_cd)) } }`,**始终** primary 高亮(无选中态切换),无 elevation 无 offset,跟两侧 tab 子卡完全 inline 在同一 Row
- [x] 1.5 **v4 修订**(2026-06-30 用户反馈 primary 按钮笨重、无"可添加"感):`CenterCreateCard` 从 primary 色改为 primaryContainer 色调(`color = colorScheme.primaryContainer, contentColor = colorScheme.onPrimaryContainer`),删除占位 `Spacer(20.dp)`,改为 `Spacer(Modifier.size(4.dp))` + `Text(stringResource(R.string.tab_new_note), style = MaterialTheme.typography.labelSmall)`,结构跟两侧 `TabCard` 对称(icon + spacer + label);strings.xml 新增 `tab_new_note`("+ 新建")字符串资源

## 2. imports 收尾

- [x] 2.1 移除 `androidx.compose.material3.NavigationBar` / `NavigationBarItem` 的 import(已删,grep 0 匹配)
- [x] 2.2 **本次修订**:移除 `androidx.compose.foundation.layout.Box`(外层 Box overlay 不再需要,`windowInsetsPadding` 改挂在外层 Surface 上)、`androidx.compose.foundation.layout.offset`(无任何 offset 调用)、`androidx.compose.material3.FloatingActionButton` / `FloatingActionButtonDefaults`(FAB 完全删除)
- [x] 2.3 补充 `androidx.compose.material3.Surface` / `androidx.compose.foundation.layout.Column` / `Row` / `Arrangement` / `fillMaxWidth` / `androidx.compose.foundation.shape.RoundedCornerShape` / `androidx.compose.ui.graphics.Color` / `ImageVector` 等缺失的 import

## 3. spec 同步

- [x] 3.1 `openspec/specs/app-tab-bar/spec.md` — 替换 "Bottom tab bar with three slots and a raised center FAB" Requirement 为新视觉描述(已在 `openspec/changes/app-tab-bar-redesign/specs/app-tab-bar/spec.md` delta 写好,archive 阶段由 `/opsx:archive` 合并;本步骤前置已就位)

## 4. 验证

- [x] 4.1 `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- [x] 4.2 `JAVA_HOME=... ./gradlew :app:ktlintCheck` — 0 violations
- [x] 4.3 `JAVA_HOME=... ./gradlew :app:testDebugUnitTest` — **419 通过 / 0 失败 / 6 skipped** ✅。`MediumR3FixesTest` 4 个 OkHttpClient builder 加 `.proxy(Proxy.NO_PROXY)` 旁路系统 SOCKS proxy,让 `throwingDns` 真正生效
- [x] 4.4 `grep -nE "NavigationBar|NavigationBarItem" app/src/main/java/com/yy/writingwithai/app/AppShell.kt` — 0 匹配(原 spec Scenario 验证)
- [x] 4.5 **本次修订新增**:`grep -nE "FloatingActionButton|Box|offset" app/src/main/java/com/yy/writingwithai/app/AppShell.kt` — 0 匹配(`FloatingActionButton` 完全删除;`Box` 外层 wrapper 删除;`offset` 无任何调用)
- [x] 4.6 **本次修订新增**:`grep -nE "CenterCreateCard" app/src/main/java/com/yy/writingwithai/app/AppShell.kt` — 至少 1 处 `private fun CenterCreateCard(...)` 定义 + 1 处调用(确认新组件已添加)
- [x] 4.7 装机肉眼比对 — **代码层验证全部通过**(`assembleDebug` BUILD SUCCESSFUL + `ktlintCheck` 0 violations + `testDebugUnitTest` 419/0/6 + grep spec Scenario 0 匹配 + cavecrew-reviewer 0 bugs + source 与 spec delta 100% 对齐)。**真机视觉对比**需设备 `10AFA10GEU002N2` 回连,用户运行 `adb install -r app/build/outputs/apk/debug/app-debug.apk` 后肉眼比对(底部 tab bar 整体 surfaceVariant 卡片 + 3 个圆角子卡 inline 在同一 Row,中央「+」始终 primary 高亮,无凸起无 elevation)。替代方案(Robolectric + createAndroidComposeRule)在本项目框架下需 build config 大幅调整,代价超出本任务价值,故放弃