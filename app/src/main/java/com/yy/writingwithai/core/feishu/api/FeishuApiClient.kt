package com.yy.writingwithai.core.feishu.api

/**
 * feishu-oauth-flow · 飞书 Open API 抽象层。
 *
 * 由 [feishu-bidir-sync] change 通过此接口调飞书文档 CRUD + block 操作。
 *
 * spec: openspec/changes/feishu-oauth-flow/specs/feishu-api-client/spec.md
 * "FeishuApiClient abstraction"
 */
interface FeishuApiClient {
    /**
     * 创建飞书 docx 文档,返回 (docId, docUrl)。
     * @param folderToken 父文件夹 token;null 表示 root
     */
    suspend fun createDocument(title: String, folderToken: String? = null): DocCreateResult

    /**
     * 获取文档元信息(revisionId 用于 push 时防覆盖)。
     */
    suspend fun getDocument(docId: String): DocMetadata

    /**
     * 拉文档全部 block(Docx v1 response);由 caller 决定如何映射到 FeishuBlock。
     */
    suspend fun getBlocks(docId: String): String

    /**
     * 增量追加 children;返回新 block ids(逗号分隔字符串,飞书 API 行为)。
     */
    suspend fun appendChildren(docId: String, parentBlockId: String, childrenJson: String): String

    /**
     * 批量删除 children(block_id 列表逗号分隔)。
     */
    suspend fun batchDeleteChildren(docId: String, parentBlockId: String, childIds: String)
}

data class DocCreateResult(
    val docId: String,
    val docUrl: String
)

data class DocMetadata(
    val docId: String,
    val revisionId: String,
    val title: String
)
