# 飞书 Open API 接口文档

> 本项目飞书集成(`core/feishu/`)涉及的全部 Open API 接口参考，包括 OAuth 认证、文档 CRUD、文件元数据查询。
>
> **绝对不要瞎猜字段** — 任何字段疑问先查对应官方文档链接。

---

## 目录

- [§1 OAuth v2 认证](#1-oauth-v2-认证)
- [§2 v1 docx/v1 文档接口](#2-v1-docxv1-文档接口)
- [§3 v2 docs_ai/v1 文档接口](#3-v2-docs_aiv1-文档接口)
- [§4 drive/v1 文件元数据](#4-drivev1-文件元数据)
- [§5 Token 类型速查](#5-token-类型速查)
- [§6 错误码速查](#6-错误码速查)
- [§7 本项目实现映射](#7-本项目实现映射)
- [§8 同步数据模型](#8-同步数据模型)
- [§9 调试清单](#9-调试清单)

---

## 1. OAuth v2 认证

### 官方文档(必读)

| 阶段 | 文档 |
|---|---|
| **1. 获取 authorization code** | https://open.feishu.cn/document/authentication-management/access-token/obtain-oauth-code |
| **2. 换取 user_access_token** | https://open.feishu.cn/document/authentication-management/access-token/get-user-access-token |
| **3. 刷新 user_access_token** | https://open.feishu.cn/document/authentication-management/access-token/refresh-user-access-token |

### 1.1 Authorize 端点

- **URL**: `https://accounts.feishu.cn/open-apis/authen/v1/authorize`
- **Method**: GET
- **Query 参数**(全 URL-encode):

| 参数 | 必填 | 说明 |
|---|---|---|
| `client_id` | 是 | 飞书开放后台 → 应用凭证 → App ID |
| `response_type` | 是 | 固定 `code` |
| `redirect_uri` | 是 | **必须**跟 token 交换时传的完全一致，且必须在飞书后台"安全设置 → 重定向 URL"登记 |
| `scope` | 否 | 空格分隔，最多 50 个。本项目用 `docx:document drive:drive offline_access` |
| `state` | 否 | CSRF 防护，回调原样返回。本项目用 UUID,5 分钟 TTL |

> 注意: authorize URL 是 **v1**, token 端点是 **v2**, **不要混**。

### 1.2 换取 user_access_token

- **URL**: `https://open.feishu.cn/open-apis/authen/v2/oauth/token`
- **Method**: POST
- **Authorization header**: **不需要**

#### 请求体(JSON)

| 字段 | 必填 | 说明 |
|---|---|---|
| `grant_type` | 是 | 固定 `authorization_code` |
| `client_id` | 是 | 飞书 App ID |
| `client_secret` | 是 | 飞书 App Secret |
| `code` | 是 | 授权码(5 分钟 TTL, **一次性**) |
| `redirect_uri` | **是** | **必须**跟 authorize 时一致，否则 `20071 redirect_uri 不匹配` |
| `code_verifier` | 否 | PKCE 流程(本项目未用) |
| `scope` | 否 | 缩减权限范围 |

> ⚠️ **历史踩坑**: `redirect_uri` 在 v2 端点**必填**。漏了飞书返回 `20063 请求体缺少必要字段`(msg 字段为空，无法定位缺哪个 — 必须按 spec 全部字段都送)。

#### 响应体(JSON，字段顶层无 data 包装)

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | int | `0` = 成功，非 0 = 失败 |
| `access_token` | string | user_access_token(2h 有效) |
| `expires_in` | int | 有效期(秒) |
| `refresh_token` | string | 刷新凭证(需 `offline_access` scope) |
| `refresh_token_expires_in` | int | refresh_token 有效期 |
| `token_type` | string | 固定 `Bearer` |
| `scope` | string | 实际授予的 scope |
| `error` | string | 错误类型(失败时) |
| `error_description` | string | 错误详情(失败时) |

### 1.3 刷新 user_access_token

- **URL**: 同 §1.2, `https://open.feishu.cn/open-apis/authen/v2/oauth/token`
- **Method**: POST
- **Authorization header**: **不需要**

#### 请求体

| 字段 | 必填 | 说明 |
|---|---|---|
| `grant_type` | 是 | 固定 `refresh_token` |
| `client_id` | 是 | |
| `client_secret` | 是 | |
| `refresh_token` | 是 | 上次授权或刷新时拿到的 refresh_token |
| `scope` | 否 | 缩减权限范围 |

> **不需要 redirect_uri**(refresh 跟 code 无关)。

#### 响应体

同 §1.2。

---

## 2. v1 docx/v1 文档接口

> 官方文档: https://open.feishu.cn/document/server-docs/docs/docs/docx-v1/document
>
> v1 接口操作**空文档**(不含内容),仅做 CRUD 元数据 + block 级操作。
> 本项目同步流程已全部迁移到 v2 docs_ai, v1 仅保留兼容。

### 2.1 创建文档

- **URL**: `POST /open-apis/docx/v1/documents`
- **官方文档**: https://open.feishu.cn/document/server-docs/docs/docs/docx-v1/document/create
- **认证**: user_access_token (Bearer)
- **请求体**:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `title` | string | 是 | 文档标题 |
| `folder_token` | string | 否 | 目标文件夹 token，空则创建在用户默认空间 |

- **响应** `data.document`:

| 字段 | 类型 | 说明 |
|---|---|---|
| `document_id` | string | 文档唯一标识 |
| `revision_id` | int | 初始版本号 |
| `title` | string | 文档标题 |
| `url` | string | 文档访问 URL |

> ⚠️ v1 创建的文档是**空的**, 需要 `appendChildren` 填充内容。本项目已改用 v2 `createDocumentV2` 一步完成。

### 2.2 获取文档元数据

- **URL**: `GET /open-apis/docx/v1/documents/{document_id}`
- **官方文档**: https://open.feishu.cn/document/server-docs/docs/docs/docx-v1/document/get
- **认证**: user_access_token
- **响应** `data.document`:

| 字段 | 类型 | 说明 |
|---|---|---|
| `document_id` | string | 文档 ID |
| `revision_id` | int | 当前版本号 |
| `title` | string | 文档标题 |

### 2.3 获取文档所有 Block

- **URL**: `GET /open-apis/docx/v1/documents/{document_id}/blocks`
- **官方文档**: https://open.feishu.cn/document/server-docs/docs/docs/docx-v1/document-block/list
- **认证**: user_access_token
- **Query 参数**:

| 参数 | 类型 | 说明 |
|---|---|---|
| `page_size` | int | 分页大小(默认 500) |
| `page_token` | string | 分页游标 |

- **响应** `data`:

| 字段 | 类型 | 说明 |
|---|---|---|
| `items` | array | Block 列表 |
| `page_token` | string | 下一页游标 |
| `has_more` | bool | 是否有下一页 |

### 2.4 插入子 Block

- **URL**: `POST /open-apis/docx/v1/documents/{document_id}/blocks/{block_id}/children`
- **官方文档**: https://open.feishu.cn/document/server-docs/docs/docs/docx-v1/document-block-children/create
- **认证**: user_access_token
- **请求体**: JSON，包含 `children` 数组，每个元素定义一个 Block (text/heading/code 等)

### 2.5 批量删除子 Block

- **URL**: `DELETE /open-apis/docx/v1/documents/{document_id}/blocks/{block_id}/children/batch_delete`
- **官方文档**: https://open.feishu.cn/document/server-docs/docs/docs/docx-v1/document-block-children/batch_delete
- **认证**: user_access_token
- **请求体**:

| 字段 | 类型 | 说明 |
|---|---|---|
| `start_index` | int | 起始子 block 索引(含) |
| `end_index` | int | 结束子 block 索引(不含) |

> 注意: 按**索引范围**删除，不是按 block_id。索引从 0 开始。

### 2.6 插入图片 Block (image block)

- **URL**: `POST /open-apis/docx/v1/documents/{document_id}/blocks/{parent_block_id}/children`
- **官方文档**: https://open.feishu.cn/document/server-docs/docs/docs/docx-v1/document-block-children/create
- **FAQ 参考**: https://open.feishu.cn/document/docs/docs/faq#1908ddf0
- **认证**: user_access_token

#### 三步法插入图片（正确流程）

> **历史踩坑(2026-07-05)**: 原实现使用 POST 方法调用 batch_update，导致图片 token 无法绑定，
> 图片块一直转圈后失败。**正确做法是使用 PATCH 方法**。

飞书官方推荐的三步法流程：

1. **创建空的 image block**
   - 调用 `POST /open-apis/docx/v1/documents/{doc_id}/blocks/{parent_block_id}/children`
   - 请求体: `{"children":[{"block_type":27,"image":{}}]}`
   - 返回: 新创建的 image block 的 `block_id`

2. **上传图片素材**
   - 调用 `POST /open-apis/drive/v1/medias/upload_all` (multipart/form-data)
   - **关键参数**:
     - `parent_type`: `"docx_image"`
     - `parent_node`: **步骤1返回的 block_id**（不是 document_id！）
     - `extra`: `{"drive_route_token":"document_id"}`
   - 返回: `file_token` (`boxcn...`)

3. **绑定图片 token**
   - 调用 `PATCH /open-apis/docx/v1/documents/{doc_id}/blocks/batch_update`
   - **注意**: 必须用 **PATCH** 方法，不能用 POST
   - 请求体:
     ```json
     {
       "requests": [
         {
           "block_id": "步骤1返回的block_id",
           "replace_image": {
             "token": "步骤2返回的file_token"
           }
         }
       ]
     }
     ```

#### 本项目实现要点

- **代码位置**: `FeishuDocService.syncAttachments()` + `FeishuApiClientImpl`
- **创建空 block**: `createBlock(docId, parentBlockId, 27, "\"image\":{}")`
- **上传素材**: `uploadMedia(docId, blockId, file, mimeType)` - parent_node 传 block_id
- **绑定 token**: `replaceImageBlock(docId, blockId, fileToken)` - 使用 PATCH 方法
- **失败降级**: 任一步骤失败 → 全部转 `[图片:id]` 占位符 + IMAGE_FAIL_PARTIAL event

#### batch_update vs 单个 PATCH

飞书提供两种方式绑定图片 token：

| 方式 | URL | Method | 适用场景 |
|---|---|---|---|
| 单个 PATCH | `PATCH .../blocks/{block_id}` | PATCH | 单张图片 |
| batch_update | `PATCH .../blocks/batch_update` | PATCH | 多张图片批量绑定（最多200个） |

> ⚠️ **重要**: batch_update 必须用 **PATCH** 方法！飞书文档明确说明 HTTP Method 是 PATCH。
> 使用 POST会导致请求被拒绝或图片 token 无法绑定。

- **请求体 children 数组中 image block 的字段**:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `block_type` | int | 是 | 固定 `27` (= 27 表示 image block) |
| `image` | object | 是 | 图片对象 |
| `image.token` | string | 是 | 上传素材返回的 `file_token` (`boxcn...`) |
| `image.align` | int | 否 | 对齐: 1=左 2=居中 3=右 (本项目固定 `2` 居中) |
| `image.caption` | object | 否 | 标题(本项目不填) |

- **响应**: 跟 §2.4 一致。

> ⚠️ 单次 appendChildren 最多 50 个 children,超出需分批循环调。
> 本项目 `FeishuDocService.syncAttachments` 内部按附件顺序串行处理（符合飞书 5 QPS 限制）。

---

## 3. v2 docs_ai/v1 文档接口

> 官方文档: <https://open.feishu.cn/document/server-docs> (server-docs 文档站是真理来源)
>
> v2 接口支持 **XML 格式内容**，可一步创建含内容的文档。
> 本项目同步流程**主力使用 v2**。

### 3.1 创建文档(含内容)

- **URL**: `POST /open-apis/docs_ai/v1/documents`
- **认证**: user_access_token
- **请求体**:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `format` | string | 是 | 固定 `"xml"` |
| `content` | string | 是 | XML 格式文档内容 |
| `parent_token` | string | 否 | 父文件夹 token(注意: v2 用 `parent_token`，v1 用 `folder_token`) |

> ⚠️ v2 用 `parent_token` 不是 `folder_token`！且 token 必须是**云空间文件夹 token**(fldcn*)，
> 不能直接传 wiki token(wikcn*)。wiki token 需先通过 `drive/v1/metas/batch_query`(§4.1)解析。

- **响应** `data.document`:

| 字段 | 类型 | 说明 |
|---|---|---|
| `document_id` | string | 文档 ID(**可能不存在**，见下方 fallback) |
| `node_token` | string | 节点 token(document_id 缺失时的 fallback) |
| `obj_token` | string | 对象 token(第二个 fallback) |
| `url` / `url_outer` | string | 文档访问 URL |
| `revision_id` | string | 版本号 |

> ⚠️ **重要**: 当指定 `parent_token` 创建文档到文件夹时，响应可能**不包含 `document_id`**，
> 而是返回 `node_token` 或 `obj_token`。本项目按优先级解析: `document_id` → `node_token` → `obj_token`。
>
> 历史踩坑: 严格解析 `document_id` 会报 "missing field: document_id"。

### 3.2 更新文档(原子覆盖)

- **URL**: `PUT /open-apis/docs_ai/v1/documents/{doc_token}`
- **认证**: user_access_token
- **请求体**:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `format` | string | 是 | 固定 `"xml"` |
| `command` | string | 是 | `"overwrite"` = 原子替换全文 |
| `content` | string | 是 | XML 格式新内容 |

- **响应** `data.document` (可选):

| 字段 | 类型 | 说明 |
|---|---|---|
| `document_id` / `node_token` / `obj_token` | string | 同创建，可能缺失，有 fallback |
| `revision_id` | string | 新版本号 |
| `title` | string | 文档标题 |

> 设计要点: updateDoc 走 v2 overwrite，不做"删除 + 追加"两步操作，避免非原子性问题。

### 3.3 读取文档内容

- **URL**: `POST /open-apis/docs_ai/v1/documents/{doc_token}/fetch`
- **认证**: user_access_token
- **请求体**:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `format` | string | 是 | `"markdown"` 或 `"xml"` |

- **响应** `data.document`:

| 字段 | 类型 | 说明 |
|---|---|---|
| `content` | string | Markdown/XML 格式文档内容 |

### 3.4 追加 Block

- **URL**: `PUT /open-apis/docs_ai/v1/documents/{doc_token}`
- **认证**: user_access_token
- **请求体**:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `format` | string | 是 | 固定 `"xml"` |
| `command` | string | 是 | `"block_insert_after"` |
| `block_id` | string | 是 | 插入位置的参考 block ID，`"-1"` 表示文档末尾 |
| `content` | string | 是 | XML 格式追加内容 |

> 注意: 追加 Block 和更新文档用的是**同一个 URL**(PUT)，区别在于 `command` 字段。

---

## 4. drive/v1 文件元数据 + 文件操作

> 官方文档: https://open.feishu.cn/document/server-docs/docs/drive-v1/file/batch_query

### 4.1 批量查询文件元数据 (resolveFolderToken)

- **URL**: `POST /open-apis/drive/v1/metas/batch_query`
- **官方文档**: https://open.feishu.cn/document/server-docs/docs/drive-v1/file/batch_query
- **认证**: user_access_token
- **请求体**:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `request_docs` | array | 是 | 要查询的文档列表 |
| `request_docs[].doc_token` | string | 是 | 文件 token |
| `request_docs[].doc_type` | string | 是 | 文件类型: `"doc"` / `"docx"` / `"sheet"` / `"bitable"` / `"wiki"` / `"folder"` 等 |
| `with_url` | bool | 否 | 是否返回访问 URL |

- **响应** `data`:

| 字段 | 类型 | 说明 |
|---|---|---|
| `metas` | array | 元数据列表，顺序与请求对应 |
| `metas[].doc_token` | string | 文件的真实 token |
| `metas[].doc_type` | string | 文件的真实类型(可能与输入不同) |
| `metas[].url` | string | 访问 URL(`with_url=true` 时返回) |

#### 典型用法: wiki token → 真实 folder token

用户可能从飞书 wiki 页面复制 token(如 `wikcnXXXXXX`)，这不是云空间文件夹 token，
不能直接用于 `createDocumentV2` 的 `parent_token`。需要:

1. 根据前缀推断 `doc_type`(见 §5 Token 类型速查)
2. 调 `batch_query` 获取真实类型和 `doc_token`
3. 如果解析出 `doc_type == "folder"`，原 token 可直接用
4. 如果解析出 `doc_type == "wiki"` 等其他类型，用返回的 `doc_token`

> 降级策略: `batch_query` 失败时，返回原 token 让飞书 API 自行校验(可能报错，但不会静默丢数据)。

### 4.2 删除文件 (deleteFile)

- **URL**: `DELETE /open-apis/drive/v1/files/{file_token}`
- **官方文档**: https://open.feishu.cn/document/server-docs/docs/drive-v1/file/delete
- **认证**: user_access_token
- **路径参数**:

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `file_token` | string | 是 | 要删除的文件 token |

- **响应**: 标准飞书响应，`code == 0` 表示成功。

> ⚠️ 文件不会立即永久删除，而是**移到回收站**，30 天内用户可在飞书恢复。
> 本项目用于 folder token 变更后"删除旧文档 + 在新文件夹新建"场景。

### 4.3 上传素材 (upload_all) — 用于插入图片

- **URL**: `POST /open-apis/drive/v1/medias/upload_all`
- **官方文档**: https://open.feishu.cn/document/server-docs/docs/drive-v1/media/upload_all
- **认证**: user_access_token
- **请求**: `multipart/form-data`

| 字段(form part) | 必填 | 说明 |
|---|---|---|
| `file_name` | 是 | 上传文件名(本项目用 `noteAttachment.localPath` 的 basename) |
| `parent_type` | 是 | 固定 `"docx_image"` (插入 docx 图片) |
| `parent_node` | 是 | 关联的 docx 文档 token(本项目用 `ref.docId`) |
| `size` | 是 | 文件字节数 |
| `checksum` | 否 | SHA-1 校验和(本项目计算但不严格校验) |
| `file` | 是 | 文件二进制 |

- **限制**: 单文件 ≤ 20 MB, 5 QPS, 单用户 1 万 / 天。**v1 不支持分片上传**,超过 20 MB 会直接报错。
- **响应** `data`:

| 字段 | 类型 | 说明 |
|---|---|---|
| `file_token` | string | 素材 token (`boxcn...`),后续 image block `image.token` 字段用 |

> 本项目 `FeishuApiClient.uploadMedia` 在 `file.length() > 20 MB` 时直接抛 `FeishuError.BadRequest(0, "file > 20 MB, v1 不支持分片")` —— 上传不发出。

---

## 5. Token 类型速查

飞书各类资源 token 有固定前缀，可用于推断类型:

| 前缀 | 类型 | 说明 | 可否用作 parent_token |
|---|---|---|---|
| `fldcn` | folder | 云空间文件夹 | ✅ **可以直接用** |
| `wikcn` | wiki | 知识库 wiki 节点 | ❌ **需要 batch_query 解析** |
| `doccn` | doc | 旧版文档 | ❌ 需解析 |
| `docx` | docx | 新版文档 | ❌ 需解析 |
| `sheetcn` | sheet | 电子表格 | ❌ |
| `bascn` | bitable | 多维表格 | ❌ |
| `msncn` | messenger | 消息 | ❌ |

> 只有 `fldcn` 前缀的 folder token 可以直接作为 `createDocumentV2` 的 `parent_token`。
> 其他类型必须先通过 `drive/v1/metas/batch_query` 解析。

---

## 6. 错误码速查

### 6.1 OAuth 错误码 (authen/v2/oauth/token)

| code | 含义 |
|---|---|
| 0 | 成功 |
| 20001 | 必要参数缺失 |
| 20002 | client_secret 无效 |
| 20003 | 授权码无效或已使用 |
| 20004 | 授权码已过期(>5 分钟) |
| 20009 | 租户未安装应用 |
| 20010 | 用户无应用使用权限 |
| 20037 | refresh_token 过期(需重新授权) |
| 20063 | **请求体缺少必要字段**(msg 空，务必按 spec 全部字段都送) |
| 20064 | refresh_token 已使用或已撤销 |
| 20067 | scope 列表有重复 |
| 20068 | scope 包含未授权权限 |
| 20071 | redirect_uri 不匹配 |
| 20073 | refresh_token 已消费 |
| 20074 | refresh_token 未在应用启用 |

### 6.2 docx/v1 文档错误码

| code | 含义 |
|---|---|
| 0 | 成功 |
| 10001 | 参数错误 |
| 10002 | 权限不足(缺少 `docx:document` scope) |
| 10003 | 文档不存在或已删除 |
| 10004 | 文档已归档 |
| 10006 | folder_token 无效或无权限 |
| 10007 | 请求频率超限 |
| 230001 | Block 不存在 |
| 230002 | 父 Block 不存在 |
| 230003 | 子 Block 索引越界 |
| 230005 | Block 类型不支持此操作 |

### 6.3 docs_ai/v1 文档错误码

| code | 含义 |
|---|---|
| 0 | 成功 |
| 10001 | 参数错误 |
| 10002 | 权限不足 |
| 10003 | 文档不存在 |
| 10006 | parent_token 无效或类型不匹配 |
| 10007 | 请求频率超限 |
| 99991663 | **token 失效**(AuthInterceptor 自动重试刷新) |

### 6.4 drive/v1 文件错误码

| code | 含义 |
|---|---|
| 0 | 成功 |
| 10001 | 参数错误 |
| 10002 | 权限不足(缺少 `drive:drive` scope) |
| 10003 | 文件不存在 |
| 10007 | 请求频率超限(对 upload_all 即 5 QPS / 1 万/天) |
| 1061002 | 文件已被删除 |
| 1061003 | 文件已归档 |

> feishu-sync-image-support:upload_all 超过 20 MB 飞书返回 `1061001 文件大小超过限制`(OQ 已记入 design.md followup)。

### 6.5 HTTP 状态码映射 (FeishuError)

本项目统一将 HTTP 状态码映射到 `FeishuError` sealed class:

| HTTP 状态码 | FeishuError 子类 | 说明 |
|---|---|---|
| 200 + `code != 0` | `BadRequest(code, msg)` | 业务错误 |
| 200 + `code == 99991663` | `TokenInvalid` | Token 失效，触发自动刷新 |
| 400 | `BadRequest(400, body)` | 请求格式错误 |
| 401 | `AuthExpired` | 凭证失效，需重新授权 |
| 403 | `Forbidden(scope?)` | 权限不足 |
| 404 | `NotFound(resource)` | 文档/资源不存在 |
| 429 | `RateLimited(retryAfterSeconds)` | 限流，Retry-After 头 |
| 5xx | `ServerError(code)` | 飞书服务端错误 |

---

## 7. 本项目实现映射

### 接口 → 代码对照表

| 接口 | FeishuApiClient 方法 | FeishuDocService 方法 | 调用场景 |
|---|---|---|---|
| OAuth authorize | `OAuthLauncher.buildAuthorizeUrl()` | — | 用户点击"飞书授权" |
| OAuth token exchange | `UserTokenProvider.exchangeCode()` | — | OAuthCodeReceiver 回调 |
| OAuth token refresh | `UserTokenProvider.refreshToken()` | — | AuthInterceptor 401 重试 |
| v1 createDocument | `createDocument()` | — | *(保留兼容，未使用)* |
| v1 getDocument | `getDocument()` | `readDoc()` | 读取文档元数据(title/revision) |
| v1 getBlocks | `getBlocks()` | — | *(保留兼容，未使用)* |
| v1 appendChildren | `appendChildren()` | `FeishuDocService.syncAttachments()` (image block 路径) | **新增**: 图片 block 追加到飞书 doc 末尾 |
| v1 batchDeleteChildren | `batchDeleteChildren()` | — | *(保留兼容，未使用)* |
| drive upload_all (image) | `uploadMedia()` | `FeishuDocService.syncAttachments()` | **新增**: 图片附件上传到飞书素材库 |
| v2 createDocumentV2 | `createDocumentV2()` | `createDoc()` | **主流程**: 创建飞书文档 |
| v2 updateDocumentV2 | `updateDocumentV2()` | `updateDoc()` | **主流程**: 更新飞书文档 |
| v2 fetchDocumentV2 | `fetchDocumentV2()` | `readDoc()` | **主流程**: 读取文档内容 |
| v2 appendBlockV2 | `appendBlockV2()` | `appendBlock()` | 追加内容到文档末尾 |
| drive batch_query | `resolveFolderToken()` | `createDoc()` 内调用 | 创建前解析 folder token |
| drive deleteFile | `deleteFile()` | `deleteDoc()` | folder token 变更后删除旧文档 |

### 关键实现文件

| 文件 | 职责 |
|---|---|
| `core/feishu/api/FeishuApiClient.kt` | API 接口定义 + 数据类 |
| `core/feishu/api/FeishuApiClientImpl.kt` | OkHttp 实现，所有 endpoint 的请求/响应解析 |
| `core/feishu/api/FeishuError.kt` | 域错误 sealed class |
| `core/feishu/api/AuthInterceptor.kt` | user_access_token 自动注入 + 401 重试 |
| `core/feishu/auth/UserTokenProvider.kt` | OAuth token 交换/刷新 |
| `core/feishu/auth/FeishuAuthStore.kt` | 加密存储 token/credentials |
| `core/feishu/auth/OAuthLauncher.kt` | 构建 authorize URL |
| `core/feishu/auth/OAuthCodeReceiver.kt` | 处理 OAuth 回调 |
| `core/feishu/sync/FeishuDocService.kt` | 文档操作 facade(createDoc/readDoc/updateDoc/appendBlock/deleteDoc) |
| `core/feishu/sync/FeishuSyncService.kt` | 同步调度(pull/push/pushWithFolderMigration/bidir) |
| `core/feishu/converter/MarkdownToXmlConverter.kt` | Markdown → XML 转换 |

### 基地址

所有飞书 API 基地址: `https://open.feishu.cn/open-apis/`

代码中通过 `FeishuApiClientImpl.urlFor(segments)` 拼接，`segments` 传的是 `open-apis/` 之后的路径。

> 历史踩坑: 曾漏掉 `open-apis/` 前缀，请求发到 `open.feishu.cn/docx/v1/documents`(缺少 `/open-apis/`)，
> 飞书返回 404 或非 JSON body。

---

## 8. 同步数据模型

### 8.1 feishu_ref 表 (笔记 ↔ 飞书文档关联)

| 字段 | 类型 | 说明 |
|---|---|---|
| `noteId` | String (PK) | 本地笔记 ID |
| `docId` | String | 飞书文档 ID |
| `docUrl` | String | 飞书文档 URL |
| `lastSyncedAt` | Long | 上次同步时间戳 |
| `syncDirection` | SyncDirection | 同步方向: `PUSH` / `PULL` / `BIDIR` |
| `localRevision` | Long | 本地版本(note.updatedAt) |
| `remoteRevision` | String | 远端版本(revision_id) |
| `status` | FeishuRefStatus | 状态: `SYNCED` / `DIRTY` / `CONFLICT` / `REMOTE_DELETED` |
| `folderToken` | String? | 创建文档时使用的 folder token(用于检测 folder token 变更) |

### 8.2 feishu_sync_event 表 (同步事件日志)

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | String (PK) | UUID |
| `noteId` | String | 关联笔记 ID |
| `direction` | SyncDirection | 同步方向 |
| `status` | String | 结果: `"OK"` / `"FALLBACK_TO_UPDATE"` / 错误描述 |
| `errorMessage` | String? | 错误详情 |
| `createdAt` | Long | 事件时间戳 |

### 8.3 FeishuRefStatus 状态机

```
         createDoc / updateDoc
DISCONNECTED ──────────────────→ SYNCED
                                     │
                              本地修改 │
                                     ↓
                                  DIRTY ←────── pull 检测到本地修改
                                     │
                          push/pull 检测到双方修改
                                     ↓
                                 CONFLICT ──── 用户选择 keep local / keep remote ────→ SYNCED
                                     │
                           远端文档被删除
                                     ↓
                             REMOTE_DELETED
```

---

## 9. 调试清单

### OAuth 相关

| 症状 | 可能原因 | 排查 |
|---|---|---|
| `20063` 请求体缺少必要字段 | body 字段不全 | 检查 `redirect_uri` 是否传入 |
| `20071` redirect_uri 不匹配 | authorize 和 token 用的 redirect_uri 不一致 | 检查两处是否完全一致 |
| `20002` client_secret 无效 | secret 有空格/截断 | 重新复制 secret |
| `20003` 授权码无效或已使用 | code 被重复消费 | 重新走 OAuth |
| `20004` 授权码已过期 | code > 5 分钟 | 重新走 OAuth |
| `20037` refresh_token 过期 | 长时间未使用 | 重新走 OAuth |
| 响应 `code != 0` 但 `msg` 空 | — | 看 `error` / `error_description` 字段 |

### 文档操作相关

| 症状 | 可能原因 | 排查 |
|---|---|---|
| `missing field: document_id` | v2 创建文档到文件夹时响应不含 document_id | 本项目已做 fallback: document_id → node_token → obj_token |
| `10006` folder_token 无效 | 传了 wiki token 而非 folder token | 确认 `resolveFolderToken` 正常工作，检查前缀 |
| `10003` 文档不存在 | 文档被删除 / ID 错误 | 检查 feishu_ref 表 docId 是否正确 |
| `10002` 权限不足 | 缺少 `docx:document` / `drive:drive` scope | 重新授权，确认 scope 列表 |
| `99991663` token 失效 | user_access_token 过期 | AuthInterceptor 应自动刷新；若持续失败需重新授权 |

### Token 解析相关

| 症状 | 可能原因 | 排查 |
|---|---|---|
| wiki token 创建文档失败 | wiki token 不能直接做 parent_token | 确认 `resolveFolderToken` 被调用，看 logcat `FeishuApi` tag |
| batch_query 返回空 metas | token 无效或类型推断错误 | 检查前缀是否正确，`doc_type` 是否匹配 |
| 解析后 token 仍无效 | batch_query 返回的是 doc_token 而非 folder token | wiki 节点的真实 token 可能指向文档而非文件夹 |

### 网络相关

| 症状 | 可能原因 | 排查 |
|---|---|---|
| `飞书网络错误: host=UnknownHostException` | DNS / 网络不通 | 检查网络连接 |
| `飞书网络错误: ssl=SSLException` | SSL 证书 / 协议问题 | 不推荐自动重试 |
| `飞书限流, 60s 后重试` | 429 Too Many Requests | 等 Retry-After 后重试 |
| `飞书服务器错误: HTTP 50x` | 飞书服务端问题 | 稍后重试 |
