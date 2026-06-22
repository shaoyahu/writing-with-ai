package com.yy.writingwithai.core.feishu.sync

import com.yy.writingwithai.core.feishu.api.DocCreateResult
import com.yy.writingwithai.core.feishu.api.DocMetadata
import com.yy.writingwithai.core.feishu.api.FeishuApiClient
import com.yy.writingwithai.core.feishu.converter.DocxToMarkdownConverter
import com.yy.writingwithai.core.feishu.converter.FeishuBlock
import com.yy.writingwithai.core.feishu.converter.MarkdownToDocxConverter
import com.yy.writingwithai.core.feishu.converter.Run

/** feishu-bidir-sync · 测试共享 fake 集合(internal 跨文件可见)。 */
internal class FakeFeishuApiClient : FeishuApiClient {
    val createdDocs = mutableListOf<String>()
    var blocksToReturn: String = "[{\"block_type\":\"text\",\"content\":\"x\"}]"
    val appendedDocIds = mutableListOf<String>()

    override suspend fun createDocument(title: String, folderToken: String?): DocCreateResult {
        val docId = "doc-new-${createdDocs.size + 1}"
        createdDocs += docId
        return DocCreateResult(docId = docId, docUrl = "https://f.cn/$docId")
    }

    override suspend fun getDocument(docId: String): DocMetadata =
        DocMetadata(docId = docId, revisionId = "rev-new", title = "t")

    override suspend fun getBlocks(docId: String): String = blocksToReturn

    override suspend fun appendChildren(docId: String, parentBlockId: String, childrenJson: String): String {
        appendedDocIds += docId
        return ""
    }

    override suspend fun batchDeleteChildren(docId: String, parentBlockId: String, childIds: String) {
        // no-op
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

internal class FakeMdConverter : MarkdownToDocxConverter {
    override suspend fun convert(markdown: String): List<FeishuBlock> =
        listOf(FeishuBlock.Paragraph(listOf(Run(markdown))))
}

internal class FakeDocxConverter : DocxToMarkdownConverter {
    var markdownToReturn: String = ""

    override suspend fun convert(blocks: List<FeishuBlock>): String = markdownToReturn
}
