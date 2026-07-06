package com.yy.writingwithai.core.feishu.sync

import com.yy.writingwithai.core.data.db.dao.NoteAttachmentDao
import com.yy.writingwithai.core.data.db.entity.NoteAttachmentEntity
import com.yy.writingwithai.core.feishu.api.DocCreateResult
import com.yy.writingwithai.core.feishu.api.DocMetadata
import com.yy.writingwithai.core.feishu.api.FeishuApiClient
import com.yy.writingwithai.core.feishu.api.FeishuError
import com.yy.writingwithai.core.feishu.api.ListFolderResponse
import com.yy.writingwithai.core.feishu.api.MediaUploadResult
import com.yy.writingwithai.core.feishu.api.ResolvedFolderToken
import com.yy.writingwithai.core.feishu.converter.MarkdownToXmlConverter
import java.io.File
import kotlinx.coroutines.flow.Flow

/** feishu-bidir-sync · 测试共享 fake 集合(internal 跨文件可见)。 */
internal class FakeFeishuApiClient : FeishuApiClient {
    // ---- v1 ----
    // fix-2026-06-27-review-r4 M13:val→var,override 内加++，让断言能读到计数。
    var v1CreateCalls = 0
    var v1BatchDeleteCalls = 0
    val createdDocs = mutableListOf<String>()

    // fix: getBlocks 返回正确的 JSON 结构(data.items 数组)
    var blocksToReturn: String = """{"data":{"items":[{"block_id":"root-block-1"}]}}"""

    // ---- v2 ----
    var v2CreateCalls = 0
    var v2UpdateCalls = 0
    var v2FetchCalls = 0
    var markdownToReturn: String = "# fetched\n\nhello"
    val appendedDocIds = mutableListOf<String>()
    val v2Created = mutableListOf<String>()

    // ---- feishu-sync-image-support(图片附件上传 + image block append) ----
    /** 上传 + 追加次数计数器与已上传文件名,断言用。 */
    val uploadMediaCalls = mutableListOf<Triple<String, String, String>>() // (docId, parentNode, mimeType)
    var uploadedFileTokenCounter = 0

    /** 模拟 uploadMedia 抛 ServerError(给 Case C/D 用)。默认 false。 */
    var uploadMediaShouldFail: Boolean = false

    /** 模拟 createBlock 抛 ServerError(给 Case D 用)。默认 false。 */
    var createBlockShouldFail: Boolean = false

    /** 模拟 appendChildren 抛 ServerError(给 Case D 用)。默认 false。 */
    var appendChildrenShouldFail: Boolean = false

    override suspend fun createDocument(title: String, folderToken: String?): DocCreateResult {
        v1CreateCalls++
        val id = "doc-v1-${createdDocs.size + 1}"
        createdDocs += id
        // fix(feishu-title-sync):v1 创建文档时标题已设置,无需再调 updateDocumentTitle
        return DocCreateResult(docId = id, docUrl = "https://f.cn/$id")
    }
    override suspend fun getDocument(docId: String): DocMetadata =
        DocMetadata(docId = docId, revisionId = "rev-1", title = "title-$docId")
    override suspend fun getBlocks(docId: String): String = blocksToReturn
    override suspend fun appendChildren(docId: String, parentBlockId: String, childrenJson: String): String {
        if (appendChildrenShouldFail) {
            throw FeishuError.ServerError(500)
        }
        appendedDocIds += docId
        return ""
    }
    override suspend fun batchDeleteChildren(docId: String, parentBlockId: String, startIndex: Int, endIndex: Int) {
        v1BatchDeleteCalls++
    }

    // feishu-sync-image-support: 新增 createBlock 实现(简化流程)
    var createBlockCalls = mutableListOf<Triple<String, String, Int>>() // (docId, parentBlockId, blockType)
    override suspend fun createBlock(
        docId: String,
        parentBlockId: String,
        blockType: Int,
        blockContentJson: String
    ): String {
        if (createBlockShouldFail) {
            throw FeishuError.ServerError(500)
        }
        createBlockCalls += Triple(docId, parentBlockId, blockType)
        return "block-${createBlockCalls.size}"
    }

    override suspend fun patchBlock(docId: String, blockId: String, patchJson: String) {
        // 不再需要,保留空实现
    }

    // fix-2026-07-05:添加 replaceImageBlock 实现
    override suspend fun replaceImageBlock(
        docId: String,
        blockId: String,
        fileToken: String,
        width: Int?,
        height: Int?
    ) {
        // 测试 fake 不实际调用 API，只记录调用即可
    }

    // feishu-sync-image-support: 测试 fake 不实际构造 multipart body(那部分单测应该在
    // FeishuApiClientImplTest),这里仅记录调用 + 可选抛错 + 返回递增 file_token。
    // fix-2026-07-05:修正签名，添加 parentNode 参数（应为 image block id）
    override suspend fun uploadMedia(
        docId: String,
        parentNode: String,
        file: File,
        mimeType: String
    ): MediaUploadResult {
        if (uploadMediaShouldFail) {
            throw FeishuError.ServerError(500)
        }
        uploadedFileTokenCounter++
        val size = if (file.exists()) file.length() else 0L
        uploadMediaCalls += Triple(docId, parentNode, mimeType)
        return MediaUploadResult(fileToken = "test-token-$uploadedFileTokenCounter", bytes = size)
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

    // feishu-import-from-folder:fake 不列文件夹
    override suspend fun listFolder(folderToken: String, pageSize: Int, pageToken: String?): ListFolderResponse =
        ListFolderResponse(files = emptyList(), nextPageToken = null, hasMore = false)
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

/**
 * feishu-sync-image-support · 测试用 attachment DAO fake。
 *
 * 真实 [NoteAttachmentDao] 有 5 个方法(observe / get / insert / delete / deleteForNote /
 * observeFirstImageForNotes),测试只关心 `getForNote` 返回哪些 entity,其他 no-op。
 */
internal class FakeNoteAttachmentDao : NoteAttachmentDao {
    /** key = noteId,value = 该 note 的附件列表。测试在 arrange 阶段预填。 */
    val store = mutableMapOf<String, List<NoteAttachmentEntity>>()

    override fun observeForNote(noteId: String): Flow<List<NoteAttachmentEntity>> =
        kotlinx.coroutines.flow.flowOf(store[noteId].orEmpty())

    override suspend fun getForNote(noteId: String): List<NoteAttachmentEntity> = store[noteId].orEmpty()

    override suspend fun insert(entity: NoteAttachmentEntity) {
        val list = store[entity.noteId].orEmpty().toMutableList()
        list += entity
        store[entity.noteId] = list
    }

    override suspend fun delete(entity: NoteAttachmentEntity) {
        val list = store[entity.noteId].orEmpty().toMutableList()
        list.removeAll { it.id == entity.id }
        store[entity.noteId] = list
    }

    override suspend fun deleteForNote(noteId: String) {
        store.remove(noteId)
    }

    override fun observeFirstImageForNotes(
        noteIds: List<String>
    ): Flow<List<com.yy.writingwithai.core.data.db.dao.FirstImageRow>> = kotlinx.coroutines.flow.flowOf(emptyList())
}
