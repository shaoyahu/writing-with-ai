## Why

笔记详情页支持插入图片附件后,用户在飞书云文档里看不到这些图片 —— 当前的 `FeishuDocService.createDoc` / `updateDoc` 走 `xmlConverter.convert(note.content, title)` 把 markdown 文本转成 XML 写到飞书,**整个同步流程只读取 `note.content`,完全不读 `note_attachments` 表**,所以图片数据从本地到飞书是断的。用户已经把图片作为附件存在本地,但飞书云文档里一篇只有纯文字,体验割裂。

## What Changes

- **新增** `FeishuApiClient.uploadMedia(docToken, file, mimeType): MediaUploadResult`:走飞书开放平台 `POST /open-apis/drive/v1/medias/upload_all`(multipart),返回 `file_token`。这是同步图片前必须先拿的 token。
- **复用 + 增强** `FeishuApiClient.appendChildren(...)`:这个方法 endpoint 已经是 `POST /open-apis/docx/v1/documents/{id}/blocks/{parent_block_id}/children`,刚好就是插入 `block_type=27` image block 的官方接口。本次让它的 caller 能传 image block JSON(`{block_type: 27, image: {token, align, caption}}`),实际签名保持兼容。
- **新增** `FeishuDocService.syncAttachments(noteId, ref, attachments): ImageSyncOutcome`:串行遍历附件 → 对每张图 `uploadMedia` 拿 file_token → 全部 token 拿到后**一次** `appendChildren` 提交所有 image block。一张失败降级成 `[图片: attachmentId]` 占位符;doc 整体同步状态标 `IMAGE_FAIL_PARTIAL` 事件,ref 状态不变(SYNCED,因为 doc 文本部分成功了)。
- **修改** `FeishuDocService.createDoc` / `updateDoc`:在 markdown 文本同步成功后,串行调 `syncAttachments` 追加图片 block 到 doc 末尾。文本失败优先抛错,文本成功后才进入图片阶段。
- **新增** 同步事件 status 字符串 `IMAGE_FAIL_PARTIAL`(已有 `FeishuSyncEventEntity.status` 是 free-form string,无需 schema 改动)。
- **修正文档**:`docs/usage/api-feishu.md:195` 那句 "官方文档: 参考 larksuite/cli 实现,飞书官方文档较分散" — 本 change 顺手把这种"参考 lark CLI"的措辞改掉,改为指明官方文档站链接(因为我们已经过一遍飞书开放平台 server-docs 文档站,确认 image block + upload_all + docs_ai 三个 endpoint 都是开放平台公开接口)。

**不做的事**(明确边界):
- 不改 Editor / Detail UI 层(`AttachmentRow` / `addAttachment` / `commitAttachment`)
- 不改 `media-attachment-infrastructure` 的设计前提(图片仍独立于 markdown,只在 sync 时拼到飞书 doc)
- 不改 markdown 正文(不引入 `![]()` 语法,不引入 `attachment://` 协议)
- 不改 `MarkdownToXmlConverter` / `MarkdownToDocxConverterImpl`(图片与文字**不**在 XML 里穿插)
- 不做 markdown 与图片 block 的顺序交错(用户明确说"图片与文字顺序无关")
- 不做大图分片上传(本 change 只支持 ≤ 20 MB;`upload_prepare` / `upload_part` / `upload_finish` 三步分片超出 v1 范围)
- 不做并发限流池(单张串行,符合 5 QPS 限制)

## Capabilities

### New Capabilities

- `feishu-sync-image`:飞书云文档图片同步。覆盖 uploadMedia + appendChildren image block + syncAttachments 编排 + 失败降级策略 + IMAGE_FAIL_PARTIAL 事件。

### Modified Capabilities

无 — 现有 specs 的 REQUIREMENT 不变(本次只增加新能力,不修改已有 spec 的语义)。

## Impact

- **新增代码**:
  - `app/src/main/java/com/yy/writingwithai/core/feishu/api/FeishuMediaUpload.kt`(multipart upload 实现)
  - `app/src/main/java/com/yy/writingwithai/core/feishu/sync/ImageSyncOutcome.kt`(结果类型 + IMAGE_FAIL_PARTIAL 状态)
  - 单测覆盖:`core/feishu/sync/ImageSyncTest.kt` 类似已有 `SyncTestFakes.kt` 模式
- **修改代码**:
  - `FeishuApiClient.kt`:加 `uploadMedia` 抽象方法 + 返回类型
  - `FeishuApiClientImpl.kt`:实现 uploadMedia(OkHttp MultipartBody);保持 `appendChildren` 签名不变
  - `FeishuDocService.kt`:`createDoc` / `updateDoc` 在 markdown 同步成功后调 `syncAttachments`;`syncAttachments` 是新方法
  - `FeishuBindsModule.kt` / DI:`FeishuMediaUpload` 加 `@Inject` 入口
- **修改文档**:
  - `docs/usage/api-feishu.md`:增补 §7 "上传素材"+ §8 "图片 block 插入"(基于开放平台官方文档,引用 server-docs URL,不引用 lark CLI);顺手修 §3 入口那句 "参考 larksuite/cli 实现" 的过时注释
  - `docs/progress.md`:本次同步条目
- **依赖**:
  - OkHttp 已是项目依赖(已有 `MultipartBody` 用法),无需新增依赖
- **API 限制**:
  - 上传 5 QPS / 10k/天 / 单文件 ≤ 20 MB(本 change 全部走单次 `upload_all`,不实现分片)
  - appendChildren 3 QPS / 单文档并发 3/秒(本 change 串行,自然满足)
- **风险**:
  - `docs_ai/v1` 创建出来的 doc 是否能被 `docx/v1` 的 `appendChildren` 操作 —— 这是 docs_ai 文档的 doc_token 能否直接当成 docx doc_id 用的兼容性问题。**首次实现 + 真机验证**时必须先验证(createDoc → appendChildren 一张空 image block 测通);如果验证失败,降级方案是改回 v1 `createDocument`(已有的 v1 docx 端点)+ v1 docx 路径