package com.yy.writingwithai.core.media

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * media-attachment-infrastructure · 附件文件存储。
 * 内部存储路径: filesDir/attachments/{noteId}/{attachmentId}.{ext}
 */
@Singleton
class AttachmentStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val attachmentsDir: File
        get() = File(context.filesDir, "attachments")

    fun getAttachmentFile(noteId: String, attachmentId: String, extension: String): File {
        val dir = File(attachmentsDir, noteId)
        dir.mkdirs()
        return File(dir, "$attachmentId.$extension")
    }

    fun save(inputStream: InputStream, noteId: String, attachmentId: String, extension: String): File {
        val file = getAttachmentFile(noteId, attachmentId, extension)
        file.parentFile?.mkdirs()
        file.outputStream().use { out -> inputStream.copyTo(out) }
        return file
    }

    fun delete(noteId: String, attachmentId: String, extension: String): Boolean {
        return getAttachmentFile(noteId, attachmentId, extension).delete()
    }

    fun deleteAllForNote(noteId: String): Boolean {
        val dir = File(attachmentsDir, noteId)
        return dir.deleteRecursively()
    }

    fun listForNote(noteId: String): List<File> {
        val dir = File(attachmentsDir, noteId)
        return if (dir.exists()) dir.listFiles()?.toList() ?: emptyList() else emptyList()
    }
}
