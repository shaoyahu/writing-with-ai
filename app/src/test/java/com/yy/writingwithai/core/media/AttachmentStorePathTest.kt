package com.yy.writingwithai.core.media

import java.io.ByteArrayInputStream
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AttachmentStorePathTest {

    private val api: ValidatingAttachmentApi = TestableAttachmentStore.validate

    @Test
    fun `getAttachmentFile rejects dotdot noteId`() {
        assertThrows(IllegalArgumentException::class.java) {
            api.getAttachmentFile("../etc", "a1", "jpg")
        }
    }

    @Test
    fun `getAttachmentFile rejects slash in attachmentId`() {
        assertThrows(IllegalArgumentException::class.java) {
            api.getAttachmentFile("n1", "a/1", "jpg")
        }
    }

    @Test
    fun `getAttachmentFile rejects bad extension`() {
        assertThrows(IllegalArgumentException::class.java) {
            api.getAttachmentFile("n1", "a1", "toolongext")
        }
        assertThrows(IllegalArgumentException::class.java) {
            api.getAttachmentFile("n1", "a1", "jpg/../")
        }
    }

    @Test
    fun `save rejects bad ids`() {
        assertThrows(IllegalArgumentException::class.java) {
            api.save(ByteArrayInputStream(byteArrayOf()), "../etc", "a1", "jpg")
        }
    }

    @Test
    fun `delete rejects bad ids`() {
        assertThrows(IllegalArgumentException::class.java) {
            api.delete("n1", "../../etc", "jpg")
        }
    }

    @Test
    fun `deleteAllForNote rejects bad noteId`() {
        assertThrows(IllegalArgumentException::class.java) {
            api.deleteAllForNote("..")
        }
    }
}
