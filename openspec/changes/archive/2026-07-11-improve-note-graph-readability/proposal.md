## Why

关联图屏(`NoteGraphScreen`)在真机视觉验收中暴露出三个互相叠加的可读性问题,直接影响用户对"这条图屏有什么用"的认知:

1. **节点标签重叠**:中心节点与距离较近的 1-hop 节点(`hopLevel=1`)在画布上落在同一象限,`nativeCanvas.drawText` 在 `radius + 4px` 处的固定偏移下,标签相互压字(用户反馈截图:MSA 节点标题与 "Forge:可扩展的" 标题重叠)。
2. **边几乎看不见**:Material 3 light scheme 下 `outline` 与 `surface` 对比度低,即使 alpha 已经从 0.5 提到 0.7、strokeWidth 1.5 + weight*2,在背景色 `#FEF7FF` 类色调上仍接近不可见。
3. **首次进入无引导**:用户不知道图屏在表达什么、节点代表什么、可以做什么交互。空态已有 "还没有任何关联" 提示,但 Loaded 态直接进图,没有任何解释,导致 "这什么意思" 类反馈。

`fix-note-graph-rendering` change 已落地 P0/P1/P2 修复(坐标平移、节点标题、chip 下移、边可见度、legend header),但只解决"图能画出来"和"控件可读",未解决"图读起来不累"。这是新 scope,需要新 OpenSpec change。

## What Changes

- **新增 `note-graph-readability` capability**,覆盖关联图屏在 Loaded 态下的视觉可读性 + 用户引导:
  - **节点标签碰撞避让**:根据节点位置 + 邻居半径自动选择 label 偏移方向(右 / 左 / 上 / 下),避免标签互相压字。
  - **边配色升级**:light scheme 用 `onSurfaceVariant.copy(alpha=0.6)` + `strokeWidth=2.5f + weight*3f`;dark scheme 用 `outlineVariant`;WIKILINK 实线 / 其它虚线 保持不变。
  - **图标题 + 节点计数提示**:TopAppBar 标题右侧加 `XX 个节点 · YY 条关联` 副标题(`titleContent` slot),让用户一眼看到图的规模。
  - **首次进入引导文案**:Loaded 态下,若节点数 ≤ 2,在画布底部加一行说明性提示("关联图显示了通过链接、标签、实体连过来的笔记;点击节点跳转,双指捏合缩放")。
  - **空态增强**:1-hop 节点数 = 0 时,空态文案改为可执行指引("给这条笔记加 #标签 或引用其他笔记,自动生成关联图"),配图标。
- **修改**:无现有 capability 的 REQUIREMENTS 需要改,本次纯新增。

## Capabilities

### New Capabilities
- `note-graph-readability`: 关联图屏的可读性增强 + 首次使用引导。

### Modified Capabilities
(无 — 现有 capability 的需求不变。)

## Impact

**Affected code**:
- `app/src/main/java/com/yy/writingwithai/feature/quicknote/graph/NoteGraphCanvas.kt`(`drawGraph` 加 label 碰撞避让 + 边配色升级)
- `app/src/main/java/com/yy/writingwithai/feature/quicknote/graph/NoteGraphScreen.kt`(TopAppBar 副标题 + 空态增强 + Loaded 态引导文案)
- `app/src/main/res/values/strings.xml`(新增 ~6 个字符串)
- `app/src/main/java/com/yy/writingwithai/core/note/graph/NodeCoords.kt`(可能扩展字段携带 hopLevel / scoreForLabelCollision 用于算法)
- `app/src/main/java/com/yy/writingwithai/core/note/graph/GraphSnapshot.kt`(可能扩展 Loaded 态携带节点数 / 边数统计字段,避免 VM 重复计算)

**Affected APIs**:
- 无公开 API 变更。`NoteGraphViewModel` 仅内部调整。
- 无新增权限。

**Affected dependencies**:
- 无。沿用现有 Compose + Material 3。

**Affected tests**:
- `NoteGraphCanvas` 的 `pickNode` 单元测试需要新增 label 碰撞避让的视觉快照测试(若现有 snapshot test 缺失则跳过,以手动视觉验收为准)。

**Affected UX**:
- Loaded 态下用户首屏认知从 "这是什么" 变成 "这是 X 个节点 / Y 条关联",并附操作引导。
- 边可见度从"勉强可见"提升到"清晰可辨"。
- 标签重叠从"完全压住"变成"自动避让"。