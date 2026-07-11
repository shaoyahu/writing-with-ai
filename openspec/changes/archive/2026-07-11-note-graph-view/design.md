# note-graph-view — Design

## Context

note-association(M0 落地)+ note-entity-extraction(M5-1 落地)已经在 `note_links` + `note_entities` + `entity_aliases` 表里积累了相当丰富的关联数据,但详情页 `RelatedNotesSection`(见 `feature/quicknote/detail/RelatedNotesSection.kt`)只把它们"线性"铺成 N 张卡片 — 看不出"以我为中心"的拓扑,也看不出实体作为"桥梁"在多条笔记之间的连接作用。

本 change 引入**笔记关系图**:1)复用既有数据源(NoteLinker / NoteEntityDao),2)从 Compose Canvas 自己画图,3)Fruchterman-Reingold 简化版力导向布局从零实现 ~200 行,**不引第三方图库**。技术决策归档如下;具体字段 / 行为走 `specs/note-entity-link/spec.md` delta。

## Goals / Non-Goals

**Goals:**
- 详情页 overflow 加"查看关联图"入口,跳 `note_graph/{noteId}` 新屏
- 中心:当前笔记(高亮,固定在画布中心 1/3 区域)
- 1-hop 节点:既有 `NoteLinker.getRelated` + `getBacklinks` 合并(dedup by noteId),上限 30
- 2-hop 节点:1-hop 笔记们的 `getRelated`(只走 1 步,递归深度硬限 1),上限 +20,总节点硬 cap 50
- 实体 chip:中心笔记的 `getByNoteId`,取 surface form 前 8 个,最多渲染 8 个 chip,浮在画布上
- 布局:Fruchterman-Reingold 简化 spring-electric,~200 行纯 Kotlin,maxIter=300
- 渲染:Compose Canvas + `drawWithCache`,单 `Modifier.graphicsLayer` transform pan/zoom
- 交互:`detectTransformGestures` 单 tap(节点) / drag(空白 pan) / pinch(zoom)
- 缓存:收敛坐标落 `SharedPreferences`,key = `graph_layout_<noteId>_v1`,重新进入同 note 不重算
- 数据零新表、零 schema 改动、零 LLM 调用、零新依赖
- 既有 `note-entity-link` / `note-entity-extraction` / `quick-note` / `ai-gateway` / `note-association-settings` 等 spec-level 行为 0 修改

**Non-Goals:**
- 3D 图 / VR / AR
- 真正的物理仿真(damping 不基于能量守恒,够收敛即可)
- 节点 cluster detection(不引入谱聚类 / Louvain / Leiden)
- 节点 drag-to-move(力导向系统已经决定位置,允许人为拖会破坏收敛)
- 时间轴维度 / 多笔记版本 diff / 多用户协同编辑图
- 导出 PNG / SVG / `IGraph` / `igraph` 序列化
- 关系类型切换(只看 WIKILINK / 只看 ENTITY_HIT) — v2+ 留给后续 change
- 节点点击弹卡片详情(M3 详情内容已在隔壁屏,弹窗会重复)
- 修改 `RelatedNotesSection` 的卡片布局(本 change 只新增关联图入口)

## Decisions

### D1. 零第三方依赖,从零写 Fruchterman-Reingold 简化版

**决策**:`core/note/graph/ForceLayout.kt` 从零实现 Fruchterman-Reingold 风格 spring-electric,~200 行纯 Kotlin + 数据结构,无任何 Maven 依赖。

**为什么**:
- 50 节点级别的规模,引第三方(`jgraphx` 1.4MB / `mpandroidchart` 760KB / `AndroidPlot` 400KB)APK 涨 5~10%,为了"看几个节点"不值
- 第三方图库默认 2D,3D / VR / 沉浸体验后续可能要换,提早绑死库会卡 v2 升级
- 节点数 cap 50,自己写约 200 行,Fruchterman-Reingold 是发布 30 年的稳定算法,无维护风险
- Algorithm 透明度高:收敛阈值 / damping / 步长都在自家代码里,出问题不用追 dependency

**备选**:
- 引 `jgraphx`(Apache 2.0,成熟,API 大):否决,代码量持平但 APK +1.4MB,依赖多
- 引 `mpandroidchart`:否决,这库面向图表(柱/折线),网络图不在设计目标
- 引 OSMDroid / GraphView:否决,前者地图、后者只支持树/缩进,都不是通用 force-directed
- pre-Physics 自渲染(Skia 直接调):否决,Compose Canvas 已经够用,裸 Skia 等于绕 Compose

### D2. 1-hop + 1 层 2-hop,节点 cap 50

**决策**:
- 中心笔记(1)
- 1-hop:`NoteLinker.getRelated(center, 30)` ∪ `NoteLinker.getBacklinks(center, 30)` 去重,(上限 30)
- 2-hop:对 1-hop 中每个节点 A,`NoteLinkDao.getRelated(A.id, 4, threshold)` 取 top 4,去重,排除中心 + 1-hop 已有,(上限 +20)
- 实体 chip:中心笔记 `note_entities` 取 surfaceForm 前 8 个

**为什么**:
- 1-hop 已经覆盖"以我为中心"的主关系,2-hop 揭示"我的朋友的朋友"的隐藏桥
- 30 + 20 = 50 ≤ 既定 cap。算法 O(N²) 在 N=50 是 2500 配对 / 次迭代,300 步 = 75 万 ops,远 < 16ms 单帧预算
- 2-hop 限制深度 1 避免"蝴蝶效应"扩散,3-hop 会让图变成噪声图谱

**备选**:
- 全图 BFS 到 depth=3 / 全表 scan:否决。规模 / 噪声 / 视觉都不可控
- 只展示 1-hop:否决,深度过浅,看不到桥接作用
- tag / entity 全图:否决,跟 QuickNoteListViewModel 现行 `selectTag` 重复,定位不清

### D3. 数据层不引入新 DAO,只加 `GraphDataLoader`

**决策**:`core/note/graph/GraphDataLoader.kt` 纯 Kotlin 类,封装 `NoteLinker` + `NoteDao` + `NoteEntityDao` 三个既有 SPI,产出 `GraphSnapshot`。Room schema 不动。

**为什么**:
- 既有 DAO(`NoteLinkDao.getRelated` / `getBacklinks` / `NoteEntityDao.getByNoteId`)已经满足"以我为中心的多信号聚合 + 共享实体查询",复用零成本
- 加 `GraphSnapshot` 作 DTO 是 UI 层契约,不解耦 feature;如果未来图要多信号过滤,加 query param 到 SPI 比加新 DAO 简单
- 拒引入"专用 graph_query SQL":复杂 JOIN 在 N=5000 时性能可预测,但本项目 N < 1000,提前优化没意义

### D4. 缓存布局坐标到 `SharedPreferences`

**决策**:收敛后的 50 节点坐标序列化 JSON,落 `SharedPreferences` key `graph_layout_<noteId>_v1`(前缀 `graph_layout_`,后缀 noteId,`<noteId>` sanitize 为字母数字下划线)。下次进入同 note 直接读缓存跳到渲染,跳过布局迭代。

**为什么**:
- 用户停留 / 切屏 / 重启 App,重算一遍 layout 浪费 CPU(battery),相同输入算 N 次浪费电
- 缓存以 noteId 为粒度,改动只在中心笔记(被编辑 / 被删除 / 新增实体抽取)时失效
- 没有这层缓存,用户每次进图视都要看到节点"飞过来",体感差;首次飞一次就够

**备选**:
- 数据库缓存(额外表):否决,粒度过细不到中心笔记维度,反而强迫 transaction
- 不要缓存:否决,实测进入耗时 200ms,改善空间 60%(省去 300 步 layout),值得做

**失效策略**(versioned key + 写 path 双向触发):
- `AppDatabase.version`(4 不变,本 change 不 bump,所以前缀 `_v1` 长期有效)
- 中心笔记 `updatedAt` 变 → invalidate(`version` <= cached.version)
- 1-hop 新增节点(新增关联):cache 仍可绘制但新节点从中心飞过来,本 change 接受,不进一步优化

### D5. Compose Canvas + `drawWithCache`,单一 `graphicsLayer` pan/zoom

**决策**:`Modifier.drawWithCache { ... }` 缓存 `Path` 对象(边、节点圆),渲染时只绘当前视口;外层单一 `Modifier.graphicsLayer { scaleX; scaleY; translationX; translationY }` 同步 pan/zoom。`Canvas { drawCircle; drawPath; ... }` 一遍 sweep。

**为什么**:
- 50 节点 ≈ 50 个 circle + 30 条 line,每帧重算 path 浪费,`drawWithCache` 缓存到 struct change
- Compose 的 `graphicsLayer` 把 transform 编译期 lazy,不需要每个 drawBlock 重新算 matrix
- Compose `Canvas` 走 Skia,跟 Skia 自己调不同,DSL 简洁,读 / 写比 OSMDroid / 自绘 Skia 都清晰
- `drawWithCache` 配 `StateFlow<GraphSnapshot>` 只在数据变化时 invalidate,pan/zoom 不重组

**备选**:
- `Spacer + Modifier.drawWithContent { ... }`:等价,选用 `drawWithCache`
- 单 `Canvas` 不分 group:不否决,但 layout + transform 解耦后,可读性降低
- RenderNode / RenderEffect:否决,M3 Compose 1.7.5 已有等价 API,无需跳层

### D6. `detectTransformGestures` 单组合 + `detectTapGestures` 单 tap

**决策**:
- `Modifier.pointerInput(Unit) { detectTransformGestures(panZoomLock = false) { centroid, pan, zoom, rotation -> ... } }` 拿 pan / zoom delta
- `Modifier.pointerInput(nodes.toList()) { detectTapGestures(onTap = { offset -> /* hit test node */ }) }` 拿单 tap,**节点 hit test** 用 squared distance ≤ nodeRadius²
- 节点 hit 阈值:位移 < 12dp 才算 tap,否则视为 drag 起点(为了 drag 走 transformGestures 通道)

**为什么**:
- 单组合 `detectTransformGestures` 把 pan / zoom / rotation 用一个手势 detector 合并,跟 tap detector 分开可避免相互 cancel
- `panZoomLock = false`:pinch + drag 可同时(常用 zoom 在跑的时候顺便 drag 中心)
- 12dp 是 Material guideline 的最小触摸目标,小于这个误触率高

**备选**:
- 自写 `awaitPointerEventScope`:否决,Gesture detector 已经稳定,自写 need to handle 多指 / lifecycle 等 20 几个细节

### D7. 节点视觉编码:size=score / color=entity / 中心高亮

**决策**:
- 节点直径 12~32dp:`12 + score.sigmoid * 20`(score ≈ NoteLinker score,clamp 到 5)
- 节点色:`MaterialTheme.colorScheme.primaryContainer` 描边 + 中心笔记 `surfaceVariant` 实心(高亮)
- 边色:`MaterialTheme.colorScheme.outline`,opacity 0.5,粗细 = 信号强度(评分高的 WIKILINK 边更粗)
- 边箭头:中心节点指向外 / 外指向中心 双向;WIKILINK 类型加箭头头,其它只画 line
- 实体 chip:节点之间连线中点处,贴 `AssistChip`(`label = surfaceForm`,`leadingIcon = Icons.Outlined.Hub`),半透 `surfaceContainerHigh` 背景

**为什么**:
- 节点 = 笔记,中心笔记视觉突出避免视觉混淆
- 边 = 信号强度,粗细传达权重
- 实体 chip 浮在两笔记之间表明"桥"作用,跟既有 `note_association_signal_entity` 文案一致

### D8. 兜底:布局不收敛走 circular layout

**决策**:`ForceLayout.converge()` 返回 `Result<LayoutConverged>`(boolean + iterationCount + finalEnergy)。若 300 步后误差 > tolerance 0.05,屏内回调走 `CircularLayout.fallback()`(节点均匀排在两个同心圆,外圈 ≤ 24,内圈补位)。

**为什么**:
- 力导向布局已知缺陷:完全二分图 / 5+ 节点环 / 全连通分量会震荡或抖动不收敛
- 兜底方案要"看起来像那么回事",圆周排 + 中心连线是网格图最常见的 fallback,体验可接受
- 兜底逻辑封装在 `GraphRenderer.drawNode`,屏不感知失败,失败只在 layout 阶段被 catch

**备选**:
- 始终 circular:否决,力导向视觉更"有机",只看一两次图就够本 change 用户喜欢
- 始终 force:否决,兜底必要

### D9. 不抽 `core/note/graph/layout/` SPI,只有 `ForceLayout` 一实现

**决策**:本 change 只写 `ForceLayout`(单 class),不在 `core/note/graph/` 抽 `Layout SPI` + `ForceLayoutImpl` + `CircularLayoutImpl` 多实现。

**为什么**:
- Single user / single use site / single algorithm 不到抽象阈值;YAGNI
- `CircularLayout` 是 fallback,**它就是 fallback**,不是独立 layout 选择
- v2 + 真要换(例如引 OSM layout 算法),再抽 SPI,届时迁移代码 200 行可控

### D10. 节点点击 navigate 复用既有 `QuicknoteDetail` route

**决策**:节点 onClick 调 `navController.navigate(QuicknoteDetail(node.noteId))`,沿 `popBackStack` 自然返回上一屏(图屏)。不开新 tab / 新窗口。

**为什么**:
- 既有 `QuicknoteDetail(id)` 已类型安全(`@Serializable data class QuicknoteDetail(val id: String)`),零新 route
- 复用既有栈语义,back 自然回图屏,符合"图作为跳转主屏"的常见 UX

**备选**:
- 双向导航栈(详情 / 图互为 stack):否决,会破坏既有 QuicknoteDetail 假设(以 list 起)
- 新 Tab:否决,图屏深度浅,Tab 会让用户失去"上下文中心 = 这篇笔记"感

## Risks / Trade-offs

- **力导向布局震荡** → `damping=0.85` 每步衰减 + `maxIter=300` 硬限 + `tolerance=0.05` 早停 + circular fallback(见 D8);单测覆盖"完全二分图"、"5 节点环"、"星形拓扑"三 case
- **Compose Canvas 在低端设备掉帧** → `drawWithCache` + 单一 `graphicsLayer` transform(见 D5);只一帧 launch 时 path 计算,pan/zoom 走 transform 不重算
- **2-hop 数据查询慢** → 复用既有索引(`note_links` 的 `srcNoteId` / `dstNoteId` / `linkType` 三 index + `note_entities` 的 `entityKey` 主键),每跳 query ≤ 5ms,2-hop 上限 20 node × 4 hop = 80 query 在 < 400ms;若仍超,把 2-hop 挪到 `viewModelScope` IO,首帧先绘 1-hop,后续补 2-hop
- **缓存坐标系版本失效** → 见 D4:`_v1` 跟 AppDatabase.version 绑定,本 change 不 bump DB version,长期有效;中心笔记 `updatedAt` 变 invalidate cache(后续 change 接入,可推迟到 v2)
- **节点 click vs drag 歧义** → 12dp 阈值判 tap / drag(见 D6);误点率 Material guideline 标准
- **i18n 不全** → 中英文档先 `TODO(en):` 占位(见既有 `quick-note` spec);新 strings 6 个 key 见 spec delta
- **pan/zoom 越界 / 节点飞出屏幕** → 单 `translationX/Y` clamp 到 `[-2x, 2x]` 屏幕宽度,zoom clamp 到 `[0.5, 4]`;单测不涉及(交互层)

## Migration Plan

无 DB migration(`AppDatabase` version 4 不变),无数据迁移,无网络同步,无 ktlint rule migration。

- **回滚**:删 `feature/quicknote/graph/` + `core/note/graph/` 文件,删除 `AppNav.kt` `composable<NoteGraph>` block,删除 `QuickNoteDetailScreen.kt` overflow menu 单项(删 1 段 ~ 10 行)。overflow menu 其它项不退
- **不影响**:既有 `RelatedNotesSection` 卡片 / `EntityManagement` / `EntityDetail` / 既有 nav route 全部保留
- **新 strings**:6 个 i18n key,加到 `values/strings.xml`(zh,权威) + `values-en/strings.xml`(英文,`TODO(en):` 占位即可,后续 change polish)

## Open Questions

- **`graph_layout_<noteId>_v1` 失效策略**:本 change 不接入"中心笔记编辑后清缓存",接受缓存 coord 落后于 score 变化(节点仍会重新定位,只是起点可能漂移)。后续 change 接 `NoteRepository.save` 拦截清 cache
- **节点显示真名 vs 缩略**:v1 用完整 title(最大 32 字 ellipsis),后续若长标题过多再缩
- **暗色模式饱和度**:v1 默认 `primaryContainer` 等 token,实测在 Material 3 dynamic color 上亮 / 暗都 OK,后续若饱和度不够再调整
- **节点 score 信号 weight 一致**:`NoteLinkDao.getRelated` 已经按 5 信号公式聚合出 `score`,GraphNode.weight 直接读取,不强加 GraphNode 自己的公式
