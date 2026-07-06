## ADDED Requirements

### Requirement: Upload media to Feishu

系统 SHALL 提供 `FeishuApiClient.uploadMedia(docToken, file, mimeType)` 方法,通过 `POST /open-apis/drive/v1/medias/upload_all` multipart 表单上传图片素材,parent_type 固定为 `docx_image`,parent_node 传 doc token。返回的 `file_token` 字符串 SHALL 在调用方后续插入 image block 时使用。

#### Scenario: Successful upload
- **WHEN** 调用 `uploadMedia(docToken, jpegFile, "image/jpeg")` 且附件 ≤ 20 MB
- **THEN** 方法返回 `MediaUploadResult(fileToken = "<boxcn 开头 token>", bytes = <文件字节数>)`

#### Scenario: Auth token expired
- **WHEN** 调用 `uploadMedia` 时 user_access_token 已过期
- **THEN** AuthInterceptor 自动 refresh + 重试一次;仍失败 → 抛 `FeishuError.AuthExpired`,sync 层捕获后记录到 sync event

#### Scenario: File exceeds 20 MB
- **WHEN** 调用 `uploadMedia` 传入 > 20 MB 的附件
- **THEN** 方法 SHALL 在调用前检测文件大小,超过 20 MB 直接抛 `FeishuError.BadRequest`,不发起 HTTP 请求(v1 不支持分片上传,明确告知用户)

### Requirement: Append image blocks to doc

系统 SHALL 让 `FeishuApiClient.appendChildren(docId, parentBlockId, childrenJson)` 支持 image block JSON 输入,内部走 `POST /open-apis/docx/v1/documents/{docId}/blocks/{parentBlockId}/children`,每个 image block 用 `block_type: 27` + `image.token: <file_token>` 表达。飞书服务器 SHALL 一次创建 children 数组中的所有 block。

#### Scenario: Successful append
- **WHEN** 调用 `appendChildren(docId, parentBlockId, "[{\"block_type\":27,\"image\":{\"token\":\"boxcn...\"}}]")`
- **THEN** 飞书返回 `code: 0` 且 `data.children[i].block_id` 是新 image block 的 ID

#### Scenario: Children array exceeds 50
- **WHEN** 调用 `appendChildren` 的 children 数组 > 50 项
- **THEN** 方法 SHALL 在调用前分批(每批 ≤ 50),串行多次调用直到全部 append 完毕

### Requirement: Sync attachments after markdown

系统 SHALL 在 `FeishuDocService.createDoc` 与 `FeishuDocService.updateDoc` 中,在 markdown XML 文本同步成功后,自动追加 `syncAttachments` 步骤,把 `note_attachments` 表里该 note 的所有图片作为 image block 追加到飞书 doc 末尾。

#### Scenario: Note has no attachments
- **WHEN** `createDoc` / `updateDoc` 调 `noteAttachmentDao.getForNote(noteId)` 返回空列表
- **THEN** `syncAttachments` 跳过图片阶段,ref 状态直接置 SYNCED,无额外 sync event

#### Scenario: Note has 1 image, upload + append succeed
- **WHEN** `syncAttachments` 收到 1 张 jpeg 附件,uploadMedia 返回 file_token,appendChildren 返回成功
- **THEN** ref 状态置 SYNCED,无额外 sync event(markdown 阶段的 OK event 已记)

#### Scenario: Note has 2 images, second one upload fails
- **WHEN** `syncAttachments` 处理到第 2 张图时 uploadMedia 抛 `FeishuError.ServerError`
- **THEN** 中止本次 append,把 2 张图全部降级成 `[图片:<attachmentId>]` 文本占位符走 `appendBlockV2` 追加;ref 状态置 SYNCED;记录 `IMAGE_FAIL_PARTIAL` sync event,errorMessage 含 `upload_fail:<attachmentId>`

#### Scenario: All uploads succeed but appendChildren fails
- **WHEN** `syncAttachments` 全部 uploadMedia 成功,但 `appendChildren` 抛非 NotFound 异常
- **THEN** 走降级路径:把全部 attachmentId 转文本占位符走 `appendBlockV2` 追加;ref 状态置 SYNCED;记录 `IMAGE_FAIL_PARTIAL` sync event,errorMessage = `append_fail`

### Requirement: Single-upload serial

系统 SHALL 在 `syncAttachments` 中对每张图 `uploadMedia` **串行**调用(`await`),不并发。每次调用间无内置 delay(飞书服务端 5 QPS 限制由串行调用自然满足,典型笔记 ≤ 50 张图不触发)。

#### Scenario: 10 images uploaded serially
- **WHEN** `syncAttachments` 处理 10 张 jpeg 附件
- **THEN** 方法内部按 `attachments.sortedBy { createdAt }` 顺序逐张 uploadMedia,total wall time ≈ N × singleUploadLatency,无并发请求

### Requirement: Image-text non-interleaving

系统 SHALL NOT 在 markdown XML body 里嵌入图片元素(`<img>` / `<image>`);图片与文字 SHALL 不在 sync 文本中穿插。图片作为独立 children 块(每个 image 一个 block)追加到 doc 末尾,`appendChildren` 一次提交。

#### Scenario: Markdown has 3 paragraphs, attachments has 2 images
- **WHEN** `createDoc` 同步一篇 markdown 文本 + 2 张附件
- **THEN** 飞书 doc 的最终结构是:3 段 markdown 对应的 block + 末尾 2 个独立 image block(顺序按 attachment createdAt),不在段落中间穿插图片

### Requirement: Sync event IMAGE_FAIL_PARTIAL

系统 SHALL 在 sync 事件表新增字符串 `IMAGE_FAIL_PARTIAL`(作为 `FeishuSyncEventEntity.status` 的取值),用于标记 markdown 同步成功但图片同步部分失败的场景。`IMAGE_FAIL_PARTIAL` SHALL 不修改 `FeishuRefEntity.status` — ref 仍标 SYNCED,因为 doc 文本部分已经完整同步。

#### Scenario: Partial image failure recorded
- **WHEN** `syncAttachments` 走降级路径
- **THEN** `eventDao.insert(FeishuSyncEventEntity(status = "IMAGE_FAIL_PARTIAL", ...))` 被调用;同时 markdown 阶段的 OK event 也已记录(顺序 OK → IMAGE_FAIL_PARTIAL)

#### Scenario: SyncLogSection lists new status
- **WHEN** 用户打开 Settings → Sync Log,看到 IMAGE_FAIL_PARTIAL
- **THEN** 列表正常渲染该字符串(无需新增 UI case,沿用通用 status 渲染)