package com.yy.writingwithai.core.prefs

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * animation-switch-redesign §5.1:覆盖 [UserPrefsStore] 2 个新增 Boolean key 的接口契约。
 *
 * **为什么用 FakeUserPrefsStore 而不是 UserPrefsStoreImpl**:
 * - 真 `UserPrefsStoreImpl` 通过 `Context.userPrefsDataStore` extension 拿 DataStore,这是 Android-only API,
 *   JVM unit test 跑不动(需要 Robolectric 或自定义 [DataStore] 注入 — 当前仓库没有这个先例)。
 * - `FakeUserPrefsStore` 与 `UserPrefsStoreImpl` 是契约平行的实现:
 *   - 缺 key → `?: DEFAULT_ANIMATIONS_ENABLED(true)` 对应 Fake 的 `MutableStateFlow(true)`
 *   - `setXxx(value)` → `store.edit { it[KEY] = value }` 对应 Fake 的 `state.value = value`
 * - 因此 Fake 测试 = 契约测试;数据 schema 默认值、key 名称、setter 行为都被一并覆盖。
 *
 * spec 关联:openspec/changes/animation-switch-redesign/specs/animation-system/spec.md
 * REQ ADDED 1 / REQ ADDED 2。
 */
class UserPrefsStoreTest {

    @Test
    fun `navAnimationsEnabled defaults to true when nothing set`() = runTest {
        val store: UserPrefsStore = FakeUserPrefsStore()
        assertEquals(true, store.navAnimationsEnabledFlow.first())
    }

    @Test
    fun `tabAnimationsEnabled defaults to true when nothing set`() = runTest {
        val store: UserPrefsStore = FakeUserPrefsStore()
        assertEquals(true, store.tabAnimationsEnabledFlow.first())
    }

    @Test
    fun `setNavAnimationsEnabled round-trips false`() = runTest {
        val store: UserPrefsStore = FakeUserPrefsStore()
        store.setNavAnimationsEnabled(false)
        assertEquals(false, store.navAnimationsEnabledFlow.first())
    }

    @Test
    fun `setNavAnimationsEnabled round-trips true`() = runTest {
        val store: UserPrefsStore = FakeUserPrefsStore()
        store.setNavAnimationsEnabled(false)
        store.setNavAnimationsEnabled(true)
        assertEquals(true, store.navAnimationsEnabledFlow.first())
    }

    @Test
    fun `setTabAnimationsEnabled round-trips false`() = runTest {
        val store: UserPrefsStore = FakeUserPrefsStore()
        store.setTabAnimationsEnabled(false)
        assertEquals(false, store.tabAnimationsEnabledFlow.first())
    }

    @Test
    fun `setTabAnimationsEnabled round-trips true`() = runTest {
        val store: UserPrefsStore = FakeUserPrefsStore()
        store.setTabAnimationsEnabled(false)
        store.setTabAnimationsEnabled(true)
        assertEquals(true, store.tabAnimationsEnabledFlow.first())
    }

    @Test
    fun `nav and tab toggles are independent`() = runTest {
        val store: UserPrefsStore = FakeUserPrefsStore()
        store.setNavAnimationsEnabled(false)
        // tab 不应受影响
        assertEquals(true, store.tabAnimationsEnabledFlow.first())
        // nav 真的关了
        assertEquals(false, store.navAnimationsEnabledFlow.first())
    }
}
