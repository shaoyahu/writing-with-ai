package com.yy.writingwithai.core.media

import java.io.InputStream

/**
 * Robolectric-free 测试 helper:只跑 [AttachmentStore] 的输入校验逻辑，不实际写文件。
 * 镜像 [AttachmentStore] 的 public API，但只触发 [com.yy.writingwithai.core.security.PathSafety]
 * 校验(无 Android Context 依赖)。
 */
object TestableAttachmentStore {
    val validate: ValidatingAttachmentApi = object : ValidatingAttachmentApi {
        override fun getAttachmentFile(noteId: String, attachmentId: String, extension: String) {
            com.yy.writingwithai.core.security.PathSafety.requireSafeId(noteId, "noteId")
            com.yy.writingwithai.core.security.PathSafety.requireSafeId(attachmentId, "attachmentId")
            com.yy.writingwithai.core.security.PathSafety.requireSafeExt(extension)
        }
        override fun save(input: InputStream, noteId: String, attachmentId: String, extension: String) {
            getAttachmentFile(noteId, attachmentId, extension)
        }
        override fun delete(noteId: String, attachmentId: String, extension: String) {
            getAttachmentFile(noteId, attachmentId, extension)
        }
        override fun deleteAllForNote(noteId: String) {
            com.yy.writingwithai.core.security.PathSafety.requireSafeId(noteId, "noteId")
        }
    }
}

interface ValidatingAttachmentApi {
    fun getAttachmentFile(noteId: String, attachmentId: String, extension: String)
    fun save(input: InputStream, noteId: String, attachmentId: String, extension: String)
    fun delete(noteId: String, attachmentId: String, extension: String)
    fun deleteAllForNote(noteId: String)
}
