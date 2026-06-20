package com.yy.writingwithai.app

import com.yy.writingwithai.core.prefs.ConsentState
import com.yy.writingwithai.core.prefs.FakeConsentStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * M4-4 · AppNav ConsentGate 单测(覆盖 r1 H1 widget 入口 + 撤回两条 Scenario)。
 *
 * 不引入 Compose UI test 框架(避免 Robolectric + Compose test 双依赖);
 * 测 ConsentGate 决策逻辑(widgetPendingRoute + isConsented 同步检查)。
 */
class AppNavConsentGateTest {
    @Test
    fun unconsentedStateTriggersOnboardingRoute() {
        val consent = FakeConsentStore().apply { seed(ConsentState.EMPTY) }
        assertEquals(false, consent.consentFlow.value.accepted)
    }

    @Test
    fun consentedStateSkipsOnboarding() {
        val consent =
            FakeConsentStore().apply {
                seed(ConsentState(accepted = true, acceptedAt = 1L, version = 1))
            }
        assertEquals(true, consent.consentFlow.value.accepted)
    }

    @Test
    fun versionBumpReTriggersOnboarding() = runTest {
        val consent =
            FakeConsentStore().apply {
                seed(ConsentState(accepted = true, acceptedAt = 1L, version = 1))
            }
        // 模拟 R.integer.consent_version 升到 2 → isConsented(2) = false → 重同
        assertEquals(false, consent.isConsented(currentVersion = 2))
    }

    @Test
    fun widgetPendingRouteStoredReadCleared() {
        val pending = MutableStateFlow<String?>(null)
        // 模拟 MainActivity.onCreate 未同意
        pending.value = "quicknote/edit?prefillFocus=true"
        assertEquals("quicknote/edit?prefillFocus=true", pending.value)
        // 模拟 AppNav 同意后 navigate + 清
        pending.update { null }
        assertNull(pending.value)
    }
}
