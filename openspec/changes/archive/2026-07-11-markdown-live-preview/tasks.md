## Tasks

### 阶段 1:Markdown 渲染管线抽出 + 解析器实现

- [x] 新建 `app/src/main/java/com/yy/writingwithai/core/ui/MarkdownRenderer.kt`
  - 顶层纯函数 `fun render(markdown: String, entityHighlights: List<EntityHighlight> = emptyList(), primaryColor: Color): AnnotatedString`
  - 解析器只识别 §D2 列出的 Markdown 触发符,其他字符原样 append
  - 解析失败 / 未闭合触发符回退到字面字符(无 throw)
  - entity 段通过 `buildEntityAnnotatedString`(从 `QuickNoteDetailHelpers.kt` 复用)叠加

- [x] 新建 `app/src/main/java/com/yy/writingwithai/core/ui/MarkdownText.kt`
  - 薄 Composable `MarkdownText(markdown, modifier, entityHighlights, primaryColor)`:调 `MarkdownRenderer.render` + `Text(annotatedString, modifier)`
  - 默认 `entityHighlights = emptyList()`,`primaryColor = MaterialTheme.colorScheme.primary`(在 Composable 内取)

- [x] 新建 `app/src/test/java/com/yy/writingwithai/core/ui/MarkdownRendererTest.kt`(JVM 单测)
  - 覆盖:H1/H2/H3 渲染 / bullet & 有序列表 / bold / italic / inline code / wikilink / link / 段落分隔
  - 容错:`**` 未闭合 / `# ` 行末 / `*italic*` 与 `**bold**` 共存歧义
  - HTML 注入:`<script>` / `<iframe>` / `[x](javascript:...)` 当字面字符
  - entity 叠加:Markdown 文本含 `[[entity]]` + `entityHighlights` 时,entity 段含 ✦ + primary 色
  - 性能:1KB 笔记渲染 < 50ms(JVM measureTimeMillis)

### 阶段 2:详情屏迁移到 MarkdownText(零行为回归)

- [x] `QuickNoteDetailScreen.kt`:正文 `Text(annotated = annotatedContent)` 改为 `MarkdownText(contentText, modifier, entityHighlights, primaryColor)`
- [x] 验证 entity 点击 / `detectTapGestures` / `starsBefore` 计数逻辑不变(走 D7 兼容路径)
- [x] 单测:`MarkdownRendererTest` 加 case 验证 entity 高亮 + ✦ 字符在 markdown 输出里仍存在

### 阶段 3:SimpleMarkdownEditor.Preview 升级

- [x] `app/src/main/java/com/yy/writingwithai/core/editor/SimpleMarkdownEditor.kt`:`Preview()` 实现替换为 `MarkdownText(markdown, modifier)`,删除原 `Text(markdown)` 占位
- [x] `MarkdownEditor` 接口签名不变(只是换实现)
- [x] `EditorModule` DI 不动

### 阶段 4:编辑器屏加 toggle 按钮 + 拆分模式

- [x] `app/src/main/java/com/yy/writingwithai/feature/quicknote/edit/QuickNoteEditorScreen.kt`:
  - 新增 `enum class PreviewMode { EDIT, PREVIEW, SPLIT }`
  - `var previewMode by rememberSaveable { mutableStateOf(PreviewMode.EDIT) }`
  - TopAppBar `actions` 在"保存"按钮之前加 `IconButton`,点击循环 `EDIT → PREVIEW → SPLIT → EDIT`
  - 根据 `previewMode` 渲染 `BasicTextField` / `MarkdownText` / 上下分屏(用 `Column` 分配 weight(1f) + weight(1f))
  - 进入 SPLIT 前查 `LocalConfiguration.current.screenWidthDp < 600` → 强制回退 PREVIEW + Snackbar 提示
  - preview != EDIT 时 `previewContent by remember(viewModel) { snapshotFlow { uiState.content }.debounce(200L).collectAsState(initial = "") }`(用 `kotlinx.coroutines.flow.debounce`,Compose 调 `LaunchedEffect` + `collectAsState`)

- [x] `app/src/main/res/values/strings.xml` + `values-en/strings.xml`:
  - `quicknote_editor_preview_mode_edit` / `_preview` / `_split`
  - `quicknote_editor_preview_split_unsupported`
  - `quicknote_editor_preview_toggle_cd`

### 阶段 5:验证 + review

- [x] `./gradlew :app:check` 全绿(含 ktlintCheck + testDebugUnitTest + lint)
- [x] `./gradlew :app:assembleDebug` 通过
- [x] 手测路径(在 Android 模拟器或真机):
  - 新建笔记 → 编辑 `# Hello` → 切 PREVIEW → 看到 `headlineLarge` 渲染
  - 切 SPLIT(横屏宽屏) → 上下分屏,编辑同步预览
  - 竖屏小屏点 SPLIT → 强制回退 PREVIEW + Snackbar
  - 详情屏打开含 `# / ** / [[entity]]` 的笔记 → entity 点击仍弹 sheet,Markdown 渲染生效
  - 输入 `<script>alert(1)</script>` → 预览显示为字面字符,无异常
  - 旋转屏幕(previewMode 由 rememberSaveable 持久化)→ 模式不丢失
- [x] self-review:走 `/opsx:apply` 后发起 `ecc:code-review` 或 `code-review:code-review`,确保单测覆盖 §"Markdown 子集"全部语法 + 注入 case