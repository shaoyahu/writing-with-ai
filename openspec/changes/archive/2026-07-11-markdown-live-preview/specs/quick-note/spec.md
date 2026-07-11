## MODIFIED Requirements

### Requirement: Editor top bar exposes preview mode toggle

`QuickNoteEditorScreen` TopAppBar `actions` 区 MUST 在原"保存"按钮之前渲染一个 `IconButton`，点击后循环切换 `PreviewMode` 三态:`EDIT` → `PREVIEW` → `SPLIT` → `EDIT`。当前态对应的 `Icon` 与 `contentDescription` 来自 `R.string.quicknote_editor_preview_*` 资源，三态文案分别为"编辑 / 预览 / 分屏"。

#### Scenario: 初始态是 EDIT
- **WHEN** 用户从 tab bar 中央 FAB / 列表空状态"新建"按钮进入编辑器
- **THEN** `previewMode == EDIT`，`IconButton` 图标为 `Icons.Filled.Edit`，正文 `BasicTextField` 占据全部编辑区域(行为同 M1)

#### Scenario: 切到 PREVIEW
- **WHEN** 用户点 toggle 一次，`previewMode: EDIT → PREVIEW`
- **THEN** 编辑区隐藏，预览区显示 `MarkdownText(content)` 占满 weight(1f);`IconButton` 图标变为 `Icons.Filled.Visibility`

#### Scenario: 切到 SPLIT
- **WHEN** 用户再点一次，`previewMode: PREVIEW → SPLIT`
- **THEN** 上下分屏：上半 `BasicTextField` weight(1f) + 下半 `MarkdownText(content)` weight(1f);`IconButton` 图标变为 `Icons.Filled.VerticalSplit`

#### Scenario: SPLIT 在小屏竖屏被强制回退
- **WHEN** 用户点 toggle 准备进入 SPLIT，但 `LocalConfiguration.current.screenWidthDp < 600`
- **THEN** 实际态仍是 PREVIEW(不进入 SPLIT)，同时 `Snackbar` 显示"R.string.quicknote_editor_preview_split_unsupported"短文案;`previewMode` StateFlow 内部逻辑跳过 SPLIT 直接 PREVIEW → EDIT

#### Scenario: 切回 EDIT
- **WHEN** 用户在 SPLIT 态点 toggle，`previewMode: SPLIT → EDIT`
- **THEN** 预览区消失，`BasicTextField` 重新占满 weight(1f);焦点不强制恢复(用户在编辑器内已 focus 过)

### Requirement: Preview pane renders Markdown subset

`MarkdownRenderer.render(markdown: String, entityHighlights: List<EntityHighlight> = emptyList(), primaryColor: Color): AnnotatedString` MUST 解析并渲染以下 Markdown 子集：

| 语法 | 视觉 |
| --- | --- |
| `# ` 行首 | `headlineLarge` |
| `## ` 行首 | `headlineMedium` |
| `### ` 行首 | `titleLarge` |
| `- ` / `* ` 行首 | bullet `·` + indent |
| `1. ` / `2. ` ... 行首 | `1.` / `2.` + indent(顺序号原样保留) |
| `**bold**` | `FontWeight.Bold` |
| `*italic*` / `_italic_` | `FontStyle.Italic` |
| `` `code` `` | `background = surfaceVariant` + `FontFamily.Monospace` |
| `[[wikilink]]` | `color = primary` |
| `[text](url)` | `color = primary` + `Underlined`(仅视觉，无 click handler) |
| 段落间空行 | 8dp vertical spacing(通过 AnnotatedString 的 `\n\n` 体现，由 `Text` 默认行距) |

`MarkdownText(markdown, modifier, entityHighlights, primaryColor)` 是薄 Composable，调 `MarkdownRenderer.render` + `Text(annotatedString)`。

#### Scenario: 基础语法同时渲染
- **WHEN** `markdown = "# 标题\n\n这是 **粗体** 和 *斜体* 和 `code`"`
- **THEN** 输出 `AnnotatedString` 含 4 个 span：标题段 `headlineLarge`、普通段含 `Bold` SpanStyle、`Italic` SpanStyle、`code` 段含 `surfaceVariant` background + Monospace

#### Scenario: 未闭合触发符当字面字符
- **WHEN** `markdown = "这是 **未闭合粗体"`(尾部 `**` 缺失)
- **THEN** 输出 `AnnotatedString` 含原文本"这是 **未闭合粗体"，`**` 显示为两个星号字符，不被吞掉

#### Scenario: wikilink 视觉提示
- **WHEN** `markdown = "见 [[晨跑计划]] 一文"`
- **THEN** "晨跑计划" 段以 `SpanStyle(color = primary)` 渲染(不跳实体详情，仅视觉提示)

#### Scenario: 列表缩进
- **WHEN** `markdown = "- 项目一\n- 项目二"`
- **THEN** 两行都以 `· ` 前缀 + indent 渲染；行间不出现多余 vertical spacing(列表段视为单一 block)

### Requirement: Preview escapes user HTML and JS payload as literal text

`MarkdownRenderer.render` MUST NOT 解释 `<script>` / `<iframe>` / `<img src=x onerror=...>` / `[click](javascript:alert(1))` 等 HTML / JS 语法为可执行 markup。所有不匹配 D2 Markdown 触发符的字符(含 `<` `>` `&` `"` `'`)走 `append(literal)` 原样加入输出 `AnnotatedString`。`addUrlAnnotation` / `addStringAnnotation` / `addStringAnnotation("URL", ...)` 不出现在 `MarkdownRenderer` 输出里(防止第三方 ClickableText callback 误跳)。

#### Scenario: script 标签当字面字符
- **WHEN** `markdown = "Hello <script>alert(1)</script>"`
- **THEN** 输出 `AnnotatedString.text` 含完整字符串 `Hello <script>alert(1)</script>`，无 `<script>` 被解析为隐藏 markup;`Text` 渲染时可见所有尖括号字符

#### Scenario: javascript: URL 仅视觉提示
- **WHEN** `markdown = "[点我](javascript:alert(1))"`
- **THEN** 输出含"点我"以 `SpanStyle(color = primary, textDecoration = Underlined)` 渲染，URL `javascript:alert(1)` 字符串本身不被解析为 `addUrlAnnotation`(Compose `Text` 不会自动跳转)

#### Scenario: HTML 实体字符原样显示
- **WHEN** `markdown = "5 &lt; 10 &amp;&amp; 10 &gt; 5"`
- **THEN** 输出 `AnnotatedString.text` 原样保留 `&lt;` `&amp;` `&gt;` 字符;不会被自动转义为 `<` `&` `>`(也不需要转义 —— 因为不解释 HTML)

### Requirement: Preview updates debounced 200ms after content edits

`QuickNoteEditorScreen` 在 `previewMode != EDIT` 模式下 MUST 用 `snapshotFlow { uiState.content }.debounce(200L).collectAsState(initial = "")` 驱动 `MarkdownText` 的 `markdown` 参数。`BasicTextField.onValueChange` 同步写 `viewModel.setContent(...)`(行为不变)，但**预览**不跟随每次按键即时重渲染。

#### Scenario: 连续按键预览延迟
- **WHEN** 用户在 PREVIEW 模式下连续键入 "abc" 三个字符(无停顿)
- **THEN** `BasicTextField` 显示 "abc" 实时同步；预览区在最后一次按键后 ~200ms 才显示 "abc" 渲染结果

#### Scenario: 停顿后预览即时
- **WHEN** 用户键入 "ab" 后停顿 500ms 再键入 "c"
- **THEN** 键入 "ab" 后 ~200ms 预览显示 "ab" 渲染；再键入 "c" 后 ~200ms 预览显示 "abc"

#### Scenario: 切回 EDIT 模式不丢光标
- **WHEN** 用户在 PREVIEW 模式下编辑完切回 EDIT
- **THEN** `BasicTextField` 仍持有最新 `uiState.content`(没丢字符);光标位置由 IME 决定(不强制恢复)

### Requirement: Detail screen reuses MarkdownText without behavior regression

`QuickNoteDetailScreen` 正文渲染 MUST 改为调 `MarkdownText(content, modifier, entityHighlights = entityHighlights, primaryColor = primaryColor)`，原 `buildEntityAnnotatedString(content, entityHighlights, primaryColor)` 内部逻辑被 `MarkdownRenderer.render` 复用。点击 entity 命中 `ModalBottomSheet` 行为必须保留(M3+ entity-tap-reliable-fix)。

#### Scenario: 详情屏渲染含 Markdown 的内容
- **WHEN** `note.content = "# 标题\n\n这是 **粗体** [[实体]] 文本"`，`entityHighlights` 含 `[[实体]]` span(start=15, end=19)
- **THEN** `MarkdownText` 输出 AnnotatedString 含："标题" 段 `headlineLarge`、`**粗体**` 段 `Bold` SpanStyle、`[[实体]]` 段以 primary 色 + ✦ 字符渲染(实体末尾);点击 `[[实体]]` 仍弹 `ModalBottomSheet`

#### Scenario: 详情屏 entity 命中区域不变
- **WHEN** 用户点击 entity span 上 / 内 / ✦ 右侧任意位置
- **THEN** `detectTapGestures` 命中逻辑(`contentOffset <= h.contentEnd` + `EntityCrossStarChar.first()` 计数)走 M5 已修路径，命中行为不变

#### Scenario: 详情屏非 entity 文本无视觉变化
- **WHEN** `note.content = "纯文本无 Markdown 语法"`
- **THEN** 渲染结果视觉上与 M3+ 一致(无 Markdown 触发符时原样显示);`MarkdownText` 与原 `Text(annotatedContent)` 视觉等价

### Requirement: i18n strings for preview mode

`app/src/main/res/values/strings.xml`(中文权威)与 `values-en/strings.xml`(英文) MUST 新增以下 key，Composable 内禁止硬编码：

| key | 中文 | 英文 |
| --- | --- | --- |
| `quicknote_editor_preview_mode_edit` | 编辑 | Edit |
| `quicknote_editor_preview_mode_preview` | 预览 | Preview |
| `quicknote_editor_preview_mode_split` | 分屏 | Split |
| `quicknote_editor_preview_split_unsupported` | 当前屏幕宽度不支持分屏，已切回预览 | Split view is not supported on this screen width |
| `quicknote_editor_preview_toggle_cd` | 切换编辑预览模式 | Toggle preview mode |

#### Scenario: 中文显示
- **WHEN** 系统语言为中文，用户点 toggle 按钮
- **THEN** `IconButton.contentDescription = "切换编辑预览模式"`;Snackbar fallback 文案 = "当前屏幕宽度不支持分屏，已切回预览"

#### Scenario: 英文显示
- **WHEN** 系统语言为英文，用户点 toggle 按钮
- **THEN** `IconButton.contentDescription = "Toggle preview mode"`;Snackbar fallback 文案 = "Split view is not supported on this screen width"

#### Scenario: 英文未翻译不阻断构建
- **WHEN** 某个 key 在 `values-en/strings.xml` 中值为 `TODO(en): quicknote_editor_preview_*`
- **THEN** `./gradlew :app:assembleDebug` 与 `:app:check` 仍通过，运行时显示该占位文本