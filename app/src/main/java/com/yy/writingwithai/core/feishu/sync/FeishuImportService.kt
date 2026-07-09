package com.yy.writingwithai.core.feishu.sync

import android.util.Log
import androidx.room.withTransaction
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
        data class Failure(val reason: String, val isAuthExpired: Boolean = false) : ImportResult()
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
     *
     * 不仅检查 token 存在,还校验 expiresAt:飞书 access_token 2h 过期,
     * snapshot.expiresAt < now 时认为已过期,避免让过期 token 通过授权检查
     * 后续 API 调用才抛 AuthExpired(用户体验更糟)。
     */
    suspend fun ensureAuthorized(): Boolean {
        val snapshot = authStore.getAccessTokenSnapshot() ?: return false
        // Pair<String, Long> = (token, expiresAt)
        return snapshot.second > System.currentTimeMillis()
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
                    // 飞书某些租户 / 子账号返 "Docx" / "DOCX" / "docx "(带空格) 等不规范值,
                    // 严格 == 比较会全部 miss → listFolderDocs 返空 → UI 误报"未找到 docx"。
                    .filter { it.type.trim().equals("docx", ignoreCase = true) }
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
     *
     * @param folderToken 父文件夹 token(透传到每篇 ref 行的 folderToken 字段,供未来按 folder 维度去重 / 分组)
     * @param docTokens 要导入的文档 token 列表
     * @param onProgress (完成数, 总数) 回调
     *
     * 失败模式:
     * - 单篇失败 → ImportSummary.failureCount++ + failedDocTokens += token,不中断 batch
     * - 任意一篇遇到 [FeishuError.AuthExpired](token 过期) → 终止 batch,后续所有 token 进 failedDocTokens,
     *   避免 50 篇里 30 篇因同一过期 token 重复失败,用户分不清 token 过期 vs 单篇问题
     */
    suspend fun importFolderDocs(
        folderToken: String?,
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
            val result = importOneDocx(token, parentFolderToken = folderToken)
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

            // 批量中途 token 过期:终止后续所有 import,把剩余 token 全标失败。
            // fix-full-review:改用 ImportResult.Failure.isAuthExpired 类型标记，
            // 替代脆弱的 result.reason.contains("授权已过期") 字符串匹配(中文文案变更即失效)
            if (result is ImportResult.Failure && result.isAuthExpired) {
                val remaining = docTokens.drop(idx + 1)
                failed.addAll(remaining)
                failure += remaining.size
                Log.w(TAG, "importFolderDocs: AuthExpired at idx=$idx, abort batch (${remaining.size} docs skipped)")
                onProgress(docTokens.size, docTokens.size)
                break
            }
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

    /**
     * @param parentFolderToken 父文件夹 token(批量导入时透传;单文档导入传 null)
     */
    private suspend fun importOneDocx(docToken: String, parentFolderToken: String? = null): ImportResult =
        withContext(Dispatchers.IO) {
            try {
                // 1. resolveFolderToken — 处理 wiki / 其他类型 token。
                // AuthExpired 不吞:让上层 catch 收到正确错误,触发 UI 重授权提示。
                // 其他 FeishuError 也透传(原版 runCatching 吞所有 → 部分失败被吞成原 token 重试)。
                val resolved = try {
                    feishuApi.resolveFolderToken(docToken)
                } catch (e: FeishuError) {
                    // 不可降级的错误直接抛;可降级(IO / Network)走原 token 兜底
                    if (e is FeishuError.AuthExpired || e is FeishuError.Forbidden) throw e
                    if (e is FeishuError.NotFound) throw e
                    ResolvedFolderToken(docToken, null, false)
                }
                val realDocToken = resolved.token

                // 2. fetch markdown
                val rawMarkdown = feishuApi.fetchDocumentV2(realDocToken)

                // 3. 拿 title + revision — 优先 v1 getDocument;失败时从 markdown 第一行取 title
                val meta = runCatching { feishuApi.getDocument(realDocToken) }.getOrNull()
                val title = meta?.title?.takeIf { it.isNotBlank() }
                    ?: extractTitleFromMarkdown(rawMarkdown)
                    ?: "未命名笔记"
                // 飞书 API 返回的 markdown 可能第一行就是 # 标题，
                // 已提取到 Note.title，正文中应移除以避免重复显示。
                val markdown = stripLeadingTitle(rawMarkdown, title)
                // revision 用 v1 真实 revisionId(供双向同步 conflict detection);拿不到时 fallback "0"
                // 避免空字符串导致 FeishuSyncService 误判 CONFLICT(Finding #3)
                val remoteRevision = meta?.revisionId?.takeIf { it.isNotBlank() } ?: "0"

                // 4. 准备 noteId + 下载图片
                val noteId = UUID.randomUUID().toString()
                val dl = imageDownloader.downloadAndInline(markdown, noteId, httpClient)

                // 5. 构造 note + 落库 + 跨 DAO 写入(必须在同一事务内,避免半成品孤儿笔记)
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
                // fix H8:把 noteRepository.upsert 也移入事务块,确保 note + attachment + ref + event
                // 全部原子化。之前 upsert 在事务外,attachment/ref/event 失败会留下孤儿 note 行。
                val refStatus = if (anyImageFailed) {
                    FeishuRefStatus.PARTIAL_IMPORT_FAIL
                } else {
                    FeishuRefStatus.SYNCED
                }
                db.withTransaction {
                    noteRepository.upsert(note, tags = listOf("飞书"))
                    if (dl.attachments.isNotEmpty()) {
                        dl.attachments.forEach { attachmentDao.insert(it) }
                    }
                    refDao.upsert(
                        FeishuRefEntity(
                            noteId = noteId,
                            docId = realDocToken,
                            docUrl = docUrl,
                            lastSyncedAt = now,
                            syncDirection = SyncDirection.PULL,
                            localRevision = now,
                            remoteRevision = remoteRevision,
                            status = refStatus,
                            folderToken = parentFolderToken
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
                }
                ImportResult.Success(noteId = noteId, docId = realDocToken, anyImageFailed = anyImageFailed)
            } catch (e: FeishuError.NotFound) {
                ImportResult.Failure("文档不存在或已删除")
            } catch (e: FeishuError.AuthExpired) {
                ImportResult.Failure("飞书授权已过期,请重新授权", isAuthExpired = true)
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
        val match = H1_HEADING.find(firstLine) ?: return null
        return match.groupValues[1].take(100)
    }

    /**
     * 移除 markdown 开头的 `# 标题` 行（如果与提取的 title 匹配），
     * 避免标题在 Note.title 和 content 中重复显示。
     */
    private fun stripLeadingTitle(markdown: String, title: String): String {
        val lines = markdown.lines()
        val firstNonBlank = lines.indexOfFirst { it.isNotBlank() }
        if (firstNonBlank < 0) return markdown
        val firstLine = lines[firstNonBlank].trim()
        // 匹配 # 标题 或 ## 标题 等开头的 heading 行
        val headingMatch = HEADING_LINE.find(firstLine)
        if (headingMatch != null && headingMatch.groupValues[1] == title) {
            return lines.filterIndexedTo(mutableListOf()) { i, _ -> i != firstNonBlank }
                .joinToString("\n").trimStart('\n')
        }
        return markdown
    }

    companion object {
        private const val TAG = "FeishuImport"

        // feishu-import-from-folder:批量 sleep 避免触发飞书 5 QPS
        private const val BATCH_DELAY_MS = 200L

        // fix:提取内联 Regex 常量，避免每次调用重新编译
        private val H1_HEADING = Regex("""^#\s+(.+?)\s*$""")
        private val HEADING_LINE = Regex("""^#{1,6}\s+(.+?)\s*$""")
    }
}
