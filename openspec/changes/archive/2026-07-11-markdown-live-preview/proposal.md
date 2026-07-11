## Why

当前笔记编辑器(`QuickNoteEditorScreen`)走纯文本 `BasicTextField`，用户在编辑时看不到 Markdown 渲染效果：写 `# 标题` / `- 列表` / `**粗体**` 后必须点保存 → 跳详情页才能看到渲染结果。这条"编辑 → 保存 → 看效果 → 不满意 → 再编辑"循环打断写作心流，是当前 UX 的高频痛点。

富文本编辑器(`rich-text-editor`)change 已经定义了 `MarkdownEditor` 接口 (`Editor` + `Preview`)，`SimpleMarkdownEditor` 是 v1 占位实现（`Preview` 仅 `Text(markdown)` 原样输出）。`rich-text-editor` 的 v2 是"引入真正的 Markdown 渲染库或自建 AnnotatedString 渲染"，但截止当前 change 未落地。**本次 change 是 v2 的第一步**：在编辑器屏加 toggle / 拆分视图，让用户编辑时直接看到 Markdown 渲染预览，先解决 UX 问题；后续 v2.1 再做完整 Markdown 语法子集 + scroll-sync。

## What Changes

- **抽出可复用 Markdown 渲染 Composable**：把详情屏已有的 `buildEntityAnnotatedString` / `annotatedContent` 渲染管线抽到 `core/ui/MarkdownText.kt`，作为详情屏 + 编辑器预览屏的共享组件。
- **实现基础 Markdown 渲染**：自建轻量 `Markdown → AnnotatedString` 解析器，支持标题(H1-H3) / 列表(- item) / 行内代码(`code`) / 加粗(`**bold**`) / 斜体(`*italic*` / `_italic_`) / wikilink(`[[...]]`) / 链接(`[text](url)`)。**故意不做**：表格、代码块语言标记、数学公式、图片(因 v2 还要做智能块渲染，单独走后续 change)。
- **编辑器屏加预览 toggle**：TopAppBar `actions` 加切换按钮(`Icons.Filled.Visibility` / `Icons.Filled.VisibilityOff`)，点击切换"纯编辑 / 仅预览 / 上下分屏(landscape & width ≥ 600dp)"三种模式。默认 = 纯编辑(保持 M1 行为)。
- **预览更新去抖**：预览不跟随每次按键即时重渲染，使用 200ms `snapshotFlow` + `debounce` 节流；编辑大笔记时确保输入流畅。
- **XSS / 注入防御**：Markdown 渲染时**所有用户文本走 `appendLiteral` 风格**，原始 HTML 标签(`<script>` / `<iframe>` / `onclick=`)显示为字面字符，不解析为 markup / 不执行。
- **UI 文案 i18n**：toggle 按钮 + 模式文案走 `R.string.quicknote_editor_preview_*`，中英文双语；Composable 内禁止硬编码。

## Capabilities

### Modified Capabilities
- `quick-note`: 编辑器屏新增 preview toggle + 拆分视图；详情屏 markdown 渲染管线被复用
- `rich-text-editor`: `MarkdownEditor.Preview` 由"纯文本"升级为"基础 Markdown 渲染"

## Impact

- 受影响模块:
  - `app/src/main/java/com/yy/writingwithai/core/ui/MarkdownText.kt`（**新建**：可复用 Markdown 渲染 Composable）
  - `app/src/main/java/com/yy/writingwithai/core/ui/MarkdownRenderer.kt`（**新建**：纯函数 `String → AnnotatedString`，VM/Compose 都能跑，便于单测）
  - `app/src/main/java/com/yy/writingwithai/core/editor/SimpleMarkdownEditor.kt`（替换 `Preview` 实现，调 `MarkdownText`）
  - `app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/QuickNoteDetailScreen.kt`（`buildEntityAnnotatedString` 调用处改为 `MarkdownText`，行为不变；entity 高亮由 `MarkdownText` 内部叠加）
  - `app/src/main/java/com/yy/writingwithai/feature/quicknote/detail/QuickNoteDetailHelpers.kt`（`buildEntityAnnotatedString` 保留，签名不变，被 `MarkdownText` 复用）
  - `app/src/main/java/com/yy/writingwithai/feature/quicknote/edit/QuickNoteEditorScreen.kt`（加 preview toggle 状态 + 切换 UI + 拆分布局）
  - `app/src/main/res/values/strings.xml` + `values-en/strings.xml`（新增 i18n key）
  - `app/src/test/java/com/yy/writingwithai/core/ui/MarkdownRendererTest.kt`（**新建**：解析器单测）
- 不改: `Note` / `NoteEntity` schema、`MarkdownEditor` 接口签名(只换实现)、Room schema、NoteRepository、AppDatabase 版本、EditorModule DI(实现类名不变)。