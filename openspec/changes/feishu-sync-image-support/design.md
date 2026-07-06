## Context

**背景**:用户的笔记里已有图片附件(`note_attachments` 表 + 内部存储 `filesDir/attachments/{noteId}/{attachmentId}.jpg`)。`FeishuDocService.createDoc` / `updateDoc` 走的是 `xmlConverter.convert(note.content, title)`,只看 `note.content` markdown 字符串,所以本地附件完全没有路径传到飞书端 — 这就是用户报的"图片没跟过去"。

**当前 sync 状态**:
- `createDoc` 走 `v1 docx createDocument(title, folderToken)` + `v2 docs_ai updateDocumentV2(docId, xml)`(已用 docs_ai 的 XML overwrite,这是 [api-feishu.md §3.2](docs/usage/api-feishu.md) 已记录)
- `updateDoc` 同样走 v2 docs_ai overwrite
- `appendBlock` 走 v2 docs_ai 的 `block_insert_after`,但 `appendBlockV2` 当前只用于 AI 流式追加文字,本 change 不动它
- `appendChildren` 是 v1 docx 的 `POST .../blocks/{parent_block_id}/children`,**只是没被 sync 主路径用**(目前没有 caller)

**目标**:让 `note_attachments` 表里的图片数据通过新的 sync 编排传到飞书云文档。

**约束**:
- 用户原话:"图片与文字顺序无关" — 图片与文字不交错,作为独立 block 追加到 doc 末尾
- 失败降级:"doc 同步成功,图片降级成文本占位符"
- 5 QPS 单张串行(不实现限流池)
- 不改 markdown 正文,不引入 `![]()` 协议

## Goals / Non-Goals

**Goals:**
- 飞书云文档同步时,`note_attachments` 表里的所有图片作为 image block 追加到 doc 末尾
- 失败降级:某张图上传/插入失败 → 占位符 + IMAGE_FAIL_PARTIAL 事件,doc 整体仍标 SYNCED
- 走飞书开放平台公开接口,参考依据是开放平台 server-docs 文档站 + SDK 源码,不是 larksuite/cli
- 单测覆盖完整 syncAttachments 编排流程(markdown OK + 1 张图 OK / 1 张图失败 / 多张图部分失败)

**Non-Goals:**
- 不改 markdown 编辑/渲染层,不在 markdown 里引入图片语法
- 不改 media-attachment-infrastructure 的设计前提
- 不做图片与文字 block 顺序交错
- 不做大图分片上传(> 20 MB 不在本 v1 范围)
- 不做并发上传 / 限流池
- 不实现 pull 端的图片下载(sync 流程只负责 push)
- 不验证 docs_ai 文档 vs docx 文档的兼容性(在 Open Question 留出,真机验证时 first-touch)

## Decisions

### D1:图片 block 走 `docx/v1` 的 `appendChildren`,不走 `docs_ai/v1`

**Why**:
- 飞书开放平台官方文档站 [document-block-children/create](https://open.feishu.cn/document/server-docs/docs/docs/docx-v1/document-block-children/create) 明确给出 `block_type: 27` + `image.token` 字段规范
- SDK 源码 `larksuite/oapi-sdk-go/service/docx/v1/model.go` 有 `Image` struct 定义(`{width, height, token}`)
- `docs_ai/v1` 的 XML body 规范官方未给出完整 schema,XML 里能否写 `<img src="file_token">` 是推测 — 不赌
- 用户原则:只用开放平台接口,不参考 lark CLI

**Alternatives**:
- (a) `docs_ai/v1` XML body 里写 `<img>` — 走通可能性高(larksuite/cli 这么做),但缺官方文档,违反"参考开放平台官方文档"原则
- (b) `docx/v1` 单张图先建空 block 再 PATCH fill token — 三步法,飞书 FAQ 给的方案,但本场景 image block 已存在(由 appendChildren 创建),不需要 PATCH 替换
- 选 (c) `docx/v1` `appendChildren` 一次性带 token 创建 image block — 走两步:`upload_all` 拿 token → `appendChildren` 带 token 创建 image block,合二为一

### D2:`uploadMedia` 实现用 OkHttp `MultipartBody`,复用已有 OkHttpClient

**Why**:
- `FeishuApiClientImpl` 已经在 `@Named("feishu") OkHttpClient` 上做了一切(AuthInterceptor / 超时 / 流式截断),直接 `httpClient.newCall(request).execute()` 拿响应
- MultipartBody 是 OkHttp 内置,无需新依赖
- 复用 `executeRequest` 不行 —— 那个是为 JSON 响应设计的,对 multipart 响应(飞书 upload_all 返回 JSON)的 `data.file_token` 解析逻辑可以复用,但 body 构造走 OkHttp MultipartBody API

### D3:`syncAttachments` 编排:串行 upload → 一次 appendChildren

**Why**:
- 用户明确说"5 QPS 单张串行",所以每张图 `uploadMedia` 之间 await 即可,无需并发池
- 全部 token 拿到后**一次** `appendChildren` 提交所有 image block — 飞书 `appendChildren` 单次最多 50 项 children,典型笔记不会超
- 单次提交好处:失败点位明确(整批失败 / 整批成功),事件记录简单

**Alternatives**:
- (a) 一图一 appendChildren — N 张图 N 次 HTTP 调用,慢且容易触发 3 QPS 限流
- (b) 串行 upload + 单次 appendChildren — 选这个
- (c) 并发 upload — 5 QPS 限制下反而要写限流池,得不偿失

### D4:失败降级策略 — 整批 image block 失败时,文档里追加文字占位符

**Why**:
- 用户原话:"doc 同步成功,图片降级成文本占位符"
- 实现:整批 `appendChildren` 失败 → 把所有失败的 attachmentId 拼成 `for (id in failedIds) append "<p>[图片:$id]</p>"` 走 v2 docs_ai 的 `appendBlockV2`(已存在)追加文字占位符,记录 `IMAGE_FAIL_PARTIAL` 事件
- 单张 upload 失败的:中止整批(因为单张失败整批都失去意义),走降级路径
- 整批成功的:ref 状态不变(SYNCED),无额外事件(成功不需要记录)

### D5:`docs_ai/v1` 创建的 doc 能否直接走 `docx/v1/.../children`?

**Why**:
- 这是 Open Question,首次真机验证 first-touch(createDoc → appendChildren 一张空 image block 测通)
- 理论上:飞书文档只有一种 docx 文档,docs_ai 和 docx/v1 只是不同 API 入口,**操作的应该是同一类文档**
- 但飞书 v2 docs_ai 可能内部数据结构与 v2 docx 有差异(待验)
- 兜底:验证失败 → 改回 v1 docx 路径:`createDocument` (v1) + `appendChildren` (v1 docx),用 v1 overwrite 思路要重做(走 batch_delete + appendBlock,非原子)。这是大改,留作 fallback

### D6:`IMAGE_FAIL_PARTIAL` 事件写入位置

**Why**:
- 已有 `FeishuSyncEventEntity.status` 是 free-form String,无需加 enum / 加列
- 写在 `syncAttachments` 里失败 → catch → `eventDao.insert(... status="IMAGE_FAIL_PARTIAL" ...)`,与现有 `OK` / `REMOTE_DELETED` 等保持一致
- 不在 createDoc / updateDoc 主路径 catch(那里只 catch NotFound → REMOTE_DELETED),因为图片失败不应该让整篇 sync 失败 — 是 syncAttachments 内部的局部 catch

## Risks / Trade-offs

- **[R1] docs_ai/v1 与 docx/v1 兼容性未验** → 真机验证(在 §"Open Questions"留 TODO);失败 → D5 兜底(改回 v1 docx 路径)
- **[R2] 单张 5 MB 内的图没问题,但用户拍 50 张图时单张串行 ≈ 50 × 200ms = 10s 等待** → 接受(v1 不做并发);后续可优化为批量并发
- **[R3] `syncAttachments` 在 `createDoc` / `updateDoc` 末尾调用,意味着 sync 失败点位有 2 个 — markdown 失败 / 图片失败** → UI 层需要根据 `FeishuSyncEventEntity.status` 区分("OK" = 全成功,"IMAGE_FAIL_PARTIAL" = markdown OK 但图片部分失败),`FeishuSyncRepository` 拿到事件列表即可判断
- **[R4] `appendChildren` 单次最多 50 项 children,极端笔记(> 50 张图)需要分批** → 暂不实现,留作 v2;v1 加 log warning 提醒
- **[R5] 旧的 sync event schema 不变,但新增 `IMAGE_FAIL_PARTIAL` 状态,前端 UI 列表渲染需要识别这个新字符串** → 已有 `SyncLogSection` 渲染是泛化的状态字符串列表(不需要 case-by-case 处理),所以无需 UI 改动 — 验证时确认

## Migration Plan

无需数据迁移:
- `FeishuSyncEventEntity.status` 是 free-form String,新增 `IMAGE_FAIL_PARTIAL` 字符串不影响历史数据
- `FeishuRefEntity.status` enum 不变(SYNCED / DIRTY / CONFLICT / REMOTE_DELETED)
- `note_attachments` 表 schema 不变

回滚:
- 本 change 是 additive(只新增能力),回滚方案 = revert commit 或 hotfix disable `syncAttachments` 调用
- 历史同步数据不受影响

## Open Questions

- **OQ1**: `docs_ai/v1` 创建的 doc token 能否被 `docx/v1/.../children` 操作?首次真机验证(createDoc → 走 appendChildren 一张空 image block 测通)
- **OQ2**: `appendChildren` 的 parent_block_id 是什么?是 doc 的根 block_id 还是要先 `getBlocks` 拿?(飞书文档示例里是直接传文档根 ID `document_id` 当 parent,但需要真机确认)
- **OQ3**: 同步事件 `IMAGE_FAIL_PARTIAL` 是否要在 UI `SyncLogSection` 加一个颜色 / 图标区分?暂用普通样式,review 后再优化