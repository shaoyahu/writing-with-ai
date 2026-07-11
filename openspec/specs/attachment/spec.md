# attachment · 能力规格

> 本规格定义「笔记正文 Markdown 中的附件内联渲染 + 缩略图点击放大查看」的能力契约。配套 `attachment-inline-render` change 实现。

## Purpose

把 `core/media/AttachmentStore` 已落盘的 `NoteAttachmentEntity` 关联的图片,在笔记详情屏正文 Markdown 中按 `![](attachment://<id>)` 语法渲染为可视缩略图,并提供全屏 lightbox 查看入口,使「在笔记里看到图片」成为内联体验而非独立附件行。
## Requirements
### Requirement: 解析 Markdown 中的 `attachment://` 链接

`AttachmentMarkdownParser.parse(content: String): List<MarkdownSegment>` 必须把含 `![](attachment://<id>)` 的 Markdown 字符串切分为 `MarkdownSegment` 序列。

- 仅匹配 `attachment://` scheme;其它 URL scheme(`https://` / `http://` / `file://` / 自定义)一律落入 `MarkdownSegment.Text` 段,保留原文
- `<id>` 必须满足 `[A-Za-z0-9_-]{1,64}`(与 `AttachmentStore.PathSafety.SAFE_ID` 一致);不合法 ID 落入 Text 段
- 空 content 返回空 list
- 无匹配的 content 返回 `[Text(content)]`

#### Scenario: 正文含单张附件链接

- **WHEN** 笔记 content = `"看这张 ![](attachment://abc123)"`
- **THEN** parse 返回 `[Text("看这张 "), AttachmentImage("abc123")]`

#### Scenario: 正文含不合法 scheme

- **WHEN** 笔记 content = `"外链 ![](https://example.com/foo.png)"`
- **THEN** parse 返回 `[Text("外链 ![](https://example.com/foo.png)")]`,**不**产生 AttachmentImage 段

#### Scenario: 正文含不合法 ID

- **WHEN** 笔记 content = `"![恶意](attachment://../../etc/passwd)"`
- **THEN** parse 返回 `[Text("![恶意](attachment://../../etc/passwd)")]`,原文保留不吞,不构成 path traversal 风险

#### Scenario: 正文无 attachment 链接

- **WHEN** 笔记 content = `"普通笔记,没有任何图片"`
- **THEN** parse 返回 `[Text("普通笔记,没有任何图片")]`,渲染行为与未升级前一致

### Requirement: 详情屏正文内联渲染缩略图

`InlineMarkdownText(content, attachmentDao, onAttachmentClick)` Composable 必须在 `QuickNoteDetailScreen` 笔记正文区域替换原 `Text(note.content)` 渲染。

- 切分后序列按垂直顺序排布(Column)
- `Text` 段直接 `Text(...)`,样式沿用 `MaterialTheme.typography.bodyLarge`
- `AttachmentImage` 段通过 `BitmapFactory.decodeFile(localPath, opts)` 解码(IO dispatcher + 256px `inSampleSize` + `RGB_565`),命中 `LruBitmapCache.instance`
- 解码失败 / 文件不存在 / IO 异常 → 96dp 灰色占位 + `R.string.quicknote_attachment_image_load_failed` 文案,**不**崩溃
- 缩略图 tap → 回调 `onAttachmentClick(attachmentId)`

#### Scenario: 缩略图渲染并可点击

- **WHEN** 详情屏加载一篇含 `![](attachment://<id>)` 的笔记,附件文件存在
- **THEN** 正文出现 96dp 圆角缩略图(非 raw 文本),点击触发 `onAttachmentClick(<id>)`

#### Scenario: 缩略图加载失败

- **WHEN** 附件文件被删除 / 损坏,正文仍有 `![](attachment://<id>)` 链接
- **THEN** 渲染为灰色占位 + 「图片加载失败」文案,详情屏其它功能(编辑 / AI / 删除)不受影响

#### Scenario: 正文无 attachment 链接的笔记

- **WHEN** 笔记 content 不含 `attachment://`
- **THEN** 详情屏正文渲染行为与未升级前完全一致(纯文本)

### Requirement: 缩略图点击打开全屏 lightbox

`onAttachmentClick(attachmentId)` 必须导航到 `AttachmentLightbox(attachmentId)` 全屏 Composable。

- 走 NavHost 新 route,不用 Dialog(避免 predictive back gesture 冲突)
- 全屏显示原图(`ContentScale.Fit`),黑色背景
- 顶栏「关闭」按钮(`R.string.quicknote_attachment_lightbox_close`)
- 底部 footer 显示文件名 + 格式化大小(`R.string.quicknote_attachment_lightbox_size_fmt`)
- 系统 back / predictive back gesture / 顶栏关闭按钮 三种方式都能关闭

#### Scenario: tap 缩略图进入 lightbox

- **WHEN** 用户在详情屏正文 tap 任一内联缩略图
- **THEN** 路由切换到 `AttachmentLightbox`,全屏显示该图片,顶栏可见「关闭」按钮

#### Scenario: predictive back 关闭 lightbox

- **WHEN** lightbox 处于栈顶,用户做系统 back 手势(或按返回键)
- **THEN** lightbox 关闭,返回到 `QuickNoteDetailScreen` 详情屏,详情屏正文缩略图仍在原位

#### Scenario: lightbox 单击关闭

- **WHEN** 用户点击 lightbox 黑色背景任意区域
- **THEN** lightbox 关闭并返回详情屏

### Requirement: i18n 字符串全部走 stringResource

正文内联渲染 + lightbox 涉及的所有用户可见文案必须走 `stringResource(R.string.xxx)`,**不**硬编码中英文。

- 涉及 keys:`quicknote_attachment_image_load_failed` / `quicknote_attachment_lightbox_close` / `quicknote_attachment_lightbox_size_fmt`
- zh(`values/strings.xml`) + en(`values-en/strings.xml`)双语齐全

#### Scenario: 切换系统语言到英文

- **WHEN** 用户在系统设置切换语言到 English 后打开笔记详情
- **THEN** 缩略图加载失败占位 / lightbox 关闭按钮文案均显示英文,无中文字符串残留

