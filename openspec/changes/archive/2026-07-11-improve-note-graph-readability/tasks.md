## 1. strings.xml 资源

- [x] 1.1 在 `app/src/main/res/values/strings.xml` 新增 6 个字符串: `note_graph_header_node_count_fmt`(`%1$d 个节点 · %2$d 条关联`)、`note_graph_header_node_count_singular`(`1 个节点 · %2$d 条关联`)、`note_graph_guidance_banner`(完整引导文案)、`note_graph_empty_actionable`(空态可执行文案)、`note_graph_empty_actionable_title`(空态标题,如 "还没有关联笔记")、`note_graph_subtitle_separator`(`·`,用于拼接)。

## 2. NoteGraphCanvas.kt 渲染层改造

- [x] 2.1 在 `drawGraph` 入口新增 `nodeLayoutCache: Map<String, NodeLayout>` 局部变量,基于 `snapshot.nodes` + `coords` + 邻居集合计算每个节点的渲染位置 + 标签方向 + 标签 box 矩形;放在 edges / nodes 循环之前,避免每边 / 每节点重复算。n > 100 打 `TODO(perf)` 注释。
- [x] 2.2 替换 `edgeColor`:light scheme 改用 `onSurfaceVariant.copy(alpha=0.6f)`,dark scheme 用 `outlineVariant`。在 `NoteGraphCanvas` composable 入口用 `isSystemInDarkTheme()` 派生 `isDark`,然后传给 `drawGraph`。
- [x] 2.3 替换边 `strokeWidth`:`2.5f + edge.weight.coerceIn(0f, 1f) * 3f`;`pathEffect` 逻辑保持(WIKILINK 实线 / 其它虚线)。
- [x] 2.4 实现 4 方向标签碰撞避让(decision D1):在 `nodeLayoutCache` 计算时,对每个节点算 right / left / above / below 四方向的 label box,跟邻居节点(距离 < `2*(r_self + r_other + labelW)`)的 box 做相交检查;选首个不重叠方向;全重叠选最近邻居夹角最大方向(右优先)。新增私有 `private fun pickLabelDirection(...)`。
- [x] 2.5 在 `drawGraph` 的 nodes 循环里,用 `nodeLayoutCache[node.noteId]!!.labelBox` 替换原来固定的 `center.x + radius + 4f, center.y + radius * 0.5f` 偏移;label 截到 12 字符 + `…` 逻辑保留。

## 3. NoteGraphScreen.kt UI 改造

- [x] 3.1 TopAppBar `title` slot 改 `Column`:第一行 `关联图`(`titleLarge`);第二行根据 `snapshot.nodes.size` / `snapshot.edges.size` 选 `note_graph_header_node_count_singular` 或 `note_graph_header_node_count_fmt`(`labelSmall`、`onSurfaceVariant` 颜色)。
- [x] 3.2 Loaded 态下,如果 `snapshot.nodes.size <= 2`,在 Box 内 `Alignment.BottomCenter` 叠 GuidanceBanner Composable(放在 `EntityChipOverlay` 之前,后者条件渲染跳过)。`GuidanceBanner` 走 Surface(`surfaceContainerHigh` + 12dp 圆角 + `padding(horizontal=16dp, vertical=12dp)`),文本 `note_graph_guidance_banner`,`labelMedium`。
- [x] 3.3 `EmptyGraphBlock` 改造:Column 内顺序 Icon(`Icons.Filled.AccountTree`,48dp,`onSurfaceVariant` 着色)+ Text(`note_graph_empty_actionable_title`,`titleMedium`)+ Text(`note_graph_empty_actionable`,`bodyMedium`,居中,`onSurfaceVariant` 颜色,`padding(top=8dp)`),整体水平居中、`padding(24dp)`。

## 4. 构建与静态检查

- [x] 4.1 跑 `./gradlew :app:assembleDebug` 确认编译通过;失败则按 build error 自修。
- [x] 4.2 跑 `./gradlew :app:ktlintCheck`;失败先跑 `ktlintFormat`,残留 ktlint 错误自修(用 `// ktlint-disable` 兜底需要带 reason 注释)。
- [x] 4.3 跑 `./gradlew :app:testDebugUnitTest` 确认既有单测不破。

## 5. 真机目视验收

- [ ] 5.1 在 1080×2400 模拟器上构建并安装 debug APK;打开任一带 1-hop 邻居的笔记 → 进入关联图,截图记录:
  - TopAppBar 副标题正确显示(`X 个节点 · Y 条关联`)
  - 中心 + 1-hop 节点的标签不再互相压字
  - light scheme 下边清晰可辨
- [ ] 5.2 切 dark theme(系统设置 → 暗色模式),重新进关联图,截图记录 dark scheme 下边可见度 + 标签碰撞效果。
- [ ] 5.3 进入任一带 0 个 1-hop 邻居的笔记,截图记录:
  - Loaded 态下引导 banner 出现在底部,entity chips 不再叠加
  - 节点数 = 2 时 banner 显示;节点数 > 2 时 banner 不显示
- [ ] 5.4 进入任一带 0 节点空态的笔记(可临时清空 `GraphRepository` 缓存),截图记录空态 Icon + 文案渲染正常。

## 6. 归档

- [x] 6.1 跑 `openspec archive --change improve-note-graph-readability`(或走 `/opsx:archive` skill),归档到 `openspec/changes/archive/2026-07-12-improve-note-graph-readability/`,所有 tasks 标记 `[x]`。
- [x] 6.2 在 `docs/progress.md` 追加一行:M5.x 关联图可读性升级(line count ≤ 3)。