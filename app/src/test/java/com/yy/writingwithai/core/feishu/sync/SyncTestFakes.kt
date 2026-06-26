package com.yy.writingwithai.core.feishu.sync

import com.yy.writingwithai.core.feishu.api.DocCreateResult
import com.yy.writingwithai.core.feishu.api.DocMetadata
import com.yy.writingwithai.core.feishu.api.FeishuApiClient
import com.yy.writingwithai.core.feishu.converter.MarkdownToXmlConverter

/** feishu-bidir-sync · 测试共享 fake 集合(internal 跨文件可见)。 */
internal class FakeFeishuApiClient : FeishuApiClient {
    // ---- v1 ----
    val v1CreateCalls = 0
    val v1BatchDeleteCalls = 0
    val createdDocs = mutableListOf<String>()
    var blocksToReturn: String = "[]"

    // ---- v2 ----
    var v2CreateCalls = 0
    var v2UpdateCalls = 0
    var v2FetchCalls = 0
    var markdownToReturn: String = "# fetched\n\nhello"
    val appendedDocIds = mutableListOf<String>()
    val v2Created = mutableListOf<String>()

    override suspend fun createDocument(title: String, folderToken: String?): DocCreateResult {
        val id = "doc-v1-${createdDocs.size + 1}"
        createdDocs += id
        return DocCreateResult(docId = id, docUrl = "https://f.cn/$id")
    }
    override suspend fun getDocument(docId: String): DocMetadata =
        DocMetadata(docId = docId, revisionId = "rev-1", title = "title-$docId")
    override suspend fun getBlocks(docId: String): String = blocksToReturn
    override suspend fun appendChildren(docId: String, parentBlockId: String, childrenJson: String): String {
        appendedDocIds += docId
        return ""
    }
    override suspend fun batchDeleteChildren(docId: String, parentBlockId: String, startIndex: Int, endIndex: Int) {}

    // v2
    override suspend fun createDocumentV2(
        xml: String,
        folderToken: String?
    ): com.yy.writingwithai.core.feishu.api.DocCreateResultV2 {
        v2CreateCalls++
        val id = "doc-v2-${v2Created.size + 1}"
        v2Created += id
        return com.yy.writingwithai.core.feishu.api.DocCreateResultV2(
            docId = id,
            docUrl = "https://f.cn/$id",
            revisionId = "rev-v2-${v2Created.size}"
        )
    }
    override suspend fun updateDocumentV2(docToken: String, xml: String): DocMetadata? {
        v2UpdateCalls++
        return DocMetadata(docId = docToken, revisionId = "rev-2", title = "")
    }
    override suspend fun fetchDocumentV2(docToken: String): String {
        v2FetchCalls++
        return markdownToReturn
    }
    override suspend fun appendBlockV2(docToken: String, xml: String) {
        appendedDocIds += docToken
    }
}

internal class FakeFeishuRefDao : FeishuRefDao {
    val store = mutableMapOf<String, FeishuRefEntity>()

    override suspend fun upsert(ref: FeishuRefEntity) {
        store[ref.noteId] = ref
    }

    override suspend fun getByNoteId(noteId: String): FeishuRefEntity? = store[noteId]

    override suspend fun getByNoteIds(noteIds: List<String>): List<FeishuRefEntity> = noteIds.mapNotNull { store[it] }

    override suspend fun getByDocId(docId: String): FeishuRefEntity? = store.values.firstOrNull { it.docId == docId }

    override suspend fun deleteByNoteId(noteId: String) {
        store.remove(noteId)
    }

    override suspend fun markRemoteDeleted(noteId: String): Int {
        val ref = store[noteId] ?: return 0
        store[noteId] = ref.copy(status = FeishuRefStatus.REMOTE_DELETED)
        return 1
    }

    override suspend fun listAll(): List<FeishuRefEntity> = store.values.toList()

    override suspend fun deleteAll() {
        store.clear()
    }
}

internal class FakeFeishuSyncEventDao : FeishuSyncEventDao {
    val store = mutableListOf<FeishuSyncEventEntity>()

    override suspend fun insert(event: FeishuSyncEventEntity) {
        store.add(event)
    }

    override suspend fun listLast(limit: Int): List<FeishuSyncEventEntity> =
        store.sortedByDescending { it.createdAt }.take(limit)

    override suspend fun count(): Int = store.size

    override suspend fun trimTo(cap: Int) {
        if (store.size <= cap) return
        val sortedAsc = store.sortedBy { it.createdAt }
        val toRemove = sortedAsc.take(store.size - cap).map { it.id }.toSet()
        store.removeAll { it.id in toRemove }
    }
}

/**
 * 测试 fake:简单回显 markdown + title 拼成 XML,记录最近一次调用。
 * 不复刻 MarkdownToXmlConverter 全部边界(那条单测自己覆盖)。
 */
internal class FakeXmlConverter : MarkdownToXmlConverter() {
    var lastMarkdown: String? = null
    var lastTitle: String? = null
    var xmlToReturn: String = "<document><title>T</title></document>"

    override fun convert(markdown: String, title: String): String {
        lastMarkdown = markdown
        lastTitle = title
        return xmlToReturn
    }
}
