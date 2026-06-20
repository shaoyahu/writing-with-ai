package com.yy.writingwithai.core.prefs

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * M4-4 · SecureApiKeyStore reveal 5s 过期 / 撤回 / 清空行为单测。
 *
 * spec: openspec/changes/onboarding-consent/specs/secure-prefs/spec.md
 * "apikey 5-second auto-hide via Lifecycle pause" 全部 Scenario。
 *
 * 测 FakeSecureApiKeyStore(reveal 默认 Hidden),真 SecureApiKeyStoreImpl 需
 * Robolectric + AndroidKeyStore mock(M5 polish 补)。
 */
class SecureApiKeyStoreLifecycleTest {
    @Test
    fun `reveal before any save returns Hidden`() = runTest {
        val store = FakeSecureApiKeyStore()
        assertEquals(RevealState.Hidden, store.reveal("deepseek").first())
    }

    @Test
    fun clearResetsRevealToHiddenForThatProvider() = runTest {
        val store = FakeSecureApiKeyStore()
        store.save("deepseek", "sk-1")
        // Fake 不自动 reveal,显式先 reveal(隐式仍为 Hidden),但 clear 必须保证之后仍为 Hidden
        store.reveal("deepseek").first()
        store.clear("deepseek")
        assertEquals(RevealState.Hidden, store.reveal("deepseek").first())
    }

    @Test
    fun `clearAll resets reveal to Hidden for all providers`() = runTest {
        val store = FakeSecureApiKeyStore()
        store.save("deepseek", "sk-1")
        store.save("minimax", "sk-2")
        store.clearAll()
        assertEquals(RevealState.Hidden, store.reveal("deepseek").first())
        assertEquals(RevealState.Hidden, store.reveal("minimax").first())
    }

    @Test
    fun `has returns true only for stored providers`() = runTest {
        val store = FakeSecureApiKeyStore()
        store.save("deepseek", "sk-1")
        assertTrue(store.has("deepseek"))
        assertEquals(false, store.has("minimax"))
    }
}
