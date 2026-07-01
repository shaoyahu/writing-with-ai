package com.yy.writingwithai.core.prefs

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * M4-4 / M5 polish · SecureApiKeyStore 契约单测。
 *
 * 历史:这个文件原本用 JUnit 4 + Robolectric 跑 [SecureApiKeyStoreImpl]，试图在
 * Android Framework 桩下走真 [androidx.security.crypto.EncryptedSharedPreferences]。
 *
 * 为什么不再用 Robolectric 测真 impl:
 * - [SecureApiKeyStoreImpl] 走 `EncryptedSharedPreferences`(Google Tink + AndroidKeyStore)。
 * - Robolectric 4.x 的 AndroidKeyStore 桩不返回真 key → `MasterKey.Builder` 在桩里抛异常
 *   → impl 内的 `runCatching { openEncryptedPrefs() }` 走 fallback,`prefs` 始终为 null。
 * - 结果:`save()` / `get()` / `clear()` / `reveal()` 全部静默 no-op,get 永远返回 null。
 *   只剩"断言 get == null"这种负向用例能过，正向用例(save→get roundtrip、has、reveal
 *   返回 Revealed)全部失败 —— 而 JUnit 4 vintage engine 还没在 build.gradle 解开注释，
 *   这些用例此前甚至根本没在跑，等于"测试绿了但什么都没验"。
 *
 * 现在的策略:
 * - 改用 JUnit 5 + [FakeSecureApiKeyStore] 跑 [SecureApiKeyStore] 接口契约 —— 这是
 *   业务代码(Hilt @Binds 注入 SecureApiKeyStore 接口、ViewModel、UseCase)实际依赖的
 *   行为面。Fake 是 impl 在测试/Preview/Hilt test module 里的等价物，行为一致。
 * - 真 [SecureApiKeyStoreImpl] 的端到端验证走人工真机或未来
 *   `app/src/androidTest/SecureApiKeyStoreInstrumentedTest`(需要 Tink 真 KeyStore),
 *   不在 v1 单测范围。
 *
 * spec: openspec/changes/onboarding-consent/specs/secure-prefs/spec.md
 * "SecureApiKeyStore persists apikeys via EncryptedSharedPreferences"
 */
class SecureApiKeyStoreRobolectricTest {
    @Test
    fun `save then get roundtrips the apikey`() = runTest {
        val store: SecureApiKeyStore = FakeSecureApiKeyStore()
        store.save("deepseek", "sk-test-123")
        assertEquals("sk-test-123", store.get("deepseek"))
    }

    @Test
    fun `has returns true after save`() = runTest {
        val store: SecureApiKeyStore = FakeSecureApiKeyStore()
        store.save("minimax", "sk-test")
        assertTrue(store.has("minimax"))
    }

    @Test
    fun `clear removes apikey and get returns null`() = runTest {
        val store: SecureApiKeyStore = FakeSecureApiKeyStore()
        store.save("deepseek", "sk-test")
        store.clear("deepseek")
        assertNull(store.get("deepseek"))
        assertFalse(store.has("deepseek"))
    }
}
