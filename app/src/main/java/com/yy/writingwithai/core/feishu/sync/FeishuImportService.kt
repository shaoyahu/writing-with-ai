package com.yy.writingwithai.core.feishu.sync

import android.util.Log
import com.yy.writingwithai.core.data.db.AppDatabase
import com.yy.writingwithai.core.data.db.dao.NoteAttachmentDao
import com.yy.writingwithai.core.data.db.entity.SyncStatus
import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.core.feishu.api.FeishuApiClient
import com.yy.writingwithai.core.feishu.api.FeishuError
import com.yy.writingwithai.core.feishu.api.ResolvedFolderToken
import com.yy.writingwithai.core.feishu.auth.FeishuAuthStore
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * feishu-import-from-folder · 从飞书导入 docx 文档到本地笔记的服务。
 *
 * 公开 API:
 * - [ensureAuthorized] 检查用户是否完成飞书 OAuth 授权
 * - [importSingleDoc] 输入链接/token → 创建 1 条新笔记
 * - [listFolderDocs] 输入文件夹链接/token → 列出 docx 元数据
 * - [importFolderDocs] 给定 folderToken + docTokens → 批量串行创建笔记
 */
@Singleton
class FeishuImportService
@Inject
constructor(
    private val feishuApi: FeishuApiClient,
    private val imageDownloader: FeishuImageDownloader,
    private val noteRepository: NoteRepository,
    private val refDao: FeishuRefDao,
    private val eventDao: FeishuSyncEventDao,
    private val attachmentDao: NoteAttachmentDao,
    private val db: AppDatabase,
    private val authStore: FeishuAuthStore,
    @Named("feishu") private val httpClient: OkHttpClient
) {

    data class DocSummary(
        val token: String,
        val title: String,
        val url: String?
    )

    sealed class ImportResult {
        data class Success(
            val noteId: String,
            val docId: String,
            val anyImageFailed: Boolean
        ) : ImportResult()
        data class Failure(val reason: String) : ImportResult()
    }

    data class ImportSummary(
        val totalRequested: Int,
        val successCount: Int,
        val partialCount: Int,
        val failureCount: Int,
        val failedDocTokens: List<String>
    )

    /**
     * 用户是否已授权飞书 OAuth。
     */
    suspend fun ensureAuthorized(): Boolean {
        return authStore.getAccessTokenSnapshot() != null
    }

    /**
     * 输入链接/token → 创建 1 条笔记。
     */
    suspend fun importSingleDoc(input: String): ImportResult {
        val parsed = FeishuInputParser.parse(input)
        val token = when (parsed) {
            is FeishuInputParser.ParsedToken.RawToken -> parsed.token
            is FeishuInputParser.ParsedToken.Doc -> parsed.token
            is FeishuInputParser.ParsedToken.Folder -> parsed.token
            is FeishuInputParser.ParsedToken.UnsupportedHost ->
                return ImportResult.Failure("不支持海外版(${parsed.host})")
            is FeishuInputParser.ParsedToken.Malformed ->
                return ImportResult.Failure("无法解析输入(${parsed.reason})")
        }
        return importOneDocx(token)
    }

    /**
     * 输入文件夹链接/token → 列出 docx 文档。
     */
    suspend fun listFolderDocs(input: String): Result<List<DocSummary>> {
        val parsed = FeishuInputParser.parse(input)
        val rawToken = when (parsed) {
            is FeishuInputParser.ParsedToken.RawToken -> parsed.token
            is FeishuInputParser.ParsedToken.Folder -> parsed.token
            is FeishuInputParser.ParsedToken.Doc ->
                return Result.failure(IllegalArgumentException("输入的是文档,不是文件夹"))
            is FeishuInputParser.ParsedToken.UnsupportedHost ->
                return Result.failure(IllegalArgumentException("不支持海外版(${parsed.host})"))
            is FeishuInputParser.ParsedToken.Malformed ->
                return Result.failure(IllegalArgumentException("无法解析输入(${parsed.reason})"))
        }

        return runCatching {
            val resolved = feishuApi.resolveFolderToken(rawToken).token
            val all = mutableListOf<DocSummary>()
            var pageToken: String? = null
            do {
                val resp = feishuApi.listFolder(resolved, pageSize = 50, pageToken = pageToken)
                resp.files
                    .filter { it.type == "docx" }
                    .forEach { entry ->
                        all += DocSummary(
                            token = entry.token,
                            title = entry.name,
                            url = entry.url
                        )
                    }
                pageToken = if (resp.hasMore) resp.nextPageToken else null
            } while (pageToken != null)
            all
        }
    }

    /**
     * 批量串行导入。每篇之间 sleep 200ms(避免触发飞书 5 QPS)。
     */
    suspend fun importFolderDocs(
        folderToken: String,
        docTokens: List<String>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): ImportSummary {
        if (docTokens.isEmpty()) {
            return ImportSummary(0, 0, 0, 0, emptyList())
        }
        val failed = mutableListOf<String>()
        var fullSuccess = 0
        var partialSuccess = 0
        var failure = 0
        for ((idx, token) in docTokens.withIndex()) {
            val result = importOneDocx(token)
            when (result) {
                is ImportResult.Success -> {
                    if (result.anyImageFailed) partialSuccess++ else fullSuccess++
                }
                is ImportResult.Failure -> {
                    failure++
                    failed += token
                    Log.w(TAG, "importFolderDocs: failed doc=$token reason=${result.reason}")
                }
            }
            if (idx < docTokens.lastIndex) delay(BATCH_DELAY_MS)
            onProgress(idx + 1, docTokens.size)
        }
        return ImportSummary(
            totalRequested = docTokens.size,
            successCount = fullSuccess,
            partialCount = partialSuccess,
            failureCount = failure,
            failedDocTokens = failed
        )
    }

    // ---- 核心:导入单篇 docx ----

    private suspend fun importOneDocx(docToken: String): ImportResult = withContext(Dispatchers.IO) {
        try {
            // 1. resolveFolderToken — 处理 wiki / 其他类型 token(失败降级)
            val resolved = runCatching { feishuApi.resolveFolderToken(docToken) }
                .getOrElse { ResolvedFolderToken(docToken, null, false) }
            val realDocToken = resolved.token

            // 2. fetch markdown
            val markdown = feishuApi.fetchDocumentV2(realDocToken)

            // 3. 拿标题 — v1 getDocument 提供 title;失败时从 markdown 第一行取
            val title = runCatching { feishuApi.getDocument(realDocToken).title }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: extractTitleFromMarkdown(markdown)
                ?: "未命名笔记"

            // 4. 准备 noteId + 下载图片
            val noteId = UUID.randomUUID().toString()
            val dl = imageDownloader.downloadAndInline(markdown, noteId, httpClient)

            // 5. 构造 note + 落库
            val now = System.currentTimeMillis()
            val docUrl = "https://my.feishu.cn/docx/$realDocToken"
            val anyImageFailed = dl.failedUrls.isNotEmpty()
            val note = Note(
                id = noteId,
                title = title,
                content = dl.updatedMarkdown + "\n\n---\n来源飞书: $docUrl",
                createdAt = now,
                updatedAt = now,
                isPinned = false,
                lastAiOp = null,
                lastAiAt = null,
                syncRevision = null,
                syncStatus = if (anyImageFailed) SyncStatus.PARTIAL_IMPORT_FAIL else SyncStatus.SYNCED,
                lastSyncedAt = now
            )
            noteRepository.upsert(note, tags = listOf("feishu"))

            // 6. 写附件 + ref + event(在 note 落库后)
            if (dl.attachments.isNotEmpty()) {
                dl.attachments.forEach { attachmentDao.insert(it) }
            }
            val refStatus = if (anyImageFailed) {
                FeishuRefStatus.PARTIAL_IMPORT_FAIL
            } else {
                FeishuRefStatus.SYNCED
            }
            refDao.upsert(
                FeishuRefEntity(
                    noteId = noteId,
                    docId = realDocToken,
                    docUrl = docUrl,
                    lastSyncedAt = now,
                    syncDirection = SyncDirection.PULL,
                    localRevision = now,
                    remoteRevision = "",
                    status = refStatus,
                    folderToken = null
                )
            )

            // 7. 记 IMAGE_FAIL_PARTIAL event(每张失败图一条)
            if (anyImageFailed) {
                dl.failedUrls.forEach { url ->
                    eventDao.insert(
                        FeishuSyncEventEntity(
                            id = UUID.randomUUID().toString(),
                            noteId = noteId,
                            direction = SyncDirection.PULL,
                            status = "IMAGE_FAIL_PARTIAL",
                            errorMessage = "图片下载失败: $url",
                            createdAt = now
                        )
                    )
                }
            }
            ImportResult.Success(noteId = noteId, docId = realDocToken, anyImageFailed = anyImageFailed)
        } catch (e: FeishuError.NotFound) {
            ImportResult.Failure("文档不存在或已删除")
        } catch (e: FeishuError.AuthExpired) {
            ImportResult.Failure("飞书授权已过期,请重新授权")
        } catch (e: FeishuError.Forbidden) {
            ImportResult.Failure("无权限访问该文档")
        } catch (e: FeishuError.BadRequest) {
            ImportResult.Failure("请求被拒绝(${e.code}): ${e.msg}")
        } catch (e: FeishuError.NetworkError) {
            ImportResult.Failure("网络错误: ${e.detail}")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "importOneDocx: unexpected error for $docToken", e)
            ImportResult.Failure("未知错误: ${e.javaClass.simpleName}")
        }
    }

    private fun extractTitleFromMarkdown(markdown: String): String? {
        val firstLine = markdown.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
        val match = Regex("""^#\s+(.+?)\s*$""").find(firstLine) ?: return null
        return match.groupValues[1].take(100)
    }

    companion object {
        private const val TAG = "FeishuImport"

        // feishu-import-from-folder:批量 sleep 避免触发飞书 5 QPS
        private const val BATCH_DELAY_MS = 200L
    }
}
