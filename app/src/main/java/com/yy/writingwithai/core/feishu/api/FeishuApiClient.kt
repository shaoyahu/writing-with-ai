package com.yy.writingwithai.core.feishu.api

import java.io.File

/**
 * feishu-oauth-flow · 飞书 Open API 抽象层(v1 docx + v2 docs_ai)。
 *
 * v1 方法(createDocument / getDocument / getBlocks / appendChildren / batchDeleteChildren)
 * 保留兼容;新增 v2 方法基于 larksuite/cli docs_ai XML 端点。
 */
interface FeishuApiClient {
    // ---- v1 docx/v1(保留兼容) ----
    suspend fun createDocument(title: String, folderToken: String? = null): DocCreateResult
    suspend fun getDocument(docId: String): DocMetadata
    suspend fun getBlocks(docId: String): String
    suspend fun appendChildren(docId: String, parentBlockId: String, childrenJson: String): String

    /**
     * feishu-sync-image-support · 创建单个 block(带完整内容)。
     *
     * 赴 [创建块接口](https://open.feishu.cn/document/server-docs/docs/docs/docx-v1/document-block/create)
     * `POST /open-apis/docx/v1/documents/{document_id}/blocks/{block_id}/children`
     *
     * @param docId 文档 ID
     * @param parentBlockId 父 block ID (文档根 block)
     * @param blockType block 类型 (27 = image)
     * @param blockContentJson block 内容 JSON (如 image block 的 `{"token": "boxcn..."}`)
     * @return 创建的 block 的 ID
     */
    suspend fun createBlock(
        docId: String,
        parentBlockId: String,
        blockType: Int,
        blockContentJson: String = "{}"
    ): String

    /**
     * feishu-sync-image-support · PATCH 更新 block 内容。
     *
     * 赴 [更新块接口](https://open.feishu.cn/document/server-docs/docs/docs/docx-v1/document-block/patch)
     * `PATCH /open-apis/docx/v1/documents/{document_id}/blocks/{block_id}`
     *
     * @param docId 文档 ID
     * @param blockId 要更新的 block ID
     * @param patchJson PATCH 操作 JSON (如 replace_image)
     */
    suspend fun patchBlock(docId: String, blockId: String, patchJson: String)

    /**
     * feishu-sync-image-support · 上传素材到飞书云空间。
     *
     * 走 [开放平台官方接口](https://open.feishu.cn/document/server-docs/docs/drive-v1/media/upload_all)
     * `POST /open-apis/drive/v1/medias/upload_all`,parent_type 固定为 `docx_image`,
     * parent_node 传 image block ID (不是 doc_id!),返回 file_token 给后续 replaceImageBlock 用。
     *
     * fix-2026-07-05:添加 docId 参数，用于 extra.drive_route_token（飞书要求必填）
     *
     * 限制:单文件 ≤ 20 MB、5 QPS、10k/天(本实现不检查 QPS,串行 caller 自然满足)。
     * 超过 20 MB 直接抛 [FeishuError.BadRequest](v1 不支持分片上传)。
     */
    suspend fun uploadMedia(docId: String, parentNode: String, file: File, mimeType: String): MediaUploadResult

    /**
     * feishu-sync-image-support · 批量更新 block（用于 replace_image 绑定图片 token）。
     *
     * 走 [批量更新块接口](https://open.feishu.cn/document/server-docs/docs/docs/docx-v1/document-block/batch_update)
     * `POST /open-apis/docx/v1/documents/{document_id}/blocks/batch_update`
     *
     * @param docId 文档 ID
     * @param blockId 要更新的 image block ID
     * @param fileToken 上传素材返回的 file_token
     * @param width 图片宽度（可选，不传时飞书自动检测）
     * @param height 图片高度（可选，不传时飞书自动检测）
     */
    suspend fun replaceImageBlock(
        docId: String,
        blockId: String,
        fileToken: String,
        width: Int? = null,
        height: Int? = null
    )
    suspend fun batchDeleteChildren(docId: String, parentBlockId: String, startIndex: Int, endIndex: Int)

    // ---- v2 docs_ai/v1(XML format，参考 larksuite/cli) ----
    suspend fun createDocumentV2(xml: String, folderToken: String? = null): DocCreateResultV2
    suspend fun updateDocumentV2(docToken: String, xml: String): DocMetadata?
    suspend fun fetchDocumentV2(docToken: String): String
    suspend fun appendBlockV2(docToken: String, xml: String)

    // ---- drive/v1(文件元数据 + 文件操作) ----

    /**
     * 解析用户输入的 folder token。
     *
     * 用户可能输入 wiki 类型的 token(如 wikcnXXXXXX)而非云空间文件夹 token(fldcnXXXXXX)。
     * 此方法调用 `drive/v1/metas/batch_query` 查询 token 的真实类型和对应的 doc_token。
     * - 如果 token 是 folder 类型，直接返回原 token
     * - 如果 token 是 wiki 类型，返回 batch_query 响应中的真实 doc_token
     * - 其他类型也返回真实 doc_token（让飞书 API 自行校验）
     *
     * @param rawToken 用户输入的 token
     * @return 解析后可用于 createDocument / createDocumentV2 的 token；若查询失败则返回原 token（降级）
     */
    suspend fun resolveFolderToken(rawToken: String): ResolvedFolderToken

    /**
     * 删除飞书文件(移到回收站，30 天内可恢复)。
     *
     * feishu-folder-migration:当用户变更 folder token 后选择"删除旧文档 + 在新文件夹新建"时，
     * 先调此方法删除远端旧文档，再在新文件夹创建。
     *
     * API: `DELETE /open-apis/drive/v1/files/{file_token}`
     *
     * @param fileToken 文件 token(即 FeishuRefEntity.docId)
     */
    suspend fun deleteFile(fileToken: String)

    // ---- feishu-import-from-folder:列文件夹文件清单 ----

    /**
     * 列文件夹下的文件清单(分页)。
     *
     * feishu-import-from-folder:`GET /open-apis/drive/v1/files?folder_token=...`
     * 用于"从文件夹导入"批量入口。返回 `type` 字段原始字符串,业务层按 `type == "docx"` 过滤。
     *
     * @param folderToken 文件夹 token(必须是 fldcn* 类型,wiki token 走 [resolveFolderToken] 解析)
     * @param pageSize 分页大小(默认 50,飞书最大 200)
     * @param pageToken 分页游标(首次为 null)
     */
    suspend fun listFolder(folderToken: String, pageSize: Int = 50, pageToken: String? = null): ListFolderResponse
}

data class DocCreateResult(val docId: String, val docUrl: String)
data class DocMetadata(val docId: String, val revisionId: String, val title: String)
data class DocCreateResultV2(val docId: String, val docUrl: String, val revisionId: String)

/**
 * feishu-import-from-folder:listFolder 返回值。
 *
 * @param files 文件条目列表(`type` 字段原样返回,业务层自行过滤 docx)
 * @param nextPageToken 下一页游标(hasMore=true 时携带,下次调用传入)
 * @param hasMore 是否还有下一页
 */
data class ListFolderResponse(
    val files: List<FolderFileEntry>,
    val nextPageToken: String?,
    val hasMore: Boolean
)

/**
 * feishu-import-from-folder:listFolder 单个文件条目。
 *
 * 字段名严格对齐飞书响应(snake_case)。`type` 字段业务层需自己判定:
 * - `docx` 新版文档
 * - `doc` 旧版文档
 * - `sheet` / `bitable` / `mindnote` / `file` / `slides` / `wiki` / `folder` / `shortcut` 等
 */
data class FolderFileEntry(
    val name: String,
    val token: String,
    val type: String,
    val url: String?,
    val createdTime: String?,
    val modifiedTime: String?,
    val ownerId: String?,
    val parentToken: String?
)

/**
 * resolveFolderToken 的返回值。
 *
 * @param token 解析后可用于创建文档的 token（folder token 或 wiki 对应的真实 doc_token）
 * @param originalType 用户输入 token 的原始类型（如 "wiki"、"folder"、"docx" 等）；
 *   查询失败降级时为 null
 * @param resolved 是否成功通过 batch_query 解析；false 表示降级返回原 token
 */
data class ResolvedFolderToken(
    val token: String,
    val originalType: String?,
    val resolved: Boolean
)

/**
 * feishu-sync-image-support · uploadMedia 的返回值。
 *
 * @param fileToken 飞书素材 token(`boxcn...`),后续插入 image block 时塞进 `image.token`
 * @param bytes 上传的字节数(等于入参 file.length())
 */
data class MediaUploadResult(
    val fileToken: String,
    val bytes: Long
)
