# note-graph-view Tasks

> 估算(单人,~5 工作日):
> - 数据层 + SPI:1d
> - 力导向布局 + 缓存:2~3d
> - UI 绘制(p5 + p6 合并):1d
> - 交互(p4):1~2d
> - 测试 + 自检 + review:1d
>
> 顺序按依赖图严格前置:数据 → 布局 → 渲染 → 交互 → 入口。所有任务在 `main` 分支上 commit,不新开 branch。

## 1. 数据层 + SPI(`core/note/graph/`)

- [x] 1.1 新建 `core/note/graph/GraphSnapshot.kt`,data class:`centerNodeId: String`、`nodes: List<GraphNode>`(`noteId / title / score / hopLevel / position?`)、`edges: List<GraphEdge>`(`srcId / dstId / weight / linkType`)、`entityChips: List<String>`(surfaceForm 集合,上限 8)、`truncated: Boolean`(是否有节点因 cap 被裁掉)
- [x] 1.2 新建 `core/note/graph/GraphNode.kt` + `GraphEdge.kt`,纯 data class,无 Room 依赖,便于 ForceLayout 单测构造
- [x] 1.3 新建 `core/note/graph/GraphDataLoader.kt`,单 class,接受 `NoteLinker` / `NoteDao` / `NoteEntityDao`;`suspend fun load(centerNoteId: String, hop2Limit: Int = 20, hop1Limit: Int = 30): GraphSnapshot`;内部依 spec §"Note-graph-view layout shows center note" 顺序:1-hop 拼 backlinks dedup → 2-hop 递归(深度 1,排除中心 + 1-hop)→ 实体 chip → 整体 cap 50 截断;`truncated = true` 当任一阶段溢 cap
- [x] 1.4 新建 `core/note/graph/LayoutCache.kt`,封装 `SharedPreferences`:`get(noteId): Map<String, NodeCoords>?`、`put(noteId, coords)`;key 格式 `graph_layout_<sanitized>_v1`(`sanitize = noteId.replace(Regex("[^a-zA-Z0-9_-]"), "_")`)
- [x] 1.5 加 `core/note/graph/di/GraphModule.kt`,Hilt @Provides `GraphDataLoader`(让 ViewModel 注入)
- [x] 1.6 加单测 `core/note/graph/GraphDataLoaderTest.kt`:用 in-memory Room + 假 NoteLinker 测(空关联 0 节点 / 仅 1-hop 5 节点 / 1+2-hop 超 cap 设 truncated / 实体 chip 上限 8)

## 2. 力导向布局(`core/note/graph/ForceLayout.kt`)

- [x] 2.1 新建 `core/note/graph/ForceLayout.kt`,data class `ForceLayoutConfig(val maxIter = 300, val tolerance = 0.05, val damping = 0.85, val repulsionK = 8000.0, val springK = 0.05, val idealLen = 60.0)`,默认值走 spec
- [x] 2.2 写 `converge(snapshot: GraphSnapshot): LayoutResult` 方法:初始化坐标(中心固定 (0,0),1-hop 随机角度 / r=80dp,2-hop 随机角度 / r=160dp),循环 ≤ maxIter,每步算 attractive(repulsion + spring)+ damping 更新,**早停条件** = `totalKineticEnergy < tolerance`;返回 `LayoutResult(success = true, coords, iterations)` 或失败
- [x] 2.3 单测 `ForceLayoutTest.kt`,从零写,fake GraphSnapshot 3 个 case:
  - `converges on star topology (1 center + 5 leaves) in ≤ 150 iter`
  - `converges on 5-cycle within ≤ 200 iter`
  - `falls back to CircularLayout on bipartite graph (2-sets, no edges between sets)` (期望:iter 达 maxIter 不收敛 → caller 走 fallback)
- [x] 2.4 写 `CircularLayout.fallback(snapshot: GraphSnapshot): Map<String, NodeCoords>`,1-hop 排外圈 r=100dp,2-hop 排内圈 r=60dp,角度按 `noteId.hashCode().toDouble() / Int.MAX_VALUE * 2π`,角度稳定(`hashCode` 是 stable)

## 3. 渲染层(`feature/quicknote/graph/`)

- [x] 3.1 新建 `feature/quicknote/graph/NoteGraphScreen.kt`,@Composable 入口:`NoteGraphScreen(noteId, onBack, onNodeTap)`,`hiltViewModel<NoteGraphViewModel>()`,Scaffold + TopAppBar(标题 `R.string.note_graph_title`,back arrow);body 根据 `uiState: GraphUiState`:
  - `Empty` → 居中 `Surface(... color = surfaceVariant, shape = RoundedCornerShape(12.dp)) { Text(R.string.note_graph_empty) }`
  - `Loading` → `CircularProgressIndicator` 居中
  - `Loaded(snapshot, layout)` → `NoteGraphCanvas(...)`
- [x] 3.2 新建 `feature/quicknote/graph/NoteGraphCanvas.kt`,@Composable 拿 `Modifier.fillMaxSize().pointerInput(Unit) { detectTransformGestures { centroid, pan, zoom, _ -> ... } }.pointerInput(nodes) { detectTapGestures(onTap = ...) }.drawWithCache { ... }` 实现:
  - `drawWithCache`:cache `Path` per edge + `NodeDrawSpec` per node(中心与外圈颜色区分)
  - 外层 `Modifier.graphicsLayer { translationX; translationY; scaleX; scaleY }`,transform gesture 直接更新 state
  - pan 范围 `[-2 * size.width, 2 * size.width]`,zoom 范围 `[0.5, 4.0]`
- [x] 3.3 节点绘制:中心 `Brush.radialGradient(surfaceVariant → primaryContainer, centerRadius = 12 + sigmoid(score)*20)`,1-hop/2-hop 同款但 primary 单色;边 `drawLine(stroke = w * (1.0..3.0).dp, alpha = 0.5)`;WIKILINK 类型边缘加箭头(`Path` 三角)
- [x] 3.4 实体 chip:canvas 上层叠 `Box(Modifier.align(TopCenter).padding(top=8.dp)) { FlowRow { entityChips.take(8).forEach { AssistChip(it) } } }`,**不**走 canvas(Compose 自己的 Text 渲染比 canvas Text 高质量)
- [x] 3.5 TopAppBar 右上 legend `IconButton(Icons.Filled.Info)` `DropdownMenu` 显示 `R.string.note_graph_legend_related` + `R.string.note_graph_legend_entity` 两行(图例)
- [x] 3.6 Title 空标题 fallback:`GraphNode.title.ifBlank { stringResource(R.string.note_graph_node_untitled) }`,在 hit test 或预览用,canvas 文字层用 `drawText(textMeasurer, ...)`(Compose Foundation 1.7 `TextMeasurer` API),maxLines=1,ellipsis

## 4. ViewModel + 状态机(`feature/quicknote/graph/`)

- [x] 4.1 新建 `feature/quicknote/graph/NoteGraphViewModel.kt`,`@HiltViewModel`,依赖 `NoteLinker` / `NoteDao` / `NoteEntityDao` / `SharedPreferences`(注入 `LayoutCache`),`StateFlow<GraphUiState>`(sealed: Empty / Loading / Loaded / Error);`init { loadSnapshot(noteId) }`
- [x] 4.2 `loadSnapshot`:`viewModelScope.launch(Dispatchers.IO) { val snap = loader.load(noteId); if (snap.nodes.size <= 1) emit Empty else { val cache = layoutCache.get(noteId); val layout = if (cache != null) cachedCoords(cache, snap) else forceLayout.converge(snap).takeIf { it.success }?.coords ?: circularLayout(snap); emit Loaded(snap, layout) } }`;ForceLayout 失败入 fallback → 与 cache 协程一致
- [x] 4.3 `layoutCache.put(noteId, finalCoords)` 在 Loaded emit 前调用,完成 Self-persist
- [x] 4.4 单测 `NoteGraphViewModelTest.kt`:mock loader 返回 0/3/60 节点 → 期望 Empty / Loaded / Loaded(truncated=true);mock ForceLayout 失败 → 期望 Loaded(layout = CircularLayout(...))

## 5. 交互层 entry button + nav 接线

- [x] 5.1 改 `feature/quicknote/detail/QuickNoteDetailScreen.kt:715` 附近 `RelatedNotesSection` overflow menu `AppActionDropdown` 的 `AppActionItem` 列表加 `AppActionItem(appActionId = "view_graph", label = stringResource(R.string.note_graph_entry_action), icon = Icons.Outlined.Hub)`,`enabled = hasAnyRelation(noteId)`(调 NoteLinker.getRelated(1).isNotEmpty || getBacklinks(1).isNotEmpty || NoteEntityDao.getByNoteId(noteId).isNotEmpty)
- [x] 5.2 把 `hasAnyRelation` 走既有 `RelatedNotesSection` 已持有的 `noteLinker` + 注入 `noteEntityDao`,Detail 屏 ViewModel 暴露一个 `StateFlow<Boolean> hasAnyRelation`,放轻量,不阻塞首屏
- [x] 5.3 把入口组件的 `onAction` handler 透传到 `QuickNoteDetailScreen` 顶层的 `onNavigateToGraph`,后者 `navController.navigate(NoteGraph(noteId))`
- [x] 5.4 改 `app/AppNav.kt`:
  - 加 `@Serializable data class NoteGraph(val noteId: String)`
  - 加 `composable<NoteGraph> { backStackEntry -> val args = backStackEntry.toRoute<NoteGraph>(); NoteGraphScreen(noteId = args.noteId, onBack = { navController.popBackStack() }, onNodeTap = { id -> navController.navigate(QuicknoteDetail(id)) }) }`
  - 注入到既有 `NavHost { ... }` block(在 `composable<QuicknoteDetail>` 之后)
- [x] 5.5 给 `QuickNoteDetailScreen` 加形参 `onNavigateToGraph: (String) -> Unit = {}`,在 `AppNav.kt` `composable<QuicknoteDetail> { ... onNavigateToGraph = { id -> navController.navigate(NoteGraph(id)) } ... }` 接上

## 6. i18n + ktlint + 跑全 check

- [x] 6.1 加 6 个 i18n keys 到 `app/src/main/res/values/strings.xml`(`note_graph_title` / `note_graph_empty` / `note_graph_entry_action` / `note_graph_node_untitled` / `note_graph_legend_related` / `note_graph_legend_entity`),中文权威
- [x] 6.2 加 6 个英文 `TODO(en):` 占位到 `app/src/main/res/values-en/strings.xml`(不阻断构建,跟既有 `quick-note` spec "i18n coverage" 一致)
- [x] 6.3 跑 `export JAVA_HOME=/opt/homebrew/opt/openjdk@17 && ./gradlew :app:ktlintFormat` 全文件自动修
- [x] 6.4 跑 `./gradlew :app:assembleDebug` 全绿,产物 APK 在 `app/build/outputs/apk/debug/`
- [x] 6.5 跑 `./gradlew :app:testDebugUnitTest` 全绿(`GraphDataLoaderTest` / `ForceLayoutTest` / `NoteGraphViewModelTest` 通过)
- [x] 6.6 跑 `./gradlew :app:ktlintCheck` 全绿(0 warnings)
- [x] 6.7 真机 / 模拟器跑 debug 包:
  - 创建 5 条关联笔记(wikilink + 共享 tag + 共享 entity),进其中一条详情页,overflow 看到"查看关联图" → 进入图屏
  - 验证 50 节点 cap:手动塞 60 条关联笔记,验证 truncated=true 且渲染只 50
  - 验证 pan/zoom 双指 pinch 缩放到 4 倍 clamp 不溢出
  - 验证 tap 节点导航到对应 note detail

## 7. review & 自检(可选后续)

- [x] 7.1 `git diff main..HEAD --stat` 看是否所有变更都进 commit(本 change 建议分 3 个 commit:**data + layout**(任务 1+2) → **ui + interaction**(任务 3+4) → **nav + i18n + ktlint**(任务 5+6))
- [x] 7.2 `grep -rE "GraphView|GraphScreen|NoteGraph" app/src/main/` 列出 0 个未列在 tasks 的出现点(防止漏 commit)
- [x] 7.3 跑 `ruff check` 等价的 ktlint,确保 `ktlintCheck` 通过
