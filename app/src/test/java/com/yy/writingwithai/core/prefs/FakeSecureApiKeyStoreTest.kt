package com.yy.writingwithai.core.prefs

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * M4-4 · FakeSecureApiKeyStore 单测(用 fake 验证 save/get/clear/clearAll + reveal 行为)。
 */
class FakeSecureApiKeyStoreTest {
    @Test
    fun `save then get returns the apikey`() = runTest {
        val store = FakeSecureApiKeyStore()
        store.save("deepseek", "sk-xxx")
        assertEquals("sk-xxx", store.get("deepseek"))
        assertTrue(store.has("deepseek"))
    }

    @Test
    fun `get returns null for unknown provider`() = runTest {
        val store = FakeSecureApiKeyStore()
        assertNull(store.get("minimax"))
        assertFalse(store.has("minimax"))
    }

    @Test
    fun `clear removes single provider`() = runTest {
        val store = FakeSecureApiKeyStore()
        store.save("deepseek", "sk-1")
        store.save("minimax", "sk-2")
        store.clear("deepseek")
        assertNull(store.get("deepseek"))
        assertEquals("sk-2", store.get("minimax"))
    }

    @Test
    fun `clearAll removes all providers`() = runTest {
        val store = FakeSecureApiKeyStore()
        store.save("deepseek", "sk-1")
        store.save("minimax", "sk-2")
        store.clearAll()
        assertNull(store.get("deepseek"))
        assertNull(store.get("minimax"))
    }

    @Test
    fun `reveal returns Hidden by default (fake does not auto-reveal)`() = runTest {
        val store = FakeSecureApiKeyStore()
        store.save("deepseek", "sk-xxx")
        val state = store.reveal("deepseek").first()
        assertEquals(RevealState.Hidden, state)
    }

    @Test
    fun `clear resets reveal state to Hidden`() = runTest {
        val store = FakeSecureApiKeyStore()
        store.save("deepseek", "sk-xxx")
        store.reveal("deepseek")
        store.clear("deepseek")
        assertEquals(RevealState.Hidden, store.reveal("deepseek").first())
    }
}
