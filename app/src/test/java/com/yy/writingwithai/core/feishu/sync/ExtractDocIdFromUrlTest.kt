package com.yy.writingwithai.core.feishu.sync

import com.yy.writingwithai.core.feishu.api.FeishuApiClient
import com.yy.writingwithai.core.feishu.api.FeishuError
import com.yy.writingwithai.core.feishu.converter.MarkdownToXmlConverter
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * fix-2026-06-26-review-r3 HIGH H14:extractDocIdFromUrl 必须支持带 query param 的 URL。
 * 之前 `last.contains("?")` 误判 → 飞书"复制链接"产出的 ?from=copy 走不通。
 */
class ExtractDocIdFromUrlTest {

    @Test
    fun `H14 URL with from=copy query is accepted`() = runTest {
        val api = mockk<FeishuApiClient>()
        coEvery { api.getDocument("d-abc") } returns
            com.yy.writingwithai.core.feishu.api.DocMetadata("d-abc", "rev1", "t")
        coEvery { api.fetchDocumentV2("d-abc") } returns "ok"

        val svc = FeishuDocService(api, FakeXmlConverterExt(), FakeFeishuRefDao(), FakeFeishuSyncEventDao())
        val content = svc.readDoc("https://bytedance.feishu.cn/docx/d-abc?from=copy")

        assertEquals("d-abc", content.docId)
        assertEquals("ok", content.markdown)
    }

    @Test
    fun `H14 URL with fragment is accepted`() = runTest {
        val api = mockk<FeishuApiClient>()
        coEvery { api.getDocument("dxyz") } returns
            com.yy.writingwithai.core.feishu.api.DocMetadata("dxyz", "rev1", "t")
        coEvery { api.fetchDocumentV2("dxyz") } returns "body"

        val svc = FeishuDocService(api, FakeXmlConverterExt(), FakeFeishuRefDao(), FakeFeishuSyncEventDao())
        val content = svc.readDoc("https://bytedance.feishu.cn/docx/dxyz#anchor")

        assertEquals("dxyz", content.docId)
    }

    @Test
    fun `H14 plain URL still works`() = runTest {
        val api = mockk<FeishuApiClient>()
        coEvery { api.getDocument("plain") } returns
            com.yy.writingwithai.core.feishu.api.DocMetadata("plain", "rev1", "t")
        coEvery { api.fetchDocumentV2("plain") } returns "x"

        val svc = FeishuDocService(api, FakeXmlConverterExt(), FakeFeishuRefDao(), FakeFeishuSyncEventDao())
        val content = svc.readDoc("https://bytedance.feishu.cn/docx/plain")
        assertEquals("plain", content.docId)
    }

    @Test
    fun `H14 trailing slash URL still extracts docId`() = runTest {
        val api = mockk<FeishuApiClient>()
        coEvery { api.getDocument("trail") } returns
            com.yy.writingwithai.core.feishu.api.DocMetadata("trail", "rev1", "t")
        coEvery { api.fetchDocumentV2("trail") } returns "x"

        val svc = FeishuDocService(api, FakeXmlConverterExt(), FakeFeishuRefDao(), FakeFeishuSyncEventDao())
        val content = svc.readDoc("https://bytedance.feishu.cn/docx/trail/")
        assertEquals("trail", content.docId)
    }

    @Test
    fun `H14 URL with both query and fragment is accepted`() = runTest {
        val api = mockk<FeishuApiClient>()
        coEvery { api.getDocument("combo") } returns
            com.yy.writingwithai.core.feishu.api.DocMetadata("combo", "rev1", "t")
        coEvery { api.fetchDocumentV2("combo") } returns "x"

        val svc = FeishuDocService(api, FakeXmlConverterExt(), FakeFeishuRefDao(), FakeFeishuSyncEventDao())
        val content = svc.readDoc("https://bytedance.feishu.cn/docx/combo?from=copy#h1")
        assertEquals("combo", content.docId)
    }

    @Test
    fun `H14 empty input throws BadRequest`() = runTest {
        val api = mockk<FeishuApiClient>()
        val svc = FeishuDocService(api, FakeXmlConverterExt(), FakeFeishuRefDao(), FakeFeishuSyncEventDao())
        val ex = assertThrows(FeishuError.BadRequest::class.java) {
            kotlinx.coroutines.runBlocking { svc.readDoc("") }
        }
        assertTrue(ex.message?.contains("invalid feishu doc url") == true)
    }
}

internal class FakeXmlConverterExt : MarkdownToXmlConverter() {
    override fun convert(markdown: String, title: String): String = "<document/>"
}
