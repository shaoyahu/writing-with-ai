package com.yy.writingwithai.core.prefs

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M5 polish · SecureApiKeyStoreImpl Robolectric 测试。
 *
 * 验证 EncryptedSharedPreferences 在 Android Framework 桩下:
 * - save + get roundtrip
 * - has 正确
 * - clear + get = null
 * - reveal 返回 Revealed(含有效 expiresAt)
 *
 * spec: openspec/specs/secure-prefs/spec.md
 * "SecureApiKeyStore persists apikeys via EncryptedSharedPreferences"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecureApiKeyStoreRobolectricTest {
    private lateinit var store: SecureApiKeyStoreImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        store = SecureApiKeyStoreImpl(context)
    }

    @Test
    fun saveGetRoundtrip() = runBlocking {
        store.save("deepseek", "sk-test-123")
        val result = store.get("deepseek")
        assertEquals("sk-test-123", result)
    }

    @Test
    fun hasReturnsTrueAfterSave() = runBlocking {
        store.save("minimax", "sk-test")
        assertTrue(store.has("minimax"))
    }

    @Test
    fun clearRemovesAndGetReturnsNull() = runBlocking {
        store.save("deepseek", "sk-test")
        store.clear("deepseek")
        assertNull(store.get("deepseek"))
        assertEquals(false, store.has("deepseek"))
    }

    @Test
    fun revealReturnsRevealedWithValidExpiry() = runBlocking {
        store.save("deepseek", "sk-test")
        val flow = store.reveal("deepseek")
        val state = flow.value
        assertTrue(state is RevealState.Revealed)
        val revealed = state as RevealState.Revealed
        assertEquals("sk-test", revealed.apikey)
        assertTrue(revealed.expiresAt > System.currentTimeMillis())
    }
}
