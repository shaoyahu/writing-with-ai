package com.yy.writingwithai.core.feishu.api

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
}

data class DocCreateResult(val docId: String, val docUrl: String)
data class DocMetadata(val docId: String, val revisionId: String, val title: String)
data class DocCreateResultV2(val docId: String, val docUrl: String, val revisionId: String)

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
