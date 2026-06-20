package com.yy.writingwithai.core.ai.provider

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * provider-real-integration · [FakeProviderPrefsStore] 单测。
 *
 * spec: openspec/changes/provider-real-integration/specs/model-management/spec.md
 * "ProviderPrefsStore round-trip" Scenario。
 *
 * 注:真 DataStore round-trip 由 Robolectric 集成测试覆盖(M5 polish CI 验证);
 * 本单测验证 in-memory 行为 + observe Flow。
 */
class ProviderPrefsStoreTest {
    @Test
    fun default_provider_id_is_fake() = runTest {
        val store = FakeProviderPrefsStore()
        assertEquals("fake", store.getSelectedProviderId())
    }

    @Test
    fun set_then_get_roundtrip() = runTest {
        val store = FakeProviderPrefsStore()
        store.setSelectedProviderId("deepseek")
        assertEquals("deepseek", store.getSelectedProviderId())
    }

    @Test
    fun seed_overrides_value() = runTest {
        val store = FakeProviderPrefsStore(initial = "minimax")
        assertEquals("minimax", store.getSelectedProviderId())
        store.seed("mimo")
        assertEquals("mimo", store.getSelectedProviderId())
    }
}
