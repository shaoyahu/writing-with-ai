package com.yy.writingwithai.core.prefs

import com.yy.writingwithai.core.ai.api.WritingOp
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * custom-prompt-template · PromptTemplateStore 单测(用 Fake,真 DataStore 需 Robolectric 留 M5 polish)。
 */
class PromptTemplateStoreTest {
    @Test
    fun `initial state - getForOp returns null for all ops`() = runTest {
        val store = FakePromptTemplateStore()
        assertNull(store.getForOp(WritingOp.EXPAND))
        assertNull(store.getForOp(WritingOp.POLISH))
        assertNull(store.getForOp(WritingOp.ORGANIZE))
    }

    @Test
    fun `setForOp then getForOp returns value`() = runTest {
        val store = FakePromptTemplateStore()
        store.setForOp(WritingOp.POLISH, "你是一位小红书爆款写手...")
        assertEquals("你是一位小红书爆款写手...", store.getForOp(WritingOp.POLISH))
    }

    @Test
    fun `setForOp with empty string - getForOp returns null (fallback rule)`() = runTest {
        val store = FakePromptTemplateStore()
        store.setForOp(WritingOp.ORGANIZE, "非空")
        store.setForOp(WritingOp.ORGANIZE, "")
        assertNull(store.getForOp(WritingOp.ORGANIZE))
    }

    @Test
    fun `resetToDefault - getForOp returns null`() = runTest {
        val store = FakePromptTemplateStore()
        store.setForOp(WritingOp.EXPAND, "非空")
        store.resetToDefault(WritingOp.EXPAND)
        assertNull(store.getForOp(WritingOp.EXPAND))
    }

    @Test
    fun `setForOp one op does not affect others`() = runTest {
        val store = FakePromptTemplateStore()
        store.setForOp(WritingOp.POLISH, "润色新 prompt")
        assertEquals("润色新 prompt", store.getForOp(WritingOp.POLISH))
        assertNull(store.getForOp(WritingOp.EXPAND))
        assertNull(store.getForOp(WritingOp.ORGANIZE))
    }
}
