# attachment-inline-render · 任务清单

## Phase 1: 解析 + 数据层

- [x] 1. **新增 `NoteAttachmentDao.observeById`**
   - 文件:`core/data/db/dao/NoteAttachmentDao.kt`
   - 内容:`@Query("SELECT * FROM note_attachments WHERE id = :id LIMIT 1") fun observeById(id: String): Flow<NoteAttachmentEntity?>`
   - 验证:Room 编译通过,`:app:assembleDebug` 绿

- [x] 2. **新增 `AttachmentMarkdownParser` + `MarkdownSegment`**
   - 文件:`core/media/AttachmentMarkdownParser.kt`
   - 内容:`sealed interface MarkdownSegment { data class Text(...); data class AttachmentImage(val attachmentId: String) }` + `object AttachmentMarkdownParser { fun parse(content: String): List<MarkdownSegment> }` + `private val ATTACHMENT_RE = Regex("""!\[([^\]]*)\]\(attachment://([A-Za-z0-9_\-]{1,64})\)""")`
   - 验证:正则 `attachment://` 必须命中 + 非 scheme 不命中 + 不合法 ID 落入 Text

- [x] 3. **写 `AttachmentMarkdownParserTest`**
   - 文件:`core/media/AttachmentMarkdownParserTest.kt`
   - 5 case:空字符串 / 纯文本 / 单附件 / 混合 / 不合法 scheme 不误伤
   - 验证:`./gradlew :app:testDebugUnitTest --tests *AttachmentMarkdownParserTest` 全绿

## Phase 2: 详情屏内联渲染

- [x] 4. **新增 `InlineMarkdownText` Composable**
   - 文件:`feature/quicknote/detail/InlineMarkdownText.kt`
   - 内容:`remember(content) { AttachmentMarkdownParser.parse(content) }` + `Column` 排布 + Text 段 `Text(...)` + AttachmentImage 段 `produceState<Bitmap?>` + IO decoder + 256px inSampleSize + `LruBitmapCache.instance.get/put` + `Modifier.clickable { onAttachmentClick(attachmentId) }` + 失败占位
   - 验证:Compose preview 渲染 + 真机加载附件笔记看到缩略图

- [x] 5. **改造 `QuickNoteDetailScreen` 正文渲染**
   - 文件:`feature/quicknote/detail/QuickNoteDetailScreen.kt`
   - 替换正文 `Text(note.content, ...)` 为 `InlineMarkdownText(content = note.content, attachmentDao = attachmentDao, onAttachmentClick = { id -> navController.navigate(AttachmentLightbox(id = id)) })`
   - 保留下方 LazyRow 不动
   - 验证:`./gradlew :app:assembleDebug` 绿 + 真机看到内联缩略图

## Phase 3: Lightbox

- [x] 6. **新增 `AttachmentLightboxViewModel`**
   - 文件:`feature/quicknote/detail/AttachmentLightboxViewModel.kt`
   - 内容:`@HiltViewModel`,`SavedStateHandle.get<String>("id")` + `attachmentDao.observeById(id).stateIn(viewModelScope, WhileSubscribed(5_000), null)` 暴露 `attachment: StateFlow<NoteAttachmentEntity?>`
   - 验证:Hilt 编译通过

- [x] 7. **新增 `AttachmentLightboxScreen`**
   - 文件:`feature/quicknote/detail/AttachmentLightboxScreen.kt`
   - 内容:`Scaffold` 黑色背景 + TopAppBar 关闭按钮 + Box + Image(contentScale = Fit) + 底部文件名/大小 footer + `produceState<Bitmap?>` 解码(1024px 上限,内存降级) + 单击关闭
   - 验证:Compose preview + 真机 tap 缩略图进入全屏,back 关闭

- [x] 8. **注册 Nav route `AttachmentLightbox`**
   - 文件:`app/AppNav.kt`
   - 顶部 `@Serializable data class AttachmentLightbox(val id: String)` + `composable<AttachmentLightbox> { AttachmentLightboxScreen(onClose = { navController.popBackStack() }) }`
   - 验证:`./gradlew :app:assembleDebug` 绿 + 真机 back gesture 关闭

## Phase 4: i18n + 收尾

- [x] 9. **新增 3 个字符串 key × 2 语言**
   - 文件:`app/src/main/res/values/strings.xml` + `app/src/main/res/values-en/strings.xml`
   - keys:`quicknote_attachment_image_load_failed` / `quicknote_attachment_lightbox_close` / `quicknote_attachment_lightbox_size_fmt`
   - 验证:grep 中文硬编码 0 命中

- [x] 10. **全量 check**
    - `./gradlew :app:ktlintCheck` 0 error
    - `./gradlew :app:testDebugUnitTest` 全绿(含新增 `AttachmentMarkdownParserTest`)
    - `./gradlew :app:assembleDebug` 出 APK
    - 真机:详情屏 → 含 `![](attachment://<id>)` 笔记 → 看到缩略图 → tap → lightbox → back gesture 关闭

## 估计

- Phase 1:0.5 天(parser + 单测)
- Phase 2:0.5 天(InlineMarkdownText + 详情屏接入)
- Phase 3:0.5 - 1 天(lightbox + Nav 集成)
- Phase 4:0.25 天(i18n + 自检 + review)
- **总计:约 1.5 - 2 天**