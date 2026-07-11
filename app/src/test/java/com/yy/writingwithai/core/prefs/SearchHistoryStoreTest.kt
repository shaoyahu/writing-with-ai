package com.yy.writingwithai.core.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// C6 修:`SearchHistoryStore` 走 `Context.preferencesDataStore` 必须 Robolectric 提供
// 真实 `Context`。JUnit5 vintage engine 桥接后 `@RunWith(RobolectricTestRunner::class)`
// 才能在 `useJUnitPlatform()` 环境跑起来。
//
// fix M14 (full-review):改用 Hilt 注入的 [SearchHistoryStoreImpl] 实例方法替代
// 之前的静态 [SearchHistoryStoreLegacy] facade(object + Context 传参)。
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchHistoryStoreTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<android.content.Context>()
    }

    private fun newStore(): SearchHistoryStoreImpl {
        val ctx = context.applicationContext
        // preferencesDataStore 是 Context 扩展属性，绑死 Application 实例；直接 new 同类型
        // Impl 会复用同一个 DataStore 单例(file "search_history.preferences_pb")。
        // 用 spy + relax 没意义；走真实 Impl + Robolectric Context 即可。
        return SearchHistoryStoreImpl(ctx)
    }

    @Test
    fun `add and getAll`() = runTest {
        val store = newStore()
        store.clear()
        store.add("hello")
        val history = store.getAll()
        assertEquals(1, history.size)
        assertEquals("hello", history[0])
    }

    @Test
    fun `dedup on add`() = runTest {
        val store = newStore()
        store.clear()
        store.add("hello")
        store.add("hello")
        val history = store.getAll()
        assertEquals(1, history.size)
    }

    @Test
    fun `remove works`() = runTest {
        val store = newStore()
        store.clear()
        store.add("hello")
        store.remove("hello")
        val history = store.getAll()
        assertTrue(history.isEmpty())
    }
}
