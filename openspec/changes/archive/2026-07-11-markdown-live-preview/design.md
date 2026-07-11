## Context

M1 在 `core/editor/MarkdownEditor.kt` 留了 `Editor` + `Preview` 双方法接口；M1.5 `SimpleMarkdownEditor.Preview` 仅 `Text(markdown)` 原样显示，**没渲染 Markdown**。M3+ 详情屏(`QuickNoteDetailScreen`)已经有一套"内容 + 实体高亮 → `AnnotatedString`"的渲染管线(`buildEntityAnnotatedString` 在 `QuickNoteDetailHelpers.kt`)，但只渲染"实体高亮 + 末尾 ✦"，没有把 `# 标题` / `- 列表` / `**粗体**` 这些 Markdown 语法变成视觉样式 —— 详情屏实际上还是当纯文本显示带颜色 span。

用户在编辑器写 Markdown → 详情屏看不出渲染 → 必须借助第三方 App 看效果，是当前真实痛点。本次 change 把详情屏的渲染管线升级为"Markdown 解析 + 实体高亮叠加"，并在编辑器屏加 toggle 同步预览。

## Goals / Non-Goals

### Goals
1. 编辑器屏 toggle 按钮切"纯编辑 / 仅预览 / 上下分屏(landscape & 宽屏)"三种模式。
2. 编辑时实时预览 Markdown 渲染(200ms debounce)。
3. 抽出 `core/ui/MarkdownText` Composable + `core/ui/MarkdownRenderer` 纯函数供详情屏 + 编辑器预览屏共用。
4. Markdown 渲染支持基础子集：H1-H3 / `- list` / `1. list` / `**bold**` / `*italic*` / `` `code` `` / `[[wikilink]]` / `[text](url)` / 段落分隔 / 实体高亮叠加(entity detail 场景)。
5. 用户输入的 `<script>` / `<iframe>` / `onclick=` 等 HTML / JS 注入字符**作为字面字符显示**，不解释为 markup 不执行。

### Non-Goals
- **不做 scroll-sync**：编辑器输入位置 ↔ 预览位置联动留 v1.1(见 D6)。当前预览独立滚动即可。
- **不做表格 / 代码块 fence / 数学公式 / 图片内联渲染**：留给后续 change(实体智能块渲染)。
- **不做 WYSIWYG**：预览只读，不能在预览里点编辑。
- **不引入第三方 Markdown 库**：保持 `core/ui/` 零三方依赖；用 `AnnotatedString` + `SpanStyle` 自建轻量解析器。
- **不改 `MarkdownEditor` 接口签名**：只换 `SimpleMarkdownEditor.Preview` 实现。

## Decisions

### D1: 渲染管线拆到 `core/ui/MarkdownRenderer.kt`(纯函数) + `core/ui/MarkdownText.kt`(Composable)

`MarkdownRenderer.render(markdown: String, entityHighlights: List<EntityHighlight> = emptyList(), primaryColor: Color): AnnotatedString` 是顶层纯函数，可被 VM/Compose 单测，不依赖 Compose runtime。

`MarkdownText(markdown, modifier, entityHighlights, primaryColor)` 是薄 Composable，调 `MarkdownRenderer.render` + `Text(annotatedString)`。

**为什么**：详情屏 `buildEntityAnnotatedString` 当前在 `QuickNoteDetailHelpers.kt`，但它是 entity-only 的；Markdown 解析是新的关注点。两者叠加在 `MarkdownRenderer.render` 内：先走 Markdown → line-level spans，再叠加 entity highlights。详情屏原有 entity 高亮行为保留(通过 `entityHighlights` 参数传入，零行为变化)。

### D2: Markdown 子集刻意收窄

| 语法 | 渲染 | 实现 |
| --- | --- | --- |
| `# H1` / `## H2` / `### H3` | `headlineLarge` / `headlineMedium` / `titleLarge` | line start 匹配 |
| `- item` / `* item` | bullet · + indent | 1 级嵌套支持 |
| `1. item` | `1.` + indent | 仅有序起始(子序号 1 / 2 / 3... 由纯文本决定) |
| `**bold**` | `FontWeight.Bold` | 行内正则 |
| `*italic*` / `_italic_` | `FontStyle.Italic` | 行内正则(避免吞 `**`) |
| `` `code` `` | `background = surfaceVariant` + `FontFamily.Monospace` | 行内正则 |
| `[[wikilink]]` | `color = primary` | 行内正则;非实体库的 wikilink 也按 primary 色显示(v1 已知行为，不跳实体详情) |
| `[text](url)` | `color = primary` + `Underlined` | 行内正则(URL 不实际 clickable，预览只读) |
| 段落分隔(空行) | 8dp vertical spacing | `\n\n` 检测 |
| 原始 HTML `<script>` 等 | 当字面字符 | 解析器只识别 Markdown 触发符，不识别 HTML 标签 / JS 属性 |

**为什么不支持更多**：表格 / fence / 图片 inline 都需要对齐计算 + 资源加载 / 自定义 widget，单 change 塞不进；用户 main 反馈集中在"看到基本渲染效果"，完整 spec 后续 change 补。

### D3: 编辑器屏 preview 模式三态

`QuickNoteEditorScreen` 内 `var previewMode by rememberSaveable { mutableStateOf(PreviewMode.EDIT) }`：

- `PreviewMode.EDIT`(默认)：原 `BasicTextField` 占满 weight(1f)(M1 既有行为)。
- `PreviewMode.PREVIEW`：`MarkdownText(content)` 占满 weight(1f)，无编辑器；适合写完一段后通读。
- `PreviewMode.SPLIT`：竖屏且 `LocalConfiguration.current.screenWidthDp < 600` **不允许**(强制回退 EDIT 模式，状态不变)；否则上下 `Column` 各 weight(1f)，上 editor 下 preview。

TopAppBar actions 区 `IconButton` 切换：EDIT → PREVIEW → SPLIT → EDIT 循环。图标用 `Icons.Filled.Edit` / `Visibility` / `VerticalSplit`(M3 icons 全部官方有)。

**为什么不全屏 sheet / modal 弹预览**：modal 弹预览会失去"对照原文"语境；分屏一眼对照原文本 + 渲染。

### D4: 预览更新 200ms debounce

编辑器 `BasicTextField.onValueChange` 同步写 `viewModel.setContent(...)`；预览屏用 `val previewContent by remember(viewModel) { snapshotFlow { uiState.content }.debounce(200L).collectAsState(initial = "") }`。**注意**：`uiState.content` 永远是最新值，debounce 仅作用在"触发 `MarkdownText` 重渲染"这一步。

**为什么 200ms**：人类键入 pause 通常 ≥ 200ms，pause 期间预览更新无感知；不停顿输入大段时，预览整体延迟 ~200ms 可见，不影响编辑流畅。

### D5: XSS / 注入防御走"解析器只识别 Markdown 触发符"原则

`MarkdownRenderer` 解析时只识别 D2 列出的 Markdown 触发符(`#` / `**` / `*` / `_` / `` ` `` / `[[` / `[` / `-` / `1.`)。任何其他字符(包括 `<` `>` `&` `"` `'`)都走 `AnnotatedString.append(text)` 原样加入。

**关键**：不要写"先 escape HTML 再渲染"——直接用 Compose `Text` 渲染 `AnnotatedString` 也不会执行 HTML / JS(Compose `Text` 不解析 HTML)，即使不做 escape 也是安全的。但保持"Markdown 触发符之外不解释"是更深的防御：未来如果有人引入 `ClickableText` + `addUrlAnnotation`，未 escape 的 `javascript:alert(1)` 仍可能被外部 callback 误点。本 change 解析器 **不生成** `addUrlAnnotation`(URL 只作为 `SpanStyle(color = primary, textDecoration = underline)` 的视觉提示)，从根本上杜绝跳转。

### D6: scroll-sync 推迟到 v1.1

scroll-sync(编辑器光标位置 → 预览同步滚动到对应行)是 UX 锦上添花，但实现需要在 Markdown parser 内记录"字符索引 ↔ 渲染行号"映射，复杂度比 toggle 预览高一档。v1 只做"独立滚动 + toggle 切换"，符合 M5 polish 阶段"先把核心路径走通"原则。v1.1 走单独 change(命名 `markdown-scroll-sync`)，预估 +1 day。

### D7: 复用 `QuickNoteDetailHelpers.buildEntityAnnotatedString`

`buildEntityAnnotatedString(content, highlights, primaryColor)` 当前签名不依赖 Markdown 解析，只做"字符 → AnnotatedString + entity span + ✦"。本 change 把它原样保留在 `QuickNoteDetailHelpers.kt`，`MarkdownRenderer.render` 内调它处理 entity 段(Markdown 文本 → 切 entity 段 → 每段 `append` + entity span 样式)。

**风险**：详情屏 entity 高亮"最右半含 ✦ 全部归该 entity"的语义(见 `QuickNoteDetailScreen.kt:606` `contentOffset <= h.contentEnd`)必须在 Markdown 渲染后保持一致。`MarkdownRenderer` 输出 `AnnotatedString` 时，entity span 坐标是"在 markdown 输出中的索引"，详情屏 `detectTapGestures` 的 `annOffset → contentOffset` 映射仍走原 `EntityCrossStarChar.first()` 计数法(✦ 字符本身在 AnnotatedString 里也保留为可见字符，`starsBefore` 计算等价)。

## Risks / Trade-offs

- **R1: 详情屏 `buildEntityAnnotatedString` 行为漂移**：`MarkdownRenderer` 把 entity 段作为 Markdown 渲染管线的一个阶段，详情屏原本的"普通 span + ✦"行为必须保留。**缓解**：单测覆盖 `MarkdownRenderer.render(content, entityHighlights)` 在 `content="# H1\n[[entity]]"` 场景下，entity 段仍渲染 ✦ + primary 色，且 `AnnotatedString.text` 含 ✦ 字符(供详情屏 `starsBefore` 计数)。如果单测失败，**回退方案**：详情屏继续走原 `buildEntityAnnotatedString`，不走 `MarkdownText` —— 行为兼容优先级 > 复用。**耗时**：+0.5 day。
- **R2: 解析器在用户笔误时容错**：用户写 `#` 不带空格 / `**` 未闭合 → 解析器必须按字面字符 fallback，不能抛异常或丢失字符。**缓解**：所有正则匹配失败时回退到 `append(literal)`，单测覆盖 5+ 常见笔误 case。**耗时**：含在 D1 解析器实现内，无额外时间。
- **R3: 200ms debounce 让快速连写看到"延迟预览"**：用户输入不停顿时，预览整体延迟 200ms 才更新；停顿下来立刻更新。**可接受**：与 IDE / Notion / Typora 行为一致；用户感知不强。
- **R4: SPLIT 模式在竖屏小屏(宽度 < 600dp)体验差**：上下两块各占一半，正文编辑和预览都看不清。**缓解**：竖屏小屏强制 EDIT 模式，状态栏 toggle 按钮显示但点 SPLIT 直接回退 EDIT，附 Snackbar 提示"宽屏才支持分屏"。**耗时**：含在 D3 内。
- **R5: i18n key 漏配导致构建失败**：中文资源 + 英文资源必须同步加 key。**缓解**：`./gradlew :app:check` 跑 `lint` 时 `MissingTranslation` 会报错；tasks.md 第 4 步含 "en 资源同步 + lint check" 验证项。

## Migration / Compatibility

- `MarkdownEditor.Preview(markdown: String, modifier: Modifier)` 签名不变；`SimpleMarkdownEditor` 是接口唯一实现，DI(`EditorModule.bindMarkdownEditor`)不动；v2 替换 `MarkdownRenderer` 实现即可，调用方零变化。
- 详情屏 entity 高亮 / 点击弹 sheet 行为完全保留(走 D7 兼容路径 + 单测)。
- 默认 UI 仍是 EDIT 模式，老用户无感。

## Open Questions

- 无。SPLIT 模式小屏 fallback、scroll-sync 推迟、HTML escape 防御均在设计层面定好，实现阶段按 tasks.md 走即可。