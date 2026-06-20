package com.yy.writingwithai.core.prefs

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * M4-4 · FakeConsentStore 单测(用 fake 验证 API 行为,不依赖真 DataStore)。
 */
class FakeConsentStoreTest {
    @Test
    fun `initial state is EMPTY (not consented)`() = runTest {
        val store = FakeConsentStore()
        assertEquals(ConsentState.EMPTY, store.consentFlow.value)
        assertFalse(store.isConsented(currentVersion = 1))
    }

    @Test
    fun `setAccepted then isConsented returns true for matching version`() = runTest {
        val store = FakeConsentStore()
        store.setAccepted(version = 1, at = 1700000000000L)
        assertTrue(store.isConsented(currentVersion = 1))
        assertTrue(store.isConsented(currentVersion = 0)) // 旧版也算
    }

    @Test
    fun `setAccepted with lower version than current returns false`() = runTest {
        val store = FakeConsentStore()
        store.setAccepted(version = 1, at = 1L)
        assertFalse(store.isConsented(currentVersion = 2))
    }

    @Test
    fun `seed overrides state for tests`() = runTest {
        val store = FakeConsentStore()
        store.seed(ConsentState(accepted = true, acceptedAt = 42L, version = 3))
        assertEquals(ConsentState(accepted = true, acceptedAt = 42L, version = 3), store.consentFlow.value)
        assertTrue(store.isConsented(currentVersion = 3))
    }
}
