package com.yy.writingwithai.core.feishu.sync

import android.util.Log
import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.core.feishu.api.FeishuApiClient
import com.yy.writingwithai.core.feishu.api.FeishuError
import com.yy.writingwithai.core.feishu.converter.MarkdownToXmlConverter
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * feishu-doc-service-refactor · 飞书文档原子操作 facade(v2 docs_ai/v1)。
 *
 * 全部走 v2 `docs_ai/v1` XML 接口(参考 larksuite/cli):
 * - create: createDocumentV2(xml)        单次创建，自带初始内容
 * - read:   fetchDocumentV2 + getDocument 拿 markdown + title + revision
 * - update: updateDocumentV2(command=overwrite) 单次 PUT，原子替换
 * - append: appendBlockV2(command=block_insert_after) 单块追加
 *
 * 设计要点:
 * - updateDoc 走 v2 overwrite 不再做"删 + 追加"两步，避免 C3(404)与 H6(非原子)。
 * - readDoc 直接返回 Markdown 字符串，FeishuSyncService.pull 不再需要 docxConverter
 *   中转，解决 C2(pull 写空 markdown)。
 * - recordEvent 失败不影响主流程(L4)。
 */
@Singleton
class FeishuDocService
@Inject
constructor(
    private val feishuApi: FeishuApiClient,
    private val xmlConverter: MarkdownToXmlConverter,
    private val refDao: FeishuRefDao,
    private val eventDao: FeishuSyncEventDao
) {

    suspend fun createDoc(note: Note, folderToken: String? = null): FeishuRefEntity = withContext(Dispatchers.IO) {
        val title = note.title.ifBlank { "未命名笔记" }
        val xml = xmlConverter.convert(note.content, title)
        // fix(feishu-folder-token):用户可能输入 wiki 类型的 token，需要先通过
        // drive/v1/metas/batch_query 解析为真实的 docx folder token。
        val resolvedFolderToken = if (folderToken != null) {
            val resolved = feishuApi.resolveFolderToken(folderToken)
            if (resolved.resolved && resolved.originalType != null && resolved.originalType != "folder") {
                Log.i(TAG, "resolveFolderToken: $folderToken (type=${resolved.originalType}) → ${resolved.token}")
            }
            resolved.token
        } else {
            null
        }
        val created = feishuApi.createDocumentV2(xml, resolvedFolderToken)
        val now = System.currentTimeMillis()
        val ref = FeishuRefEntity(
            noteId = note.id,
            docId = created.docId,
            docUrl = created.docUrl,
            lastSyncedAt = now,
            syncDirection = SyncDirection.PUSH,
            localRevision = note.updatedAt,
            remoteRevision = created.revisionId,
            status = FeishuRefStatus.SYNCED,
            // feishu-folder-migration:记录创建文档时使用的 folder token，
            // 用于后续 push 时检测 folder token 是否变更。
            folderToken = resolvedFolderToken
        )
        refDao.upsert(ref)
        recordEventSafe(note.id, SyncDirection.PUSH, "OK", null)
        ref
    }

    /**
     * 读取飞书文档 → Markdown 字符串 + 标题 + revision。
     * 抛 BadRequest 当 url 解析不出 docId。
     */
    suspend fun readDoc(url: String): FeishuDocContent = withContext(Dispatchers.IO) {
        val docId = extractDocIdFromUrl(url)
            ?: throw FeishuError.BadRequest(0, "invalid feishu doc url: $url")
        val meta = feishuApi.getDocument(docId)
        val markdown = feishuApi.fetchDocumentV2(docId)
        FeishuDocContent(
            docId = docId,
            title = meta.title,
            revisionId = meta.revisionId,
            markdown = markdown
        )
    }

    /**
     * 原子替换整篇文档(v2 overwrite)。
     * 不再调 batch_delete + append，避免 delete 成功 / append 失败导致空文档。
     */
    suspend fun updateDoc(note: Note, ref: FeishuRefEntity): FeishuRefEntity = withContext(Dispatchers.IO) {
        val xml = xmlConverter.convert(note.content, note.title.ifBlank { "未命名笔记" })
        val meta = feishuApi.updateDocumentV2(ref.docId, xml)
        val now = System.currentTimeMillis()
        val updated = ref.copy(
            localRevision = note.updatedAt,
            remoteRevision = meta?.revisionId ?: ref.remoteRevision,
            lastSyncedAt = now,
            status = FeishuRefStatus.SYNCED
        )
        refDao.upsert(updated)
        recordEventSafe(note.id, SyncDirection.PUSH, "OK", null)
        updated
    }

    suspend fun appendBlock(
        note: Note,
        ref: FeishuRefEntity,
        parentBlockId: String?,
        content: String
    ): FeishuRefEntity = withContext(Dispatchers.IO) {
        val xml = buildAppendXml(content)
        try {
            feishuApi.appendBlockV2(ref.docId, xml)
            val now = System.currentTimeMillis()
            val updated = ref.copy(localRevision = note.updatedAt, lastSyncedAt = now)
            refDao.upsert(updated)
            recordEventSafe(note.id, SyncDirection.PUSH, "OK", null)
            updated
        } catch (e: FeishuError.NotFound) {
            // parent_block_id 失效 → 退回整篇覆盖(v2 overwrite 原子)
            recordEventSafe(note.id, SyncDirection.PUSH, "FALLBACK_TO_UPDATE", "parent_missing")
            updateDoc(note, ref)
        }
    }

    /**
     * feishu-folder-migration · 删除远端飞书文档(移到回收站)。
     *
     * 当用户变更 folder token 后选择"删除旧文档 + 在新文件夹新建"时调用。
     * 返回 `true` 表示远端已成功移到回收站,`false` 表示 API 调用失败(已记录
     * `DELETE_FAILED` 事件)。
     *
     * 失败不抛异常(降级记录),让调用方根据返回值决定是否继续后续 ref 删除 +
     * 重建流程。调用方负责删除本地 ref 和创建新文档。
     */
    suspend fun deleteDoc(ref: FeishuRefEntity): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            feishuApi.deleteFile(ref.docId)
            recordEventSafe(ref.noteId, SyncDirection.PUSH, "DELETED", null)
            true
        }.getOrElse { e ->
            Log.w(TAG, "deleteDoc failed for ${ref.docId}: ${e.message}")
            recordEventSafe(ref.noteId, SyncDirection.PUSH, "DELETE_FAILED", e.message)
            false
        }
    }

    private suspend fun recordEventSafe(
        noteId: String,
        direction: SyncDirection,
        status: String,
        errorMessage: String?
    ) {
        try {
            eventDao.insert(
                FeishuSyncEventEntity(
                    id = UUID.randomUUID().toString(),
                    noteId = noteId,
                    direction = direction,
                    status = status,
                    errorMessage = errorMessage,
                    createdAt = System.currentTimeMillis()
                )
            )
        } catch (e: IOException) {
            Log.w(TAG, "recordEvent failed: ${e.message}")
        }
    }

    private fun buildAppendXml(content: String): String = "<document><p>" + xmlEscape(content) + "</p></document>"

    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    companion object {
        private const val TAG = "FeishuDocService"
    }
}

data class FeishuDocContent(
    val docId: String,
    val title: String,
    val revisionId: String,
    val markdown: String
)

private fun extractDocIdFromUrl(url: String): String? {
    val trimmed = url.trim().trimEnd('/')
    // fix-2026-06-26-review-r3 HIGH H14:split `?` 之前先去掉 query/fragment，避免
    // 形如 `https://f.cn/docx/d1?from=copy` 被误判为 last segment 含 `?` → null。
    val withoutQuery = trimmed.substringBefore('?').substringBefore('#')
    val last = withoutQuery.substringAfterLast('/')
    return last.takeIf { it.isNotBlank() }
}
