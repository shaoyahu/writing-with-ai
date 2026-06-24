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

    // ---- v2 docs_ai/v1(XML format,参考 larksuite/cli) ----
    suspend fun createDocumentV2(xmlContent: String, folderToken: String? = null): DocCreateResultV2
    suspend fun updateDocumentV2(docToken: String, xmlContent: String): DocMetadata?
    suspend fun fetchDocumentV2(docToken: String): String
    suspend fun appendBlockV2(docToken: String, xmlContent: String)
}

data class DocCreateResult(val docId: String, val docUrl: String)
data class DocMetadata(val docId: String, val revisionId: String, val title: String)
data class DocCreateResultV2(val docId: String, val docUrl: String, val revisionId: String)
