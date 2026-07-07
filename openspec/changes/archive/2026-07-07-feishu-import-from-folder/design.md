# Design · feishu-import-from-folder

## 1. 目标与边界

实现"飞书 docx 批量导入本地笔记",**不**涉及 push 流程改动。

| 输入 | 行为 |
|---|---|
| 1 篇文档链接/token | 弹 AlertDialog → 输入 → 直接拉取 → 落 1 条笔记 |
| 1 个文件夹链接/token | 跳全屏 sub-screen → 解析 → 列 docx 清单 → 多选 → 批量串行拉取 → 落 N 条笔记 |

每篇落库都建 `feishu_ref`(noteId ↔ docId),为详情页后续 push/pull 留入口。

## 2. 飞书 API

### 2.1 新增 `listFolder(folderToken, pageSize=50, pageToken=null)`

`GET /open-apis/drive/v1/files?folder_token={token}&page_size={n}&page_token={t}`

响应:

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "files": [
      {
        "name": "标题",
        "token": "doccnXXX 或 docxXXX",
        "type": "docx | doc | sheet | bitable | folder | file | ...",
        "url": "https://my.feishu.cn/docx/doccnXXX",
        "created_time": "1700000000",
        "modified_time": "1700000000",
        "owner_id": "ou_xxx",
        "parent_token": "fldcnXXX"
      }
    ],
    "next_page_token": "xxx",
    "has_more": false
  }
}
```

**字段名严格照搬飞书响应**(snake_case,跟已有代码一致),不做驼峰转换。

### 2.2 文件 token 类型速查(对照 §5 api-feishu.md)

| 前缀 | 类型 | 列表 type 字段 | 能否做 `listFolder` 的 folder_token |
|---|---|---|---|
| `fldcn` | folder | `"folder"` | ✅ |
| `wikcn` | wiki | `"wiki"` | ❌ 需 batch_query 解析 → folder token(走现有 `resolveFolderToken`) |

如果用户输入 folder token 时带的是 `wikcn`,先调 `resolveFolderToken` 解析。

## 3. 用户输入解析

新增 `FeishuInputParser.parse(input: String): ParsedToken`(纯函数,放 `core/feishu/sync/`):

```kotlin
sealed class ParsedToken {
    data class RawToken(val token: String) : ParsedToken()    // fldcnXXX / wikcnXXX / docxXXX / doccnXXX
    data class Folder(val token: String) : ParsedToken()      // 解析后
    data class Doc(val token: String) : ParsedToken()
    data class UnsupportedHost(val host: String) : ParsedToken()
    data class Malformed(val reason: String) : ParsedToken()
}
```

规则:

| 输入 | 解析结果 |
|---|---|
| `https://my.feishu.cn/drive/folder/Z8BnXXX` | `Folder("Z8BnXXX")` |
| `https://my.feishu.cn/wiki/PwKTXXX` | `Folder("PwKTXXX")`(经 resolveFolderToken 后才确定) |
| `https://my.feishu.cn/docx/doccnXXX` | `Doc("doccnXXX")` |
| `fldcnXXX` / `wikcnXXX` / `docxXXX` / `doccnXXX` | `RawToken(...)` |
| `https://larksuite.com/...` | `UnsupportedHost("larksuite.com")` |
| 其他非 feishu.cn host | `UnsupportedHost(...)` |
| 空 / 全空白 | `Malformed("empty")` |

> `wiki` / `doc` 链接统一走 `resolveFolderToken` 或 `Doc`,不区分 —— 后续 API 自己判定。

## 4. 图片下载

### 4.1 解析 markdown 图片引用

正则(Java `Pattern` 编译一次,放 `FeishuImageDownloader` 顶层 val):

```kotlin
// markdown: ![alt](url)
private val MD_IMG = Regex("""!\[([^\]]*)\]\(([^)]+)\)""")
// html: <img ... src="url" ...> (支持单/双引号,允许 src 之前/之后有别的属性)
private val HTML_IMG = Regex("""<img\s[^>]*?src=["']([^"']+)["'][^>]*?/?>""", RegexOption.IGNORE_CASE)
```

解析顺序: 先 MD,再 HTML。同一 url 在 MD 和 HTML 都出现 → 去重,只下 1 次。

### 4.2 Content-Type 推断扩展名

`OkHttp Response.header("Content-Type")` → 取 `image/(png|jpeg|webp|gif|...)` → `extension = "png"`。

fallback:Content-Type 缺失或不是 image/ → `extension = "img"`(通用兜底)。

### 4.3 下载走 `X-No-Auth-Retry`

`AuthInterceptor.kt:42-44` 已支持:

```kotlin
val noAuth = request.header("X-No-Auth-Retry")
if (noAuth != null) {
    chain.proceed(request.newBuilder().removeHeader("X-No-Auth-Retry").build())
    return
}
```

→ `FeishuImageDownloader` 每次构造 request 加 `X-No-Auth-Retry: 1` 头,跳过 Bearer 注入。

### 4.4 OkHttp client 复用

复用现有 `@Named("feishu") OkHttpClient`(走系统代理 + 超时 + 日志拦截器),**不**新建 client。X-No-Auth-Retry 头已经能区分鉴权语义。

### 4.5 大小限制

单图 > 20MB → 跳过,占位符 `[图片下载失败]`。**不**预读 body 测大小(浪费 IO),用 `Content-Length` header 预判;无 Content-Length → 直接下,流式写,超过 20MB 中断。

实现: `ResponseBody.byteStream()` 转 `InputStream`,`AttachmentStore.save` 时计数,> 20MB 抛 `ImageTooLargeException` → 转占位符。

### 4.6 占位符替换

替换策略(在 markdown 字符串上做):

```
![alt](https://feishu-cdn/xxx.png)
```

↓ 下载失败

```
[图片下载失败]
```

如果 alt 文本非空,保留: `[图片下载失败:alt]`(可选,本期**不**保留 alt,统一纯占位符)。

替换路径:`src` 是相对路径(本地路径)时**不**动;是 http(s) 且下载成功的才替换。

## 5. 服务层设计

### 5.1 `FeishuImportService`

```kotlin
@Singleton
class FeishuImportService @Inject constructor(
    private val feishuApi: FeishuApiClient,
    private val imageDownloader: FeishuImageDownloader,
    private val noteRepository: NoteRepository,
    private val feishuRefDao: FeishuRefDao,
    private val feishuSyncEventDao: FeishuSyncEventDao,
    private val attachmentDao: NoteAttachmentDao,
    private val attachmentStore: AttachmentStore,
    private val authStore: FeishuAuthStore,
) {
    suspend fun ensureAuthorized(): Result<Unit>  // 调 getAccessTokenSnapshot()
    suspend fun importSingleDoc(input: String): ImportResult
    suspend fun listFolderDocs(input: String): List<DocSummary>
    suspend fun importFolderDocs(folderToken: String, docTokens: List<String>, onProgress: (Int, Int) -> Unit): ImportSummary
}
```

### 5.2 `ImportResult` / `ImportSummary` / `DocSummary`

```kotlin
data class DocSummary(val token: String, val title: String, val url: String)

sealed class ImportResult {
    data class Success(val noteId: String) : ImportResult()
    data class Failure(val reason: String) : ImportResult()
}

data class ImportSummary(
    val totalRequested: Int,
    val successCount: Int,
    val failureCount: Int,
    val partialCount: Int,    // 落库成功但部分图片失败
    val failedDocTokens: List<String>,
)
```

### 5.3 批量串行 + sleep 200ms

```kotlin
for ((idx, token) in docTokens.withIndex()) {
    val result = importSingleDoc(token)  // 已包含图片下载
    if (idx < docTokens.lastIndex) delay(200)
    onProgress(idx + 1, docTokens.size)
}
```

### 5.4 `importSingleDoc` 完整流程

```
1. parse input → token
2. resolveFolderToken(token)  // wiki token 处理
3. feishuApi.fetchDocumentV2(docToken) → markdown
4. imageDownloader.downloadAndInline(markdown, newNoteId) → updatedMarkdown + List<NoteAttachmentEntity>
5. 构造 Note:
   - id = UUID.randomUUID().toString()
   - title = feishuApi.getDocument(docId).title  (走 v1 getDocument,因为 v2 docs_ai 没有暴露 title)
   - content = updatedMarkdown + "\n\n---\n来源飞书: " + docUrl
   - createdAt = updatedAt = now
   - isPinned = false
   - syncStatus = if (imageDownloader.anyFailed) PARTIAL_IMPORT_FAIL else SYNCED
6. noteRepository.upsert(note, tags = listOf("feishu"))
7. attachmentDao.insert(attachmentEntities)  // 图片附件
8. feishuRefDao.upsert(FeishuRefEntity(noteId, docId, docUrl, now, PULL, ..., SYNCED 或 CONFLICT(partial)))
9. feishuSyncEventDao.insert(IMAGE_FAIL_PARTIAL events)
10. return Success(noteId)
```

> **Title 来源决策**:优先 `getDocument(docId).title`(v1 返回 title 字段);若失败 fallback 到 markdown 第一行 `# xxx`;再 fallback 到 "未命名笔记"。**不**走 fetchDocumentV2(它只返 content,没有 title 顶层字段)。

## 6. UI 设计

### 6.1 笔记列表 TopAppBar 改造

`QuickNoteListScreen.kt:132-142` 当前:

```kotlin
TopAppBar(
    title = { Text(...) }
)
```

改成:

```kotlin
TopAppBar(
    title = { Text(stringResource(R.string.quicknote_list_title), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
    actions = {
        IconButton(onClick = { importMenuExpanded = true }) {
            Icon(Icons.Filled.CloudDownload, contentDescription = "从飞书导入")
        }
        AppActionDropdown(
            expanded = importMenuExpanded,
            onDismissRequest = { importMenuExpanded = false },
            items = listOf(
                AppActionItem("从文档导入", leadingIcon = Icons.Filled.NoteAdd) { 
                    importMenuExpanded = false
                    showImportDocDialog = true 
                },
                AppActionItem("从文件夹导入", leadingIcon = Icons.Filled.FolderOpen) {
                    importMenuExpanded = false
                    onNavigateToFolderImport()  // nav to feishu_folder_import
                },
            )
        )
    }
)
```

title 保持居中(`fillMaxWidth + textAlign = Center`),actions 放右上。

### 6.2 从文档导入 Dialog

`AlertDialog` + `OutlinedTextField` + 确认/取消按钮。验证:
- 空 → 确认按钮 disabled
- 含 `feishu.cn` host 但解析失败 → 弹错误
- 不含 host + token 前缀未知 → 仍允许提交,服务端 404 时再报错

### 6.3 FolderImportScreen

三段式状态:

```
state sealed class FolderImportState {
    object Input : FolderImportState()                      // 输入框
    data class Loading(val skeleton: Boolean = true) : FolderImportState()
    data class Loaded(val docs: List<DocSummary>) : FolderImportState()
    data class Error(val reason: String) : FolderImportState()
}
```

**Input 态**:
- 顶部 TopAppBar 带返回箭头 + 标题"从文件夹导入"
- 中间 `OutlinedTextField`(hint: "粘贴文件夹链接或 token")
- "解析"按钮 → 触发 ViewModel.listFolderDocs(input)

**Loading 态**:
- 全屏 `CircularProgressIndicator`(居中)
- 下半部分 `NoteListSkeleton`(复用现有 component,3~5 行占位)

**Loaded 态**:
- 顶部 chip:`"仅显示 docx 类型(共 N 篇)"`
- `LazyColumn` 列表,每行 checkbox + title + url
- 全选 / 反选按钮(可选)
- 底部 sticky `BottomAppBar` 或 `Surface`:`"导入(已选 N)"` 按钮

**Error 态**:
- 中间 `Icon` + 文案 + 重试按钮

### 6.4 批量导入 loading + 结果 dialog

- 用户在 Loaded 态点"导入" → navigate 回 QuickNoteListScreen + 弹全屏 `AlertDialog`(不可取消)显示 CircularProgressIndicator + "正在导入 1/N"
- 完成 → 替换为结果 dialog:"成功 X / 部分失败 Y / 失败 Z,已导入到笔记列表"
- 用户点"知道了" → 关闭 dialog,QuickNoteListScreen 通过 `SharedFlow` 通知刷新列表

### 6.5 未授权处理

`QuickNoteListScreen` 进入时通过 `FeishuAuthStore.authState.collectAsStateWithLifecycle()` 监听授权状态。
- 已授权 → dropdown 正常
- 未授权 → 用户点 dropdown item 后 → 弹错误 AlertDialog:
  - 标题 "未授权飞书账号"
  - 内容 "需要先在【我的】tab 完成飞书授权才能导入"
  - 按钮: "去授权"(跳"我的"tab)、"取消"

### 6.6 列表卡片 syncStatus chip 新 case

`NoteRow.kt` 现有 chip 渲染加一个分支:

```kotlin
FeishuRefStatus.PARTIAL_IMPORT_FAIL -> Chip(label = "部分失败", color = warning)
```

chip 颜色走 `MaterialTheme.colorScheme.tertiary`(跟 DIRTY 区分)。

## 7. 数据模型

### 7.1 `SyncStatus` 新增

`core/data/db/entity/SyncStatus.kt`:

```kotlin
enum class SyncStatus {
    LOCAL, SYNCED, DIRTY, CONFLICT, REMOTE_DELETED, PARTIAL_IMPORT_FAIL
}
```

> Room 持久化字段,加新枚举值要确认 DAO 兼容(用 `@TypeConverter` 转 String)。**确认现有 converter 是否支持新值**(大概率是 free string,不需要改 converter,但需要 sanity check)。

### 7.2 `FeishuRefStatus` 新增

```kotlin
enum class FeishuRefStatus {
    SYNCED, DIRTY, CONFLICT, REMOTE_DELETED,
    PARTIAL_IMPORT_FAIL  // 新
}
```

### 7.3 `feishu_sync_event` event type

新增字符串 `"IMAGE_FAIL_PARTIAL"`(混用既有 `status` String 字段,不引入新枚举)。每张失败的图一条 event,关联 noteId。

## 8. 边界 / 失败处理

| 场景 | 处理 |
|---|---|
| 输入为空 | 确认按钮 disabled |
| 输入含 `larksuite.com` | 错误 "不支持海外版" |
| 输入解析后 token 不存在 | API 抛 10003 → 转 "文档不存在或已删除" |
| `resolveFolderToken` 失败 | 降级:用原 token,飞书 API 后续可能 10006 |
| `listFolder` 返回空 | Loaded 态 `docs = emptyList()` → 显示空状态 chip |
| 列表返回但无 docx | 同上,文案 "未找到 docx 文档" |
| 单篇拉取失败 | ImportSummary.failureCount++ + failedDocTokens += token;不中断后续 |
| 图片 > 20MB | 占位符 + IMAGE_FAIL_PARTIAL event |
| 图片下载网络超时 | 占位符 + IMAGE_FAIL_PARTIAL event(超时 10s) |
| sleep 中用户取消(back) | coroutine cancel,已导入的笔记保留(ref 行已建),用户后续可在列表看到 |

## 9. 不做(显式排除)

- 不做增量同步 / 去重
- 不做 "重试失败图片" 按钮(留给详情页从飞书链接拉)
- 不做已导入历史列表
- 不支持 sheet/bitable/doc 等
- 不支持海外版
- 不在 sub-screen 里改 markdown(只读展示)

## 10. 测试策略

- Unit test:
  - `FeishuInputParser.parse()` 各 case
  - `FeishuImageDownloader` 正则提取(url 列表)
  - `FeishuImportService.importSingleDoc` mock 各依赖
- 不写 instrumentation test(网络相关,CI 跑不动)

## 11. 风险与 followup

- **CDN url 临时签名过期**:批量 100 篇耗时 ~2min,可能部分图签名过期 → 占位符。已在 §4.1 fallback 范围内,体验上可接受。
- **附件 storage 膨胀**:导入大量 docx(图片多)可能 100MB+ → 走现有 `AttachmentStore`,无新工作。
- **markdown 解析不全**:极复杂 docx 可能含 base64 内联图 / 飞书自定义 markdown 扩展 → 本期只处理标准 `![]()` 和 `<img>`;失败即占位符,用户无感。