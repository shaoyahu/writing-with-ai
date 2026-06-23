package com.yy.writingwithai.core.prefs

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
