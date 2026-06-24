package com.yy.writingwithai.core.media

import android.content.Context
import com.yy.writingwithai.core.security.PathSafety
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * media-attachment-infrastructure · 附件文件存储。
 * 内部存储路径: filesDir/attachments/{noteId}/{attachmentId}.{ext}
 *
 * fix-2026-06-24-review-r1-critical · 路径校验:
 * - `noteId` / `attachmentId` 走 `PathSafety.SAFE_ID`(`^[A-Za-z0-9_-]{1,64}$`)
 * - `extension` 走 `PathSafety.SAFE_EXT`(`^[A-Za-z0-9]{1,8}$`)
 * - resolve 后 canonical 必须 inside `attachmentsDir`
 * - 失败抛 [IllegalArgumentException](调用方 I/O 边界 catch 转 Result.failure)
 */
@Singleton
class AttachmentStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val attachmentsDir: File
        get() = File(context.filesDir, "attachments")

    fun getAttachmentFile(noteId: String, attachmentId: String, extension: String): File {
        PathSafety.requireSafeId(noteId, "noteId")
        PathSafety.requireSafeId(attachmentId, "attachmentId")
        PathSafety.requireSafeExt(extension)
        val dir = File(attachmentsDir, noteId)
        dir.mkdirs()
        val file = File(dir, "$attachmentId.$extension")
        PathSafety.assertContainedUnder(file, attachmentsDir)
        return file
    }

    fun save(inputStream: InputStream, noteId: String, attachmentId: String, extension: String): File {
        PathSafety.requireSafeId(noteId, "noteId")
        PathSafety.requireSafeId(attachmentId, "attachmentId")
        PathSafety.requireSafeExt(extension)
        val file = getAttachmentFile(noteId, attachmentId, extension)
        file.parentFile?.mkdirs()
        file.outputStream().use { out -> inputStream.copyTo(out) }
        return file
    }

    fun delete(noteId: String, attachmentId: String, extension: String): Boolean {
        PathSafety.requireSafeId(noteId, "noteId")
        PathSafety.requireSafeId(attachmentId, "attachmentId")
        PathSafety.requireSafeExt(extension)
        return getAttachmentFile(noteId, attachmentId, extension).delete()
    }

    fun deleteAllForNote(noteId: String): Boolean {
        PathSafety.requireSafeId(noteId, "noteId")
        val dir = File(attachmentsDir, noteId)
        PathSafety.assertContainedUnder(dir, attachmentsDir)
        return dir.deleteRecursively()
    }

    fun listForNote(noteId: String): List<File> {
        PathSafety.requireSafeId(noteId, "noteId")
        val dir = File(attachmentsDir, noteId)
        PathSafety.assertContainedUnder(dir, attachmentsDir)
        return if (dir.exists()) dir.listFiles()?.toList() ?: emptyList() else emptyList()
    }
}
