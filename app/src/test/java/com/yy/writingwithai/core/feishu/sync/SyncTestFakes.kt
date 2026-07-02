package com.yy.writingwithai.core.feishu.sync

import com.yy.writingwithai.core.feishu.api.DocCreateResult
import com.yy.writingwithai.core.feishu.api.DocMetadata
import com.yy.writingwithai.core.feishu.api.FeishuApiClient
import com.yy.writingwithai.core.feishu.api.FeishuError
import com.yy.writingwithai.core.feishu.api.ResolvedFolderToken
import com.yy.writingwithai.core.feishu.converter.MarkdownToXmlConverter

/** feishu-bidir-sync · 测试共享 fake 集合(internal 跨文件可见)。 */
internal class FakeFeishuApiClient : FeishuApiClient {
    // ---- v1 ----
    // fix-2026-06-27-review-r4 M13:val→var,override 内加++，让断言能读到计数。
    var v1CreateCalls = 0
    var v1BatchDeleteCalls = 0
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
        v1CreateCalls++
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
    override suspend fun batchDeleteChildren(docId: String, parentBlockId: String, startIndex: Int, endIndex: Int) {
        v1BatchDeleteCalls++
    }

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

    // drive/v1
    var deleteFileCalls = 0
    val deletedFileTokens = mutableListOf<String>()

    /** 测试用:设 true 模拟远端删除失败,触发 FeishuError.ServerError。 */
    var deleteFileShouldFail: Boolean = false
    override suspend fun resolveFolderToken(rawToken: String): ResolvedFolderToken =
        ResolvedFolderToken(token = rawToken, originalType = "folder", resolved = true)

    override suspend fun deleteFile(fileToken: String) {
        deleteFileCalls++
        if (deleteFileShouldFail) {
            throw FeishuError.ServerError(500)
        }
        deletedFileTokens += fileToken
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

    override fun observeLast(limit: Int): kotlinx.coroutines.flow.Flow<List<FeishuSyncEventEntity>> =
        kotlinx.coroutines.flow.flowOf(store.sortedByDescending { it.createdAt }.take(limit))

    override suspend fun count(): Int = store.size

    override suspend fun trimTo(cap: Int) {
        if (store.size <= cap) return
        val sortedAsc = store.sortedBy { it.createdAt }
        val toRemove = sortedAsc.take(store.size - cap).map { it.id }.toSet()
        store.removeAll { it.id in toRemove }
    }
}

/**
 * feishu-folder-migration + 历史测试通用 fake。
 *
 * 真实 `FeishuAuthStore` 有 18 个方法(包含 OAuth state / appSecret / pendingExchange 等),
 * 测试只关心 `getFolderTokenSnapshot()` 的返回值,其它方法 no-op。
 *
 * 用法:
 *   val folderTokenRef = mutableRef("fldcnA")
 *   val auth = FakeFeishuAuthStore(folderTokenSnapshot = { folderTokenRef.value })
 *   // 测试中修改 folderTokenRef.value 即可模拟用户切换 folder token
 */
internal class FakeFeishuAuthStore(
    var folderTokenSnapshot: () -> String? = { null }
) : com.yy.writingwithai.core.feishu.auth.FeishuAuthStore {
    private fun flowOfString(value: String?): kotlinx.coroutines.flow.Flow<String?> =
        kotlinx.coroutines.flow.flowOf(value)
    override val appId: kotlinx.coroutines.flow.Flow<String?> get() = flowOfString(folderTokenSnapshot())
    override val folderToken: kotlinx.coroutines.flow.Flow<String?> get() = flowOfString(folderTokenSnapshot())
    override val accessToken: kotlinx.coroutines.flow.Flow<String?> = kotlinx.coroutines.flow.flowOf(null)
    override val refreshToken: kotlinx.coroutines.flow.Flow<String?> = kotlinx.coroutines.flow.flowOf(null)
    override val expiresAt: kotlinx.coroutines.flow.Flow<Long?> = kotlinx.coroutines.flow.flowOf(null)
    override val authState: kotlinx.coroutines.flow.StateFlow<com.yy.writingwithai.core.feishu.auth.FeishuAuthState> =
        kotlinx.coroutines.flow.MutableStateFlow(com.yy.writingwithai.core.feishu.auth.FeishuAuthState.CONFIGURED)
    override val prefsInitError: Throwable? = null
    override suspend fun setOAuthCredentials(a: String, ac: String, rt: String, e: Long) {}
    override suspend fun setAuthState(s: com.yy.writingwithai.core.feishu.auth.FeishuAuthState) {}
    override suspend fun clearAll() {}
    override fun getAccessTokenSnapshot(): Pair<String, Long>? = null
    override fun getRefreshTokenSnapshot(): String? = null
    override fun getFolderTokenSnapshot(): String? = folderTokenSnapshot()
    override fun getAppIdAndRefreshToken(): Pair<String, String>? = null
    override suspend fun setAppId(appId: String) {}
    override suspend fun setFolderToken(folderToken: String?) {}
    override fun getAppIdSnapshot(): String? = null
    override suspend fun persistAppSecret(requestId: String, secret: String) {}
    override suspend fun clearAppSecret(requestId: String) {}
    override fun getAppSecretSnapshot(requestId: String): String? = null
    override fun getAppIdAndSecret(requestId: String): Pair<String, String>? = null
    override suspend fun persistOAuthState(state: String, ttlMs: Long) {}
    override fun consumeOAuthState(): String? = null
    override suspend fun persistPendingExchange(code: String, appId: String, secret: String, requestId: String) {}
    override fun consumePendingExchange(): com.yy.writingwithai.core.feishu.auth.PendingExchange? = null
    override fun hasPendingExchange(): Boolean = false
}

/**
 * 测试 fake:简单回显 markdown + title 拼成 XML，记录最近一次调用。
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
