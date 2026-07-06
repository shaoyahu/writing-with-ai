# feishu-import-from-folder

## Why

当前 `feishu-sync-image-support` change 解决了"本地笔记 → 飞书云文档"以及"详情页从飞书链接拉取"两个方向的反向同步。但用户**首次**把飞书侧已有的大量 docx 沉淀迁入本地笔记时,没有轻量入口 —— 必须一篇一篇贴 URL 走详情页拉取,效率低。

本期在笔记列表 TopAppBar 加一个"飞书导入"下拉菜单,提供两个独立能力:

1. **从文档导入**:粘贴一篇 docx 链接/token,直接拉取并落地为新笔记。
2. **从文件夹导入**:粘贴文件夹链接/token,列出该文件夹下**仅 docx 类型**的文档清单,用户多选后批量导入为新笔记。

每篇导入都建 `feishu_ref` 行(noteId ↔ docId),让用户在**详情页**后续走现有 push/pull 双向同步逻辑(更新同一飞书文档,不再新建)。列表页入口只负责"一次性创建新笔记"。

## What Changes

### 新增 `FeishuApiClient.listFolder()`

调 `GET /open-apis/drive/v1/files?folder_token=...`,分页拉取文件夹下的文件清单。返回 `type` 字段的原始字符串,业务层自己按 `type == "docx"` 过滤。

### 新增 `FeishuImportService`(core/feishu/sync/)

封装"飞书 → 本地笔记"链路:

- `importSingleDoc(input: String): ImportResult`
  解析链接/token → resolveFolderToken → fetchDocumentV2 → 下载图片(替换 markdown)→ upsert 笔记 + tag=`feishu` + 脚注 + 建 feishu_ref。
- `listFolderDocs(input: String): List<DocSummary>`
  解析 → listFolder → 过滤 type=docx → 返回元数据(token + title + url)。
- `importFolderDocs(folderToken: String, docTokens: List<String>): ImportSummary`
  串行(每篇 sleep 200ms),失败记 IMAGE_FAIL_PARTIAL → 最终返回成功/失败计数 + 失败原因。

### 新增 `FeishuImageDownloader`(core/feishu/sync/)

- 解析 markdown 中 `![...](url)` 和 `<img src="url">` 两种图片引用
- 给请求加 `X-No-Auth-Retry` header 跳过 `AuthInterceptor`(`AuthInterceptor.kt:42-44` 已支持)
- 下载到 `AttachmentStore.save(inputStream, noteId, uuid, ext)`,扩展名走 Content-Type 推断
- 单图 > 20MB 跳过 → 占位符 `[图片下载失败]`
- 单图下载失败 → 占位符 + 记录到 `feishu_sync_event` 表 `IMAGE_FAIL_PARTIAL`
- 全部串行,不并发

### 新增 `feature/feishuimport/`

- `FolderImportScreen.kt`:全屏 sub-screen
  - 输入框 → 解析 → 全屏 CircularProgressIndicator + 占位空列表骨架屏
  - 列表展示 docx 文档,多选 checkbox
  - 顶部 chip 提示"仅显示 docx 类型"
  - 底部"导入(已选 N)"按钮 → 跳批量导入 loading dialog → 结果 dialog(失败数量)
- `FolderImportViewModel.kt`:管 state / list / import 流程

### 笔记列表 TopAppBar 改造

- 加 IconButton(导入图标)→ 弹 `AppActionDropdown`
  - "从文档导入" → `AlertDialog` 输入框
  - "从文件夹导入" → navigate 到 `feishu_folder_import`
- 未授权时点了 → 弹错误 dialog + "去授权"按钮(轻提示,不强制跳转)

### Nav 路由

- 新增 `feishu_folder_import` route(push,不进 bottom tab)
- `app/AppNav.kt` 注册

### syncStatus 新增

- 新值 `PARTIAL_IMPORT_FAIL`(已落库 + markdown 写入成功 + 部分图片失败)
- `NoteRow` 渲染"部分失败"chip(走现有 syncStatus chip 渲染逻辑,加一个 case)
- `feishu_ref.status` 同步映射

## Out of Scope

- 不做增量同步 / 不查重(同一 docx 多次导入 → 多条本地笔记)
- 不下载 sheet / bitable / doc 等其他类型
- 不支持海外 larksuite.com
- 不重试失败图片(用户在详情页走"从飞书链接拉取")
- 不做"已导入记录"列表

## 影响面

- 新文件:
  - `core/feishu/sync/FeishuImportService.kt`
  - `core/feishu/sync/FeishuImageDownloader.kt`
  - `feature/feishuimport/FolderImportScreen.kt`
  - `feature/feishuimport/FolderImportViewModel.kt`
- 修改文件:
  - `core/feishu/api/FeishuApiClient.kt` + `FeishuApiClientImpl.kt`(加 listFolder)
  - `core/feishu/sync/FeishuApiModels.kt`(加 FolderFileEntry 等)
  - `feature/quicknote/list/QuickNoteListScreen.kt`(TopAppBar 加 dropdown)
  - `app/AppNav.kt`(注册路由)
  - `core/data/db/entity/SyncStatus.kt`(加 PARTIAL_IMPORT_FAIL)
  - `core/feishu/sync/FeishuRefStatus.kt`(同步映射)
  - `feature/quicknote/list/NoteRow.kt`(chip 新 case)
  - `docs/usage/api-feishu.md`(加 §listFolder 章节)
  - `core/feishu/di/FeishuModule.kt`(提供无 Auth OkHttp client,或复用 X-No-Auth-Retry)