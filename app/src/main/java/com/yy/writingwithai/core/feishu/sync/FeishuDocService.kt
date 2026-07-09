package com.yy.writingwithai.core.feishu.sync

import android.util.Log
import com.yy.writingwithai.core.data.db.dao.NoteAttachmentDao
import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.core.feishu.api.FeishuApiClient
import com.yy.writingwithai.core.feishu.api.FeishuError
import com.yy.writingwithai.core.feishu.converter.MarkdownToXmlConverter
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
    private val eventDao: FeishuSyncEventDao,
    // feishu-sync-image-support:同步时读取 note 的附件并追加到飞书 doc
    private val noteAttachmentDao: NoteAttachmentDao
) {

    /**
     * feishu-folder-migration · 把用户输入的 folder token resolve 成真实 token。
     *
     * 用于 mismatch 检测时与 ref.folderToken 比较（两边都是 resolved 值）。
     * 返回 resolved token 或 null（输入为 null 时）。
     */
    suspend fun resolveFolderToken(rawToken: String): String? = withContext(Dispatchers.IO) {
        val resolved = feishuApi.resolveFolderToken(rawToken)
        if (resolved.resolved && resolved.originalType != null && resolved.originalType != "folder") {
            Log.i(TAG, "resolveFolderToken: $rawToken (type=${resolved.originalType}) → ${resolved.token}")
        }
        resolved.token
    }

    /**
     * fix(feishu-title-sync):v2 docs_ai/v1 创建文档时无法设置标题,
     * 改用 v1 API 创建空文档(带标题) → v2 API 覆盖内容。
     *
     * 步骤:
     * 1. resolveFolderToken(处理 wiki token)
     * 2. v1 createDocument(title, folderToken) 创建空文档,标题正确
     * 3. v2 updateDocumentV2(xml) 覆盖内容,标题保留在元数据中
     * 4. upsert ref + 记录事件
     */
    suspend fun createDoc(note: Note, folderToken: String? = null): FeishuRefEntity = withContext(Dispatchers.IO) {
        val title = note.title.ifBlank { "未命名笔记" }
        val xml = xmlConverter.convert(note.content, title)

        // fix(feishu-folder-token):用户可能输入 wiki 类型的 token，需要先通过
        // drive/v1/metas/batch_query 解析为真实的 docx folder token。
        val resolvedFolderToken = if (folderToken != null) {
            resolveFolderToken(folderToken)
        } else {
            null
        }

        // fix(feishu-title-sync):v1 创建文档(设置标题) + v2 覆盖内容(保留标题)
        val created = feishuApi.createDocument(title, resolvedFolderToken)
        val meta = feishuApi.updateDocumentV2(created.docId, xml)

        val now = System.currentTimeMillis()
        val ref = FeishuRefEntity(
            noteId = note.id,
            docId = created.docId,
            docUrl = created.docUrl,
            lastSyncedAt = now,
            syncDirection = SyncDirection.PUSH,
            localRevision = note.updatedAt,
            remoteRevision = meta?.revisionId ?: "rev-1",
            status = FeishuRefStatus.SYNCED,
            // feishu-folder-migration:记录创建文档时使用的 folder token，
            // 用于后续 push 时检测 folder token 是否变更。
            folderToken = resolvedFolderToken
        )
        refDao.upsert(ref)
        recordEventSafe(note.id, SyncDirection.PUSH, "OK", null)
        // feishu-sync-image-support:markdown 文本同步成功后,串行 upload + 一次
        // appendChildren 把 note_attachments 里的图片追加到 doc 末尾。
        syncAttachments(note, ref)
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
     *
     * fix(feishu-remote-deleted):远端文档已删除时 v2 update 返回 404,
     * 需标记 ref 为 REMOTE_DELETED 并抛 NotFound 让调用方决定是否重新创建。
     */
    suspend fun updateDoc(note: Note, ref: FeishuRefEntity): FeishuRefEntity = withContext(Dispatchers.IO) {
        val title = note.title.ifBlank { "未命名笔记" }
        val xml = xmlConverter.convert(note.content, title)
        try {
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
            // feishu-sync-image-support:markdown 同步成功后追加附件图片 block。
            syncAttachments(note, updated)
            updated
        } catch (e: FeishuError.NotFound) {
            // 远端文档已删除,标记 ref 并通知调用方
            refDao.upsert(ref.copy(status = FeishuRefStatus.REMOTE_DELETED))
            recordEventSafe(note.id, SyncDirection.PUSH, "REMOTE_DELETED", "doc ${ref.docId} not found")
            throw e
        }
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

    /**
     * fix M18 (full-review):拉取文档根 block_id(用于 image block 的 parent)。
     * 失败时 throw 给上层 catch — 原本 3 段复制粘贴 retry 块全调这个 helper,
     * 解析 / 空 items 判断只此一处,避免改了 logic 忘了同步某分支。
     *
     * @throws EmptyBlocksException 当返回 items 为 null/空(原版直接走 placeholder fallback)
     */
    private suspend fun fetchRootBlockId(docId: String): String {
        val blocksJson = feishuApi.getBlocks(docId)
        val blocksObj = Json.parseToJsonElement(blocksJson).jsonObject
        val dataObj = blocksObj["data"]?.jsonObject ?: blocksObj
        val items = dataObj["items"]?.jsonArray
        if (items == null || items.isEmpty()) throw EmptyBlocksException()
        return items[0].jsonObject["block_id"]?.jsonPrimitive?.content ?: docId
    }

    private class EmptyBlocksException : RuntimeException("blocks_empty")

    // fix-full-review:此 xmlEscape 与 MarkdownToXmlConverter.escape() 逻辑重复。
    // 未来修改必须同步两处，或改为委托 xmlConverter 暴露的公开 escape 方法。
    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    /**
     * feishu-sync-image-support · 把 note 的附件图片同步到飞书 doc 末尾。
     *
     * 流程(spec Sync attachments after markdown):
     * 1. noteAttachmentDao.getForNote(note.id) 拿该 note 所有附件,空 → Success
     * 2. 串行 uploadMedia(每张之间 await,符合 5 QPS 限制)
     *    - 任一 uploadMedia 抛非 NotFound 异常 → 全部 attachmentId 降级为文本占位符
     * 3. 全部 token 拿到后,构造 children JSON 数组(每项 block_type=27 + image.token),
     *    调 appendChildren 一次性提交(超过 50 项分批,飞书 API 单次最多 50 children)
     *    - appendChildren 抛错 → 全部 attachmentId 降级为文本占位符
     * 4. 降级路径:拼 `<document><p>[图片:id1]</p>...</document>` 走 appendBlockV2 追加,
     *    并写 IMAGE_FAIL_PARTIAL sync event
     *
     * 失败不影响 markdown 同步结果 —— ref 已 SYNCED,事件追加即可。
     */
    suspend fun syncAttachments(note: Note, ref: FeishuRefEntity): ImageSyncOutcome = withContext(Dispatchers.IO) {
        val attachments = noteAttachmentDao.getForNote(note.id)
        if (attachments.isEmpty()) {
            return@withContext ImageSyncOutcome.Success
        }
        val sortedAttachments = attachments.sortedBy { it.createdAt }

        // 0. 获取文档根 block_id (用于作为 image block 的 parent)
        // fix-2026-07-05-review-r4 MEDIUM M2:getBlocks 失败时增加 1 次重试
        // 处理网络抖动导致的临时失败
        // fix M18 (full-review):抽 helper 消重试分支的 3 段复制粘贴。
        val rootBlockId = try {
            fetchRootBlockId(ref.docId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: EmptyBlocksException) {
            return@withContext fallbackToPlaceholders(note, ref, sortedAttachments, "blocks_empty")
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "syncAttachments: getBlocks timeout, retrying once")
            try {
                fetchRootBlockId(ref.docId)
            } catch (e2: kotlinx.coroutines.CancellationException) {
                throw e2
            } catch (e2: EmptyBlocksException) {
                return@withContext fallbackToPlaceholders(note, ref, sortedAttachments, "blocks_empty")
            } catch (e2: Exception) {
                Log.w(TAG, "syncAttachments: getBlocks retry failed: ${e2.message}")
                return@withContext fallbackToPlaceholders(note, ref, sortedAttachments, "get_blocks_fail:${e2.message}")
            }
        } catch (e: java.io.IOException) {
            Log.w(TAG, "syncAttachments: getBlocks IO error, retrying once")
            try {
                fetchRootBlockId(ref.docId)
            } catch (e2: kotlinx.coroutines.CancellationException) {
                throw e2
            } catch (e2: EmptyBlocksException) {
                return@withContext fallbackToPlaceholders(note, ref, sortedAttachments, "blocks_empty")
            } catch (e2: Exception) {
                Log.w(TAG, "syncAttachments: getBlocks retry failed: ${e2.message}")
                return@withContext fallbackToPlaceholders(note, ref, sortedAttachments, "get_blocks_fail:${e2.message}")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "syncAttachments: getBlocks failed: ${e.message}")
            return@withContext fallbackToPlaceholders(note, ref, sortedAttachments, "get_blocks_fail:${e.message}")
        }

        // fix-2026-07-05-feishu-image-sync:修正飞书图片同步流程
        // 正确流程：先创建空 image block → 上传素材到 block_id → 飞书自动绑定
        // 参考 https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/drive-v1/media/introduction
        val failedIds = mutableListOf<String>()
        for (att in sortedAttachments) {
            val file = File(att.localPath)
            if (!file.exists()) {
                Log.w(TAG, "syncAttachments: attachment ${att.id} file missing at ${att.localPath}")
                failedIds += att.id
                continue
            }
            val mime = att.mimeType.ifBlank { "image/jpeg" }

            try {
                // fix-2026-07-05-feishu-image:飞书官方文档三步法插入图片
                // 参考 https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/drive-v1/media/introduction
                // 以及 lark-sdk-go: 创建空 image block 时传 image:{}（空的 Image 对象）

                // 步骤 1: 创建空的 image block（传 "image":{} 空对象）
                // 参考 lark-sdk-go: Image 结构体所有字段 omitempty，可以传空对象
                Log.d(TAG, "syncAttachments: step 1 - creating empty image block for ${att.id}")
                val imageBlockId = feishuApi.createBlock(ref.docId, rootBlockId, 27, "\"image\":{}")
                Log.d(TAG, "syncAttachments: step 1 done - image block created, block_id=$imageBlockId")

                // 步骤 2: 上传素材，parent_node 传 imageBlockId，extra.drive_route_token 传 docId
                Log.d(TAG, "syncAttachments: step 2 - uploading media, parent_node=$imageBlockId")
                val uploadResult = feishuApi.uploadMedia(ref.docId, imageBlockId, file, mime)
                Log.d(TAG, "syncAttachments: step 2 done - media uploaded, token=${uploadResult.fileToken}")

                // 步骤 3: 使用 batch_update 的 replace_image 绑定 token 到 block
                Log.d(TAG, "syncAttachments: step 3 - binding token via replaceImageBlock")
                feishuApi.replaceImageBlock(ref.docId, imageBlockId, uploadResult.fileToken)
                Log.d(TAG, "syncAttachments: step 3 done - token bound to block")

                Log.i(
                    TAG,
                    "syncAttachments: image ${att.id} synced, block_id=$imageBlockId, " +
                        "token=${uploadResult.fileToken}"
                )
            } catch (e: FeishuError.NotFound) {
                // 远端 doc 已删 → 不降级,直接抛出
                throw e
            } catch (e: FeishuError) {
                Log.w(TAG, "syncAttachments: image ${att.id} failed: ${e.message}")
                failedIds += att.id
            }
        }

        if (failedIds.isEmpty()) {
            ImageSyncOutcome.Success
        } else {
            fallbackToPlaceholders(note, ref, sortedAttachments, "image_fail:${failedIds.joinToString(",")}")
        }
    }

    /**
     * feishu-sync-image-support · 把全部 attachmentId 转成 `[图片:<id>]` 文本占位符追加到 doc,
     * 写 IMAGE_FAIL_PARTIAL 事件。返回 PartialFail。
     */
    private suspend fun fallbackToPlaceholders(
        note: Note,
        ref: FeishuRefEntity,
        attachments: List<com.yy.writingwithai.core.data.db.entity.NoteAttachmentEntity>,
        reason: String
    ): ImageSyncOutcome {
        val xml = buildPlaceholdersXml(attachments.map { it.id })
        try {
            feishuApi.appendBlockV2(ref.docId, xml)
        } catch (e: FeishuError) {
            // 降级路径还失败 → 写 IMAGE_FAIL_PARTIAL_DOUBLE_FAIL 让排查,但不抛
            Log.w(TAG, "fallbackToPlaceholders appendBlockV2 failed: ${e.message}")
            recordEventSafe(
                note.id,
                SyncDirection.PUSH,
                "IMAGE_FAIL_PARTIAL_DOUBLE_FAIL",
                "original=$reason fallback=${e.message}"
            )
            return ImageSyncOutcome.PartialFail(
                failedIds = attachments.map { it.id },
                reason = "$reason;fallback_fail=${e.message}"
            )
        }
        recordEventSafe(
            note.id,
            SyncDirection.PUSH,
            "IMAGE_FAIL_PARTIAL",
            reason
        )
        return ImageSyncOutcome.PartialFail(
            failedIds = attachments.map { it.id },
            reason = reason
        )
    }

    private fun buildPlaceholdersXml(attachmentIds: List<String>): String {
        val sb = StringBuilder("<document>")
        attachmentIds.forEach { id ->
            sb.append("<p>").append(xmlEscape("[图片:$id]")).append("</p>")
        }
        sb.append("</document>")
        return sb.toString()
    }

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
