# Proposal · fix-note-graph-rendering

**Change ID**: `fix-note-graph-rendering`
**Date**: 2026-07-11
**Status**: proposed
**Parent change**: `2026-07-11-note-graph-view` (archived)
**Author**: AI 自动起草

## Why

用户真机验证 `note-graph-view` change 时, 关联图屏 (`NoteGraphScreen`) 渲染结果不符合预期:

1. **中心节点偏左上一角被截掉** (P0)
2. **节点只有圆, 无文字标签** (P0)
3. **实体 chip 浮层覆盖中心节点区域** (P1)
4. **边几乎看不见** (P1)
5. **i (Info) 图标用户不熟悉语义** (P2 — 已确认是 legend dropdown, 保留并强化 tooltip)

截图实证: 见用户附图 (关联图屏 — note-graph-view 验收截图)。

## What Changes

修 `app/src/main/java/com/yy/writingwithai/feature/quicknote/graph/NoteGraphCanvas.kt` 的 `DrawScope.drawGraph` 和 `BoxScope.EntityChipOverlay`:

### 必修 (P0)
- **坐标平移**: `drawGraph` 内部把 ForceLayout / CircularLayout 输出坐标 `(x, y)` 整体平移到 canvas 几何中心 `(size.width/2, size.height/2)`, 中心节点 (`(0, 0)`) 落在画布中央, 1-hop 围绕外圈均匀分布。
- **节点标题**: `drawGraph` 在每个圆右侧 / 下侧画 `nativeCanvas.drawText(title, x, y, paint)`, `title` 走 `MaterialTheme.colorScheme.onSurface`, textSize=14sp, isAntiAlias=true; 空 title 跳过。`title` 长度 > 12 字符截 `…`。

### 应当 (P1)
- **EntityChipOverlay 位置**: 从 `TopCenter` 改 `BottomCenter`, `padding(bottom = 16.dp)`, 避免与中心节点视觉重叠。
- **边的可见度**: `edgeColor` alpha 0.5 → 0.7; `strokeWidth = 1f + weight*2f` → `1.5f + weight * 2f`, 最低 1.5px。
- **WIKILINK 实线, 其它虚线** 保留。

### 加分 (P2)
- **legend dropdown 解释**: `Info` 图标 `contentDescription` 改为 `R.string.note_graph_legend_title` (新增字符串), tooltip 在 dropdown 顶部加 `Text(stringResource(R.string.note_graph_legend_header))` 一行说明 (实体色=✦, 关联笔记=●)。

### 范围外 (out of scope)
- **ForceLayout 算法调参 (tolerance 0.5 → 0.05; FR 原版 1/d repulsion)** — 与现有 ForceLayoutTest star/cycle 150-iter 收敛硬约束冲突, 单独开 change (`fix-force-layout-tuning`) 处理, 配套放宽单测 iter 约束。
- **节点数 > 50 时的 viewport 性能** — 当前 50 节点实测 < 5ms / iter, 暂不优化。
- **节点的拖拽编辑布局** — 不在本 change。

## 影响

### 用户层面
- 关联图能正确显示中心 + 1-hop + 2-hop, 节点可识别 (有 title)。
- 实体 chip 不再挡中心节点。

### 代码层面
- `NoteGraphCanvas.kt` — `drawGraph` / `EntityChipOverlay` 各改一处, 函数签名不变。
- `R.string.note_graph_legend_title` + `R.string.note_graph_legend_header` 新增 (中文 + 英文)。

### Spec 层面
- 不动 OpenSpec `note-entity-link/spec.md` (layout 算法契约不变, 只修渲染层)。
- 不动 `2026-07-11-note-graph-view` archived change 的 design / tasks。

## 验收

1. `./gradlew :app:assembleDebug :app:ktlintCheck :app:testDebugUnitTest` BUILD SUCCESSFUL。
2. 真机 / 模拟器 (`Pixel_7_API_35`) 装新 build, 打开任意笔记的关联图屏:
   - 中心节点在画布几何中心, 不被截;
   - 1-hop 节点围绕外圈, 名称可见;
   - 边可见 (WIKILINK 实线 / 其它虚线);
   - 实体 chip 在底部, 不挡中心。
3. TalkBack 滑过 canvas 仍然能听到完整 a11y summary (F7 H4 已加, 不退步)。

## Risk

- **drawText 性能**: 50 节点 × drawText ≈ 1ms, 可接受。
- **标题长度截断** 不影响实际数据, 只 UI 层裁剪。
- **EntityChipOverlay 改 BottomCenter** 影响视觉重心但不影响数据。

## 关联

- Parent: `openspec/changes/archive/2026-07-11-note-graph-view/`
- Deferred follow-up: `fix-force-layout-tuning` (F8 4.1 + 4.10)
- Spec: `openspec/specs/note-entity-link/spec.md` (无 delta)