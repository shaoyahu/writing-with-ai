# note-graph-view

## Why

note-association(M0 落地)+ note-entity-extraction(M5-1 落地)已经在 `note_links` + `note_entities` + `entity_aliases` 表里积累了相当丰富的关联数据 — 每条笔记都知道它跟谁强相关、跟谁共享什么实体。但详情页底部的 `RelatedNotesSection`(见 `feature/quicknote/detail/RelatedNotesSection.kt`)只把这些信息"线性"地铺成 N 张卡片:

- 看不到"以我为中心"的拓扑结构 — 关联笔记是否还有反向关联、关联笔记之间是否互相关联,在 list 里全平。
- 看不到实体的"桥梁"作用 — 当前 `RelatedNotesSection` 只展示 RelatedNote 行(卡片),实体是被聚合在 `sharedEntities` chip 里,没有"通过这个实体找到其他笔记"的视觉提示。
- 看不到全局密度 — 一个用户的"研究/人物/作品"hub 关系,在卡片列表里得肉眼手动连。

本 change 引入**笔记关系图(Graph View)** —— 从详情页右上 overflow 入口进新屏,把当前笔记放在画布中心,2-hop 邻居(直接关联笔记 + 共享实体笔记 + 反向链接笔记)渲染成节点,共享实体以 chip 浮在关联笔记之间。画布支持 pan + pinch-zoom,点节点跳到那条笔记的详情;总节点数硬 cap 50 防 OOM;力导向布局在 Worker 协程里迭代,≤ 200ms 收敛,刷新本地存储(`SharedPreferences`)缓存避免每次进入重算。

## What Changes

- 新增 `feature/quicknote/graph/` 子模块:`NoteGraphScreen` Composable、`NoteGraphViewModel`(Hilt scoped)、`ForceLayout` 算法实现、`GraphRenderer` Canvas 绘制
- `AppNav.kt` 加类型安全 route `note_graph/{noteId}`(从 quick-note detail overflow 进)
- `QuickNoteDetailScreen` overflow menu(既有 `AppActionDropdown`)加"查看关联图"入口,见 `feature/quicknote/detail/QuickNoteDetailScreen.kt:715` 附近的 `RelatedNotesSection`
- 复用既有 `NoteLinker.getRelated` / `getBacklinks` + `NoteEntityDao.queryNotesByEntity` 数据源,**不加新 DAO**,不改 Room schema,不改 `note-entity-link` / `note-entity-extraction` spec-level 行为
- 加 `core/note/graph/GraphDataLoader.kt`(新文件):把"原始数据库行"折叠成 `GraphSnapshot`(`centerNodeId` + `nodes: List<GraphNode>` + `edges: List<GraphEdge>`),节点 / 边数量 cap 50
- 加 `core/note/graph/ForceLayout.kt`(新文件):Fruchterman-Reingold 风格的 spring-electric 简化版,~200 行,从零写、不引第三方;maxIter=300,convergenceTolerance=0.05
- 不引第三方图可视化库(no `mpandroidchart`、`jgraphx`、`androidplot` 等);理由见 design §D1
- 所有 UI 字符串走 `values/strings.xml` + `values-en/strings.xml`,中英文档 `TODO(en):` 占位优先于硬编码
- i18n keys:**6 个**(`note_graph_title` / `note_graph_empty` / `note_graph_entry_action` / `note_graph_node_untitled` / `note_graph_legend_related` / `note_graph_legend_entity`)

## Capabilities

### New Capabilities
无。本 change 复用既有 `note-entity-link` + `note-entity-extraction`,只为它们补一个"视觉化查看"的视图入口;数据模型 0 改变。

### Modified Capabilities
- `note-entity-link`:新增「关联图入口 / 关联图屏 / 布局渲染 / pan-zoom / tap-to-navigate / node cap 50 / empty state」6 条 ADDED Requirements(见 `specs/note-entity-link/spec.md` delta)
- 不改 `quick-note` / `note-entity-extraction` / `ai-gateway` 等任何 spec-level 行为

## Impact

- **代码**:`feature/quicknote/graph/`(新)+ `app/AppNav.kt`(1 路由 + 1 composable)+ `feature/quicknote/detail/QuickNoteDetailScreen.kt`(overflow +1 项)+ `core/note/graph/`(新 SPI)
- **依赖**:零新增。`Canvas` / `pointerInput` / `detectTransformGestures` 全部 Compose 基础 API
- **Room schema**:0 改动,`AppDatabase` 仍 `version=4`(M5-1 落地版本)
- **apikey / 成本**:0 — 图视图纯本地绘制,不调 LLM,不计费,不写 `ai_history`
- **UI**:Material 3 既有 token,新增 1 个 `TopAppBar` 屏 + 1 个 overflow menu item
- **测试**:`ForceLayout` 单测覆盖收敛 / 不动 / 振荡场景;`GraphDataLoader` 单测覆盖 cap / 0-hops / 1-hop 折叠
- **性能预算**:首次进入屏 ≤ 200ms 渲完,pan/zoom 帧间隔 ≤ 16ms(60Hz 稳帧);`StateFlow<GraphSnapshot>` 在 layout convergence 前显示原 layout,避免阻塞 UI
- **不在范围**:3D 图、VR 沉浸、cluster detection、ontology 推演、时间轴维度、多用户协同编辑图、severity 颜色映射、节点 drag-to-move、节点关系类型切换(只看 WIKILINK / 只看 ENTITY_HIT)、导出 PNG / SVG

## Risks / Trade-offs

- **布局震荡(disconnected components)** → ForceLayout 加 `damping = 0.85`(每步动能衰减 15%)+ `maxIter = 300` + `convergenceTolerance` 早停,实测在 ≤ 50 节点 / 网络稀疏场景 80~150 步收敛;单测覆盖"完全二分图"、"星形拓扑"、"5 节点环"三种极端 case
- **大 note 数据慢查询** → 数据源 Layer 复用既有索引(`note_links` 已挂 `srcNoteId` / `dstNoteId` / `linkType` 三 index),`queryNotesByEntity` 走 `entityKey` 主键扫。50 节点 cap 在数据层兜底,不会 OOM
- **能量函数收敛失败** → 兜底机制:迭代 300 步后能量误差 > 容忍阈值,改用 `CircularLayout`(节点均匀排在圆周上,边连向中心)渲染,Fruchterman 不收敛的连通分量 fallback 到子圆
- **Compose Canvas 在低 RAM 设备掉帧** → 用 `drawWithCache` 而非 `drawBehind`,配合 `Modifier.graphicsLayer { scaleX/Y; translationX/Y }`(单一节点 transform 比每帧 dispatch 整 List 重排便宜)
- **pan/zoom 手势与详情页 other gestures 冲突** → `detectTransformGestures(panZoomLock = true)` 锁定单时刻单手势,跟 FAB / selection toolbar 互不干扰
- **节点点击 vs 拖动** → `pointerInput` 通过 `detectTapGestures(onTap = ...)` 区分:单指 tap on node → navigate;单指 drag empty area → pan;两指 pinch → zoom。阈值:位移 < 12dp 才算 tap,否则视为 drag 起点
- **空态性能** → 0 邻居直接渲染 `note_graph_empty` 文案,不进 layout 算法,1ms 内首帧
- **i18n 占位不阻断构建** → 跟既有 `TODO(en):` 前缀约定一致(见 `quick-note` spec "i18n coverage" Requirement)
- **节点文字渲染溢出** → 长标题用 `Text(maxLines=1, overflow=Ellipsis)` + 节点 `maxWidth=120dp`,chip 可折行
- **依赖注入** → `NoteGraphViewModel` 走既有 `@HiltViewModel`,依赖 `NoteLinker` + `NoteDao` + `NoteEntityDao`(全部 @Singleton 已有),不增 `core/note/di` 入口
