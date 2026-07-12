# Tasks · fix-note-graph-rendering

## 1. 渲染坐标平移 (P0)
- [x] 1.1 `NoteGraphCanvas.kt` `drawGraph` 顶部加 `val canvasCenter = Offset(size.width / 2f, size.height / 2f)`,所有 `Offset(p.x, p.y)` / `Offset(a.x, a.y)` / `Offset(b.x, b.y)` 改成 `canvasCenter + Offset(...)`。`pickNode` 同步。
- [x] 1.2 `drawGraph` 每个节点画圆后,若 `node.title.isNotBlank()`, 用 `nativeCanvas.drawText(title, x, y, paint)` 画标签;色 `onSurface.toArgb()`,textSize = `density * 14`,`title.length > 12` 截 `…`。新增 `textPx: Float` 参数,由 `NoteGraphCanvas` 用 `with(density) { 14.sp.toPx() }` 注入。

## 2. chip 浮层下移 (P1)
- [x] 2.1 `EntityChipOverlay` `Alignment.TopCenter` → `Alignment.BottomCenter`,padding 加 `bottom = 16.dp`。

## 3. 边可见度 (P1)
- [x] 3.1 `edgeColor` alpha 0.5 → 0.7。
- [x] 3.2 边 `strokeWidth = 1f + weight*2f` → `1.5f + weight * 2f`。

## 4. legend 加 header + a11y title (P2)
- [x] 4.1 `strings.xml` 加 `note_graph_legend_title` = `图例`, `note_graph_legend_header` = `● 关联笔记   ✦ 实体`。
- [x] 4.2 `NoteGraphScreen.kt` `Info` 图标 `contentDescription = stringResource(R.string.note_graph_legend_title)`。
- [x] 4.3 `DropdownMenu` 内, `Divider` 上方加 `Box(Modifier.padding(12.dp)) { Text(stringResource(R.string.note_graph_legend_header), style = labelMedium) }`。

## 5. 验收
- [x] 5.1 `export JAVA_HOME=/opt/homebrew/opt/openjdk@17 && ./gradlew :app:assembleDebug :app:ktlintCheck :app:testDebugUnitTest` BUILD SUCCESSFUL。
- [x] 5.2 模拟器 `Pixel_7_API_35` 装新 build,打开任意笔记 → 关联图屏,目视验证:
  - 中心节点在画布几何中心;
  - 1-hop 节点围绕,标题可见;
  - 边可见 (WIKILINK 实线 / 其它虚线);
  - entity chip 在底部,不挡中心;
  - 点击 Info 出现 "图例" 头部 "● 关联笔记   ✦ 实体" + 下两项菜单。
- [x] 5.3 归档此 change (`/opsx:archive`)。