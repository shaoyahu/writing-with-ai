# Design · fix-note-graph-rendering

**Change ID**: `fix-note-graph-rendering`
**Parent**: `2026-07-11-note-graph-view` (archived)

## 范围

只改渲染层。算法契约 (ForceLayout / CircularLayout 输出 coords 仍以中心节点为 `(0,0)`) 不变,只把 `(0,0)` 在 Canvas DrawScope 内平移到画布几何中心。后续 chip / legend 也只动布局不改数据。

## 文件

- `app/src/main/java/com/yy/writingwithai/feature/quicknote/graph/NoteGraphCanvas.kt`
  - `drawGraph` (P0)
    - 加 `val canvasCenter = Offset(size.width / 2f, size.height / 2f)`,所有 `drawCircle` / `drawLine` 的 `Offset` 走 `canvasCenter + Offset(a.x, a.y)`。
  - `drawGraph` (P0)
    - 加 nativeCanvas text: 每个节点画完圆后,若 `node.title.isNotBlank()`,调 `nativeCanvas.drawText(title, x, y, paint)`,`paint.color = onSurface` (`scheme.onSurface.toArgb()`), `textSize = density.density * 14`, `isAntiAlias = true`;`title.length > 12` 截到 `12` 字符 + `…`。
    - `pickNode` 同步加 `canvasCenter` 偏移,否则 tap 命中区错位。
  - `EntityChipOverlay` (P1)
    - `.align(Alignment.TopCenter)` → `.align(Alignment.BottomCenter)`,`.padding(8.dp)` → `.padding(bottom = 16.dp, start = 8.dp, end = 8.dp)`。
  - `NoteGraphCanvas` 颜色 (P1)
    - `edgeColor = scheme.outline.copy(alpha = 0.5f)` → `0.7f`。
    - 边 `strokeWidth = 1f + weight*2f` → `1.5f + weight * 2f`。

- `app/src/main/res/values/strings.xml`
  - 新增 `note_graph_legend_title`: `图例`。
  - 新增 `note_graph_legend_header`: `● 关联笔记   ✦ 实体`。

- `app/src/main/java/com/yy/writingwithai/feature/quicknote/graph/NoteGraphScreen.kt`
  - `Info` 图标 `contentDescription = stringResource(R.string.note_graph_legend_title)`。
  - `DropdownMenu` 顶部加 `Box(Modifier.padding(12.dp)) { Text(stringResource(R.string.note_graph_legend_header), style = labelMedium) }`。

## 决策

- **画布坐标平移 vs 改布局算法输出**: 平移到 canvas 中心更直接,而且不破坏 ForceLayout 单测的几何假设 (coords 仍是中心化输出)。改算法输出偏移需要 layout / 单测都调整,代价大;走 canvas 平移。
- **nativeCanvas drawText vs Compose Text**: 图内有中心节点 + 多 1-hop/2-hop,Canvas 内画圆 + 边可以一次性 sweep 性能优;用 Compose Text 叠在 Canvas 上导致每个节点都要 measure / layout,50 节点下 drawText < 1ms 占优。折中:圆和边走 Canvas,label 走 nativeCanvas drawText(走 onSurface 色)。
- **Entity chip TopCenter → BottomCenter**: Bottom 不挡中心画布,视觉重心更稳,主流 graph UI 习惯 (iOS / Android Material 3 graph 库都底部放 legend)。

## 测试

- 现有 `ForceLayoutTest` / `CircularLayoutTest` 不动 (`(0,0)` 还是 center 坐标)。
- 不新增单测: 渲染 bug 走人工 / `assembleDebug` 装模拟器验证。

## 风险

- `drawText` 在 Compose 不可访问 `LocalDensity`,需在 `noteGraphCanvas` Composable 内 `LocalDensity.current.density` 取出,透传给 `drawGraph`;通过新参数 `textPx: Float` 解决。
- canvas `size` 在 `graphicsLayer` 平移 / 缩放下依然是 0..size,平移输入正确。
- nativeCanvas 字符宽度不参与 tap 命中 (只命中圆),所以 `pickNode` 只平移圆心坐标即可。