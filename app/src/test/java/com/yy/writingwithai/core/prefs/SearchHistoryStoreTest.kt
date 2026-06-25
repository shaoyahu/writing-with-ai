package com.yy.writingwithai.core.prefs

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// C6 修:`SearchHistoryStore` 走 `Context.preferencesDataStore` 必须 Robolectric 提供
// 真实 `Context`。JUnit5 vintage engine 桥接后 `@RunWith(RobolectricTestRunner::class)`
// 才能在 `useJUnitPlatform()` 环境跑起来。
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchHistoryStoreTest {
    @Test
    fun `add and getAll`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        SearchHistoryStore.clear(context)
        SearchHistoryStore.add(context, "hello")
        val history = SearchHistoryStore.getAll(context)
        assertEquals(1, history.size)
        assertEquals("hello", history[0])
    }

    @Test
    fun `dedup on add`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        SearchHistoryStore.clear(context)
        SearchHistoryStore.add(context, "hello")
        SearchHistoryStore.add(context, "hello")
        val history = SearchHistoryStore.getAll(context)
        assertEquals(1, history.size)
    }

    @Test
    fun `remove works`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        SearchHistoryStore.clear(context)
        SearchHistoryStore.add(context, "hello")
        SearchHistoryStore.remove(context, "hello")
        val history = SearchHistoryStore.getAll(context)
        assertTrue(history.isEmpty())
    }
}
