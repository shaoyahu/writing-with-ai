# attachment-inline-render · 设计

## 1. 整体思路

正文 Markdown → **纯函数 `AttachmentMarkdownParser.parse(content: String): List<MarkdownSegment>`** 切成两段序列(纯文本 / 附件图)→ `InlineMarkdownText` Composable 按序渲染(纯文本走 `Text(...)`,附件图走 `produceState` 解码 → `Image(bitmap = ...)`)→ tap → Nav 跳转 `AttachmentLightbox(attachmentId)` 全屏查看。

**关键不引入**:
- 不引入 Coil / Glide(项目已有 `LruBitmapCache` + `BitmapFactory` 解码管线,M7 fix 验过)。
- 不引 CommonMark / Markwon(本 change 只解析 `attachment://` 一种 scheme,正则足够;M2 「note-markdown-render」再做完整 Markdown)。
- 不开 Compose Navigation 3D 转场动画(NavHost 默认 fade / slide 已够用)。

## 2. `AttachmentMarkdownParser`

位置:`core/media/AttachmentMarkdownParser.kt`。`data class MarkdownSegment` 两种:
```kotlin
sealed interface MarkdownSegment {
    data class Text(val raw: String) : MarkdownSegment
    data class AttachmentImage(val attachmentId: String) : MarkdownSegment
}
```

正则(放在 companion object):
```kotlin
private val ATTACHMENT_RE = Regex(
    """!\[([^\]]*)\]\(attachment://([A-Za-z0-9_\-]{1,64})\)"""
)
```

`parse(content)` 用 `findAll` 拿到 MatchResult 序列 + `range`,用 `content.substring` 切出 Text 段,MatchResult 段构 `AttachmentImage(groupValues[2])`(groupValues[1] 是 alt,丢弃;**不**当作显示文案,因为附件详情页 alt 通常空)。空 content → 空 list;无匹配 → `[Text(content)]`。

**为什么用 sealed interface 而非 Pair**:Text 和 AttachmentImage 渲染逻辑完全不同,Compose 一旦拿到 sealed,`when` 穷尽有编译器保证,未来加 `Link` / `Code` 段也好扩展(M2 留口子)。

## 3. `InlineMarkdownText` Composable

位置:`feature/quicknote/detail/InlineMarkdownText.kt`。签名:
```kotlin
@Composable
fun InlineMarkdownText(
    content: String,
    attachmentDao: NoteAttachmentDao,
    onAttachmentClick: (attachmentId: String) -> Unit,
    modifier: Modifier = Modifier
)
```

渲染策略:
- `remember(content) { AttachmentMarkdownParser.parse(content) }` —— content 变才重 parse,避免每次 recomposition 都跑正则。
- `Column` 垂直排布;`Text(...)` 段用 `MaterialTheme.typography.bodyLarge`,段间 4dp `Spacer`。
- `AttachmentImage` 段用 `produceState<Bitmap?>(initialValue = null, key1 = attachmentId)` + `LaunchedEffect` 在 `Dispatchers.IO` 上 `AttachmentStore.getAttachmentFile(noteId, attachmentId, "jpg")`(注:这里需要 noteId → 改签名加 noteId 参数,或从 `attachmentDao` 反查 `observeById(attachmentId)` 拿 `localPath` —— 后者更解耦,选择后者,见 §4)。`inSampleSize` 256px 目标,优先 `LruBitmapCache.instance.get(localPath)`,miss 则 decode 后 `put`。
- 渲染:`if (bitmap != null) Image(bitmap.asImageBitmap(), ..., modifier = Modifier.clickable { onAttachmentClick(attachmentId) })`;`else if (loaded)` 灰色 96dp `Box` + `stringResource(R.string.quicknote_attachment_image_load_failed)` 小字。**不**用 `CircularProgressIndicator`(避免快速解码场景闪一下,96dp 占位已经够不晃眼)。
- 缩略图尺寸:宽度 `fillMaxWidth()` 但高度自适应(`Modifier.aspectRatio(bitmap.width.toFloat() / bitmap.height)`);点击区域 96dp 圆角 8dp。

## 4. noteId 怎么传? —— `attachmentDao.observeById`

`InlineMarkdownText(content, attachmentDao, onClick)` 只拿 attachmentId,不知道 noteId。两种实现:

- **A 方案:签名加 noteId**:简单,但要求调用方(QuickNoteDetailScreen)传入,加耦合。
- **B 方案:从 attachmentDao 反查 localPath**:`NoteAttachmentDao.observeById(id)` 一次性 `Flow<NoteAttachmentEntity?>` `.first()` 拿 localPath。Dao 加一个轻量 query(`@Query("SELECT * FROM note_attachments WHERE id = :id LIMIT 1") fun observeById(id: String): Flow<NoteAttachmentEntity?>`)。

**选 B 方案**。`InlineMarkdownText` 不需要知道 noteId,逻辑内聚;`observeById` 是只读查询,与既有 `observeForNote` 平行,改动小。

## 5. `AttachmentLightboxScreen` + ViewModel

位置:`feature/quicknote/detail/AttachmentLightboxScreen.kt` + `AttachmentLightboxViewModel.kt`。

**架构**:用 `SavedStateHandle.get<String>("id")` 拿 attachmentId,ViewModel 内 `noteAttachmentDao.observeById(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)` 暴露 `attachment: StateFlow<NoteAttachmentEntity?>`。Screen 拿到 attachment 后 `produceState<Bitmap?>` 解码,这次**不**缩(inSampleSize 算原图原始尺寸,内存不够时 fallback 1024px 上限)。

**为什么不放成 Dialog**:
- Dialog 内的 back 行为在 Compose 1.5+ 与 predictive back gesture 冲突(需要手动 `BackHandler` 拦截,既不优雅也容易跟系统手势冲突)。
- NavHost route 自带 back stack + predictive back,既符合 Android 14+ 设计也兼容老版本。
- 注册方式:`@Serializable data class AttachmentLightbox(val id: String)` 加到 `AppNav.kt`,在根 NavHost 注册 `composable<AttachmentLightbox> { AttachmentLightboxScreen(...) }`。tap 时 `navController.navigate(AttachmentLightbox(id = attachmentId))`。

**顶栏 + 内容布局**:
- `Scaffold` 黑色背景 + `TopAppBar`(关闭 icon + 文件名,`topAppBarColors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White)`)。
- 内容 `Box(Modifier.fillMaxSize().clickable { /* 单击关闭 */ })` + `Image(bitmap.asImageBitmap(), ..., Modifier.fillMaxSize(), contentScale = ContentScale.Fit)`。
- 底部 footer `Surface(Color.Black.copy(alpha = 0.6f))` 显示 `${fileName} · ${formatSize(fileSize)}`,`formatSize` 复用 `core/media/AttachmentStore` 旁边的 `FormatUtils.formatFileSize`(若没就新建一个 internal util)。
- v1 **不**加 pinch-to-zoom(推迟 v1.1,见 proposal §"Scope Decisions")。

## 6. 路由集成

`app/AppNav.kt`:
- 顶部 `@Serializable` import 旁加 `@Serializable data class AttachmentLightbox(val id: String)`。
- `NavHost` block 内追加:
```kotlin
composable<AttachmentLightbox> {
    AttachmentLightboxScreen(onClose = { navController.popBackStack() })
}
```
- `QuickNoteDetailScreen` 内 `InlineMarkdownText(..., onAttachmentClick = { id -> navController.navigate(AttachmentLightbox(id = id)) })`。

## 7. 国际化

`app/src/main/res/values/strings.xml` + `values-en/strings.xml` 各加 3 个 key:

| key | zh | en |
| --- | --- | --- |
| `quicknote_attachment_image_load_failed` | 图片加载失败 | Failed to load image |
| `quicknote_attachment_lightbox_close` | 关闭 | Close |
| `quicknote_attachment_lightbox_size_fmt` | `%1$s · %2$s` | `%1$s · %2$s` |

**不**在 Composable 里硬编码中文。

## 8. 测试

`core/media/AttachmentMarkdownParserTest.kt`(JVM 单测,纯函数,不依赖 Android framework):
1. **空字符串** → 空 list。
2. **纯文本无 attachment** → `[Text("hello world")]`。
3. **单张附件** `![](attachment://abc123)` → `[Text(""), AttachmentImage("abc123")]`,但 Text 段若仅是空字符串可优化掉 → 实际输出 1 段 `AttachmentImage("abc123")`。
4. **混合** `before ![](attachment://xyz) after` → `[Text("before "), AttachmentImage("xyz"), Text(" after")]`。
5. **不合法 scheme** `![](https://example.com/foo.png)` → `[Text(...raw...)]`,**不**解析成 AttachmentImage(避免用户误输入被吞)。
6. **不合法 ID** `![](attachment://BAD/ID)`(含特殊字符)→ 原样保留在 Text 段。

`produceState` 渲染路径(IO + BitmapFactory)走 Android instrumented test 太重,v1 不覆盖,代码评审(`QuickNoteDetailScreen.kt:651-700` 已有的 LazyRow 解码模式可直接复用)覆盖。

## 9. 性能 / 内存

- **解码**:复用既有 `QuickNoteDetailScreen.kt:651-700` 的 `inSampleSize` 算法(`reqWidth = 256`,计算 `inSampleSize = max(1, srcWidth / reqWidth)`),`inPreferredConfig = RGB_565`。
- **缓存**:所有解码后 `LruBitmapCache.instance.put(localPath, bitmap)`,64MB 封顶(M7 fix)。
- **正文加载**:解析在 `remember(content)`,content 不变不重 parse。Bitmap 解码在 `LaunchedEffect(attachmentId)`,段级独立,滚动/重组不影响。
- **lightbox**:原图若 > 4MB 仍可能 OOM,**降级策略**:`inSampleSize` 算到 ≤ 1024px,显示「图片过大,显示缩小版本」副文案;留 v1.1 全屏 pinch 时再优化。

## 10. 迁移 / 兼容

- **现有数据**:已经在 LazyRow 显示的附件不需要重写;只新增正文内联渲染,新字段零迁移。
- **现有笔记**:无 `attachment://` 的笔记渲染行为零变化(纯文本)。
- **APK 升级**:无 schema 变更,无需 `AppDatabase` 升级。
- **降级路径**:若 `LruBitmapCache` 满了,旧图 LRU 回收,新 decode 走原始路径,行为对齐已有 LazyRow。

## 11. 安全 / 隐私

- **path safety**:`AttachmentMarkdownParser` 的正则 `[A-Za-z0-9_-]{1,64}` 与 `AttachmentStore.PathSafety.SAFE_ID` 完全一致;即便用户恶意写入 `attachment://../../etc/passwd` 也会被正则 fail,落入 Text 段保留原文,不构成 path traversal。
- **Prompt injection 防御**:此 change 不涉及 AI 调用,无 prompt 拼接风险。
- **logcat**:`InlineMarkdownText` 解码失败**不**打 log(release 包防信息泄露);DEBUG 包可在 `produceState` catch 内 `Log.w`,沿用既有 LazyRow 的写法。

## 12. 不做

- 不做 pinch-to-zoom(v1.1)。
- 不做普通 Markdown(粗体 / 列表 / 链接)解析(M2)。
- 不做 GIF 动画 / 视频内联(独立 change)。
- 不改 Editor picker(B6a 已闭环)。
- 不改 List row 缩略图(`note-list-thumbnail` 已闭环)。
- 不引新依赖。