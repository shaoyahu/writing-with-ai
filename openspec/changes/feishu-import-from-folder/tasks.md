# Tasks · feishu-import-from-folder

> 实现顺序:**后端先行**(API client → service → image downloader)→ **UI 后行**(Screen → ViewModel → Nav 路由 → 列表页 dropdown 集成)。
> 验证:每完成一段跑 `./gradlew :app:assembleDebug` + `:app:testDebugUnitTest` + `:app:ktlintCheck`。

---

## Phase 1:数据层 + API 扩展

- [x] **T1.1** 在 `core/feishu/api/FeishuApiClient.kt` 加 `suspend fun listFolder(folderToken: String, pageSize: Int = 50, pageToken: String? = null): ListFolderResponse` 接口方法
- [x] **T1.2** 在 `FeishuApiClientImpl.kt` 实现 listFolder(`GET /open-apis/drive/v1/files`),响应解析为 `ListFolderResponse(files: List<FolderFileEntry>, nextPageToken: String?, hasMore: Boolean)`
- [x] **T1.3** 新建 `FolderFileEntry` data class(name/token/type/url/createdTime/modifiedTime/ownerId/parentToken),字段名严格 snake_case 跟飞书响应一致
- [x] **T1.4** 在 `docs/usage/api-feishu.md` 加 §4.4 listFolder 章节,字段表 + 错误码 + 调用示例

**Phase 1 验证**:`./gradlew :app:assembleDebug`

---

## Phase 2:解析 + 同步状态枚举

- [x] **T2.1** 新建 `core/feishu/sync/FeishuInputParser.kt`(object + 纯函数):
  - `sealed class ParsedToken { RawToken/Folder/Doc/UnsupportedHost/Malformed }`
  - `fun parse(input: String): ParsedToken`
  - 单元测试覆盖所有 case(链接 5 种 + 直接 token 4 种 + 海外版 2 种 + 空)
- [x] **T2.2** 在 `core/data/db/entity/SyncStatus.kt` 加 `PARTIAL_IMPORT_FAIL` 枚举值
- [x] **T2.3** 在 `core/feishu/sync/FeishuRefStatus.kt` 加 `PARTIAL_IMPORT_FAIL` 枚举值
- [x] **T2.4** sanity check SyncStatus 的 Room `@TypeConverter`:若 converter 是 free-string 映射则无需改;若是 if-else 枚举映射则要补 case

**Phase 2 验证**:`./gradlew :app:testDebugUnitTest --tests "com.yy.writingwithai.core.feishu.sync.FeishuInputParserTest"`

---

## Phase 3:图片下载器

- [x] **T3.1** 新建 `core/feishu/sync/FeishuImageDownloader.kt`:
  - 顶层 val:`MD_IMG`,`HTML_IMG` 两个正则
  - `data class DownloadResult(val updatedMarkdown: String, val attachments: List<NoteAttachmentEntity>, val failedUrls: List<String>)`
  - `suspend fun downloadAndInline(markdown: String, noteId: String, httpClient: OkHttpClient): DownloadResult`
  - 内部 `extractUrls(markdown): List<String>`(去重保留首次出现顺序)
  - 内部 `downloadOne(url, noteId, client): DownloadedImage?`(捕获所有异常返回 null)
  - 内部 `replaceInMarkdown(markdown, urlMap): String`(url → 本地路径或占位符)
- [x] **T3.2** 实现 Content-Type → 扩展名推断(`png/jpeg/webp/gif` + fallback `img`)
- [x] **T3.3** 实现 20MB 限制:有 Content-Length → 预判;无 → 流式计数
- [x] **T3.4** 单图失败时:占位符 + 返回 failedUrls(由 ImportService 写 IMAGE_FAIL_PARTIAL event)
- [x] **T3.5** 请求加 `X-No-Auth-Retry: 1` 头(测试:截图 NetworkLog 确认没 Authorization 头)
- [x] **T3.6** 单元测试:`extractUrls` 各种 markdown/HTML 混合 case

**Phase 3 验证**:`./gradlew :app:testDebugUnitTest --tests "com.yy.writingwithai.core.feishu.sync.FeishuImageDownloaderTest"`

---

## Phase 4:导入 Service

- [x] **T4.1** 新建 `core/feishu/sync/FeishuImportService.kt`:
  - `@Singleton` + `@Inject` 注入全部依赖
  - `data class DocSummary(token, title, url)`
  - `sealed class ImportResult { Success(noteId) / Failure(reason) }`
  - `data class ImportSummary(totalRequested, successCount, failureCount, partialCount, failedDocTokens)`
  - `suspend fun ensureAuthorized(): Boolean`(`getAccessTokenSnapshot() != null`)
  - `suspend fun importSingleDoc(input: String): ImportResult`
  - `suspend fun listFolderDocs(input: String): List<DocSummary>`(parse → resolveFolderToken → listFolder → filter type=docx)
  - `suspend fun importFolderDocs(folderToken: String, docTokens: List<String>, onProgress: (Int, Int) -> Unit): ImportSummary`(串行 + 每篇后 sleep 200ms + CancellationException 不吞)
- [x] **T4.2** 实现 `importSingleDoc` 完整流程(design §5.4):
  - title 来源优先级: `getDocument().title` → markdown 首行 `# xxx` → "未命名笔记"
  - content = updatedMarkdown + `\n\n---\n来源飞书: ` + docUrl
  - syncStatus 由 imageDownloader.failedUrls 是否空决定(PARTIAL_IMPORT_FAIL / SYNCED)
  - tags = `["feishu"]`
  - 失败时记 FeishuSyncEvent IMAGE_FAIL_PARTIAL(每个失败图一条)
- [x] **T4.3** 单测:`importSingleDoc` mock 各依赖 → 验证写库 + ref 行 + event 写入

**Phase 4 验证**:`./gradlew :app:assembleDebug`

---

## Phase 5:Nav 路由

- [x] **T5.1** 在 `app/AppNav.kt` 注册新路由 `feishu_folder_import`(`composable("feishu_folder_import") { FolderImportScreen(...) }`)
- [x] **T5.2** 在 `feature/quicknote/list/QuickNoteListScreen.kt` 加 `onNavigateToFolderImport: () -> Unit` 形参
- [x] **T5.3** 在 `app/AppShell.kt`(或 NavHost 装配点)把 `onNavigateToFolderImport` 接到 navigate("feishu_folder_import")

**Phase 5 验证**:`./gradlew :app:assembleDebug`

---

## Phase 6:FolderImport 全屏 sub-screen

- [x] **T6.1** 新建 `feature/feishuimport/FolderImportViewModel.kt`:
  - `@HiltViewModel`
  - 注入 `FeishuImportService`
  - `sealed class FolderImportState { Input / Loading / Loaded(docs) / Error(reason) }`
  - `private val _state = MutableStateFlow<FolderImportState>(Input)`
  - `fun onParse(input: String)` → 调 `listFolderDocs` → Loading → Loaded/Error
  - `suspend fun importSelected(tokens: List<String>, onProgress: (Int, Int) -> Unit): ImportSummary`
- [x] **T6.2** 新建 `feature/feishuimport/FolderImportScreen.kt`:
  - `@Composable fun FolderImportScreen(onBack: () -> Unit, viewModel: FolderImportViewModel = hiltViewModel())`
  - 顶部 TopAppBar:返回箭头 + 标题"从文件夹导入"
  - Input 态:`OutlinedTextField` + "解析"按钮
  - Loading 态:`CircularProgressIndicator`(居中) + 下半 `NoteListSkeleton`(复用 `core/ui/NoteListSkeleton.kt`)
  - Loaded 态:顶部 chip "仅显示 docx 类型(共 N 篇)" + `LazyColumn`(checkbox + title + url)+ 底部"导入(已选 N)"按钮
  - Error 态:图标 + 文案 + "重试"按钮
- [x] **T6.3** 处理空结果:`docs.isEmpty()` → 显示"未找到 docx 文档"空状态
- [x] **T6.4** 处理多选 state(`mutableStateOf<Set<String>>(emptySet())`)

**Phase 6 验证**:`./gradlew :app:assembleDebug`

---

## Phase 7:列表页 dropdown + 文档导入 Dialog

- [x] **T7.1** `QuickNoteListScreen.kt:132` TopAppBar 加 `actions`:
  - `IconButton` + `Icons.Filled.CloudDownload`(导入图标)
  - 点击 → `importMenuExpanded = true`
  - `AppActionDropdown` 弹两个 items:`"从文档导入"` / `"从文件夹导入"`
- [x] **T7.2** 文档导入 Dialog:
  - state:`var showImportDocDialog by remember { mutableStateOf(false) }`
  - state:`var importInput by remember { mutableStateOf("") }`
  - `AlertDialog` + `OutlinedTextField` + 确认 / 取消
  - 确认 → 调 ViewModel 的 `importSingleDoc(input)` → 弹结果 Toast / Snackbar
- [x] **T7.3** 未授权检测:
  - `QuickNoteListViewModel` 注入 `FeishuAuthStore`(若未注入则加)
  - 暴露 `authState: StateFlow<FeishuAuthState>`
  - Composable 监听,未授权时 dropdown item 点击 → 弹错误 AlertDialog(标题"未授权飞书账号" + 内容 + "去授权"/"取消"按钮)
  - "去授权"按钮 → navigate 到"我的"tab(`navigate("my")` 或 equivalent)
- [x] **T7.4** 导入完成后通知刷新:
  - 用 `MutableSharedFlow<Unit>` 通知列表刷新(已有 `noteUpdateEvents` 模式可参考)
  - 或直接靠 Room Flow 自动刷新(不强制 push)

**Phase 7 验证**:`./gradlew :app:assembleDebug`

---

## Phase 8:列表卡片 syncStatus chip 渲染

- [x] **T8.1** 定位 `NoteRow.kt`(或在 `QuickNoteListScreen.kt` 内部)的 chip 渲染分支
- [x] **T8.2** 加 `FeishuRefStatus.PARTIAL_IMPORT_FAIL` case:
  - 文案:"部分失败"
  - 颜色:`MaterialTheme.colorScheme.tertiary`
- [x] **T8.3** 字符串走 `R.string.quicknote_list_sync_status_partial_import_fail`(新增)
- [x] **T8.4** 颜色走 `MaterialTheme.colorScheme.tertiary`(新增 token,如未定义)

**Phase 8 验证**:`./gradlew :app:assembleDebug`

---

## Phase 9:批量导入 Loading + 结果 Dialog

- [x] **T9.1** 在 `QuickNoteListScreen` 加 state:
  - `var batchImportProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }` — null 表示未在进行
  - `var batchImportSummary by remember { mutableStateOf<ImportSummary?>(null) }`
- [x] **T9.2** 加载 dialog:`AlertDialog` 不可取消 + `CircularProgressIndicator` + 文本 "正在导入 i/N"
- [x] **T9.3** 结果 dialog:`AlertDialog` + 文本 "成功 X / 部分失败 Y / 失败 Z" + "知道了"按钮
- [x] **T9.4** "从文件夹导入"完成返回时:FolderImportScreen 触发 navigate back + 传回 ImportSummary → QuickNoteListScreen 接收并显示
  - 用 `NavBackStackEntry.savedStateHandle` 或 Nav arg 传值

**Phase 9 验证**:`./gradlew :app:assembleDebug`

---

## Phase 10:字符串 + 文案

- [x] **T10.1** `app/src/main/res/values/strings.xml` 新增:
  - `quicknote_list_import_menu_title`(dropdown 标题)
  - `quicknote_list_import_from_doc`(从文档导入)
  - `quicknote_list_import_from_folder`(从文件夹导入)
  - `quicknote_list_import_dialog_title`
  - `quicknote_list_import_dialog_hint`
  - `quicknote_list_import_unauthorized_title`
  - `quicknote_list_import_unauthorized_msg`
  - `quicknote_list_import_unauthorized_go_auth`
  - `quicknote_list_sync_status_partial_import_fail`(部分失败)
  - `folder_import_screen_title`(从文件夹导入)
  - `folder_import_input_hint`
  - `folder_import_parse_button`
  - `folder_import_chip_docx_only`(仅显示 docx 类型)
  - `folder_import_empty_no_docx`
  - `folder_import_import_button_fmt`(导入(已选 %d))
  - `folder_import_loading_fmt`(正在导入 %d/%d)
  - `folder_import_result_fmt`(成功 %d / 部分失败 %d / 失败 %d)
  - `folder_import_error_retry`
  - `folder_import_error_unsupported_host`(不支持海外版)

**Phase 10 验证**:`./gradlew :app:ktlintCheck`

---

## Phase 11:集成测试 + 端到端验证

- [x] **T11.1** `./gradlew :app:assembleDebug` —— 编译通过
- [x] **T11.2** `./gradlew :app:testDebugUnitTest` —— 所有单测过(新增 + 旧的)
- [x] **T11.3** `./gradlew :app:ktlintCheck` —— ktlint 无错
- [x] **T11.4** `./gradlew :app:installDebug` + adb 启动应用
- [x] **T11.5** 手动验收:
  - 笔记 tab 右上角有导入图标
  - 点 dropdown → 两个选项
  - 未授权时点 → 弹"未授权"dialog + "去授权"按钮
  - 授权后输入 1 篇 docx 链接 → 列表新增 1 条笔记 + tag=`feishu` + 末尾脚注
  - 输入文件夹链接 → 跳 sub-screen → 列 docx → 多选 → 批量导入 → 列表新增 N 条
  - 含图片的 docx → 笔记显示图片 + 第一张图缩略图(列表)
  - 部分图片下载失败的 docx → 笔记卡片显示"部分失败"chip

**Phase 11 验收清单** —— 全部勾完才算 change 完成。

---

## 不做

(留作 followup,本期不做)

- 增量同步 / 去重
- 重试失败图片按钮
- 已导入历史列表
- sheet/bitable/doc 解析
- 海外 larksuite 支持
- markdown 实时预览编辑(sub-screen 只读展示)