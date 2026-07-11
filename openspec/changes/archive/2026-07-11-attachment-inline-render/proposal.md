# attachment-inline-render · 笔记正文内联渲染附件缩略图

## Why

B6a `media-attachment-infrastructure`(2026-06-23 归档)已经在 `core/media/` 落地 `NoteAttachmentEntity` / `AttachmentStore` / `ImageCompressor` / `LruBitmapCache`,`feature/quicknote/detail/QuickNoteDetailScreen.kt` 也已实现「内容下方 LazyRow + 80dp 缩略图 + 系统 picker 添加按钮」的三件套(见 `QuickNoteDetailScreen.kt:651-713`)。

但有两个产品级缺口未填:

1. **正文 Markdown 不解析 `![](attachment://<id>)`**:用户在编辑器粘贴或编辑时,业务路径只会把图片作为独立附件行落入正文外 LazyRow;正文中若出现 `attachment://` 链接(导入飞书 doc 时,`MarkdownToDocxConverterImpl` 已知会落地 `assets/img/...` 形式的 path,后续若切换到本机附件,正文就需要本地化解析),目前会被原样显示成 `![photo](attachment://xxx)`,用户看到的是 raw 文本而非图。
2. **缩略图点击没有放大入口**:用户期望「看到缩略图 → 点开大图」的自然链路;详情屏当前 tap 无反馈,无法验证图片是否符合预期,也不方便分享/复核。

`note-list-thumbnail`(2026-07-03 归档)已经实现列表行缩略图(`NoteRow.kt:88 firstImagePath` + 72dp `LruBitmapCache.instance.get`),不在本 change 范围内。

## What Changes

- 新增 `AttachmentMarkdownParser`(`core/media/` 下),把 `![](attachment://<id>)` 切分成 `List<MarkdownSegment>`(Text / AttachmentImage);正则 `!\[[^\]]*\]\(attachment://([A-Za-z0-9_-]{1,64})\)`,严格匹配 attachment store 的 `PathSafety.SAFE_ID`,**不**误伤其它 URL scheme。
- `QuickNoteDetailScreen` 正文用新 `InlineMarkdownText` Composable 渲染:Text 段直接 `Text(...)`;AttachmentImage 段用 `produceState` + `BitmapFactory.decodeFile(path, opts)` + IO dispatcher + `inSampleSize` 缩到 256px,命中 `LruBitmapCache.instance`,失败回退灰色占位 + `quicknote_attachment_image_load_failed` 文案。
- 缩略图点击 → 新 Nav 路由 `AttachmentLightbox(attachmentId)`,全屏 `Image(...)` + 顶栏关闭按钮 + 底部文件名/大小 + **Android predictive back gesture**(依赖既有 `enableOnBackInvokedCallback=true`)。
- 详情屏内容外 LazyRow **保留**(附件总览),新增的正文内联渲染是「正文里出现过的 attachment 也能 tap 放大」,两者并存不冲突。
- **新增字符串**:`quicknote_attachment_image_load_failed` / `quicknote_attachment_lightbox_close` / `quicknote_attachment_lightbox_size_fmt`(双语 zh + en)。
- **不**改 schema / 不改 AttachmentStore / 不改 ImageCompressor / 不改 Editor picker(详见 §"Scope Decisions")。

## Capabilities

### New Capabilities

- `attachment`:定义「正文 Markdown 解析 + 内联缩略图渲染 + lightbox 全屏查看」三件套的能力契约,带 4 个 Requirement + 每 Requirement 1-2 Scenario。

### Modified Capabilities

- 无(`quick-note` spec 已有 detail/edit/list 子项,正文渲染契约归 `attachment` capability 更合适)。

## Scope Decisions

| 候选 | 决定 | 理由 |
| --- | --- | --- |
| **列表行缩略图** | **非 v1**(已实现) | `NoteRow.kt:88` 已接 `firstImagePath`,2026-07-03 `note-list-thumbnail` 归档 |
| **编辑器附件 picker** | **非 v1**(已实现) | `QuickNoteEditorScreen.kt:89-91` 已有 `ActivityResultContracts.PickVisualMedia(ImageOnly)` + `QuickNoteEditorViewModel.addAttachment/pendingAttachmentUris/commitAttachment`,B6a 已闭环 |
| **Lightbox pinch-to-zoom / 双指缩放** | **推迟到 v1.1** | v1 范围 1.5 天,pinch 需 `Modifier.transformable` + 自定义缩放状态机(再 +1~2 天),且 99% 场景「看图 + 关闭」够用;v1.1 单独开 change |
| **正文普通 Markdown(粗体 / 列表 / 链接)** | **非本 change** | 路线图 M2 的「note-markdown-render」范围;本 change 只解析 `attachment://` 这一个 scheme |
| **GIF / 视频内联** | **非 v1 / 非 v1.1** | `ImageCompressor` 只处理 JPEG/GIF → JPEG;GIF 动画 / 视频要走 `androidx.media3` 或 Glide,独立 change |

## Impact

- 新增 1 个文件 `core/media/AttachmentMarkdownParser.kt`(~60 行,纯函数 + data class,无依赖注入)
- 新增 1 个文件 `core/media/AttachmentMarkdownParserTest.kt`(JVM 单测,5 case)
- 新增 1 个文件 `feature/quicknote/detail/InlineMarkdownText.kt`(~120 行,Composable)
- 新增 1 个文件 `feature/quicknote/detail/AttachmentLightboxScreen.kt`(~80 行,Composable + ViewModel)
- 新增 1 个文件 `feature/quicknote/detail/AttachmentLightboxViewModel.kt`(~40 行,@HiltViewModel)
- 修改 1 个文件 `feature/quicknote/detail/QuickNoteDetailScreen.kt`(正文 Text 替换为 InlineMarkdownText;新增 navController.navigate(AttachmentLightbox(id)))
- 修改 1 个文件 `app/AppNav.kt`(新增 `@Serializable data class AttachmentLightbox(val id: String)` + `composable<AttachmentLightbox>` 注册)
- 修改 2 个文件 `app/src/main/res/values/strings.xml` + `values-en/strings.xml`(3 个 key × 2 lang)
- 不引入新依赖(纯 Kotlin + Compose,BitmapFactory + LruBitmapCache 已在 core)
- 不动 schema / 不动 AttachmentStore / 不动 ImageCompressor / 不动 Editor picker
- 真机验证路径:装 APK → 写一条含 `![](attachment://<id>)` 的笔记(直接编辑 content 文本) → 详情屏看到内联缩略图 + 点开 lightbox → predictive back 关闭

## Acceptance Criteria

1. 正文中 `![](attachment://<id>)` 在详情屏渲染为 96dp 圆角缩略图(非 raw 文本),其它段落样式不变。
2. 缩略图 tap → 进入全屏 lightbox,显示原始比例图片,顶栏「关闭」按钮可用,系统 back / gesture back 也能关闭(走 NavHost 标准 back 栈,**不**用 Dialog 避免回弹问题)。
3. 图片文件不存在 / 解码失败 / IO 异常 → 灰色占位 + 「图片加载失败」文案,**不**崩溃。
4. 正文无 `attachment://` 链接的笔记,渲染行为与 v0 完全一致(纯文本显示)。
5. LazyRow 下方「附件总览」保持原状,与正文内联渲染并存,不重复加载同一张图(都走 `LruBitmapCache`)。
6. JVM 单测 `AttachmentMarkdownParserTest` 5 case 全绿,正则匹配 + 非 attachment scheme 不误伤。
7. `:app:assembleDebug` + `:app:ktlintCheck` + `:app:testDebugUnitTest` 全绿,无新增 warning。

## Risks

1. **正则误伤**:用户正文可能误输入 `![pic](attachment://abc)` 这种不带 ID 的尝试。**缓解**:`SAFE_ID` 严格匹配 `^[A-Za-z0-9_-]{1,64}$`,`AttachmentStore` 已用同款校验;不合法 ID 直接走 Text 段显示原文本,绝不 crash。
2. **大图 OOM**:正文里可能塞多张大图。**缓解**:`inSampleSize` 计算 + 256px 目标 + `BitmapFactory.Options.inPreferredConfig = RGB_565` + `LruBitmapCache` 64MB 封顶(M7 fix);复用既有 LazyRow 的解码模式,行为对齐。