package com.yy.writingwithai.feature.settings.alias

import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.data.db.dao.entity.EntityAliasDao
import com.yy.writingwithai.core.data.db.entity.entity.EntityAliasRow
import com.yy.writingwithai.core.note.entity.EntityType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * AliasManagementViewModel 单测:
 * - init 触发 refresh，aliases 从 DAO 加载
 * - merge 发送 Merged 消息并刷新
 * - unmerge 发送 Unmerged 消息并刷新
 * - clearMessage 重置 message 为 null
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AliasManagementViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: AliasManagementViewModel
    private lateinit var aliasDao: EntityAliasDao

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0

        aliasDao = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        if (::viewModel.isInitialized) {
            viewModel.viewModelScope.cancel()
        }
        Dispatchers.resetMain()
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun init_triggersRefresh_aliasesLoadedFromDao() = runTest(dispatcher) {
        val rows = listOf(
            EntityAliasRow(EntityType.PERSON, "person::zhang_san", "person::zhangsan"),
            EntityAliasRow(EntityType.LOCATION, "location::bj", "location::beijing")
        )
        coEvery { aliasDao.listAll() } returns rows

        viewModel = AliasManagementViewModel(aliasDao)
        advanceUntilIdle()

        assertEquals(rows, viewModel.aliases.value)
        coVerify { aliasDao.listAll() }
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    @Test
    fun merge_postsMergedMessage_andRefreshes() = runTest(dispatcher) {
        coEvery { aliasDao.listAll() } returns emptyList()
        viewModel = AliasManagementViewModel(aliasDao)
        advanceUntilIdle()

        viewModel.merge(EntityType.PERSON, "Zhang San", "zhangsan")
        advanceUntilIdle()

        assertEquals(AliasMessage.Merged, viewModel.message.value)
        coVerify {
            aliasDao.upsert(
                match { row ->
                    row.entityType == EntityType.PERSON &&
                        row.aliasKey == EntityType.normalizeKey(EntityType.PERSON, "Zhang San") &&
                        row.canonicalEntityKey == EntityType.normalizeKey(EntityType.PERSON, "zhangsan")
                }
            )
        }
        // refresh was called (listAll invoked at least twice: init + post-merge)
        coVerify(atLeast = 2) { aliasDao.listAll() }
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    @Test
    fun unmerge_postsUnmergedMessage_andRefreshes() = runTest(dispatcher) {
        coEvery { aliasDao.listAll() } returns emptyList()
        viewModel = AliasManagementViewModel(aliasDao)
        advanceUntilIdle()

        viewModel.unmerge(EntityType.PERSON, "person::zhang_san")
        advanceUntilIdle()

        assertEquals(AliasMessage.Unmerged, viewModel.message.value)
        coVerify { aliasDao.deleteByAlias(EntityType.PERSON, "person::zhang_san") }
        // refresh was called (listAll invoked at least twice: init + post-unmerge)
        coVerify(atLeast = 2) { aliasDao.listAll() }
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    @Test
    fun clearMessage_resetsMessageToNull() = runTest(dispatcher) {
        coEvery { aliasDao.listAll() } returns emptyList()
        viewModel = AliasManagementViewModel(aliasDao)
        advanceUntilIdle()

        viewModel.merge(EntityType.WORK, "My Book", "mybook")
        advanceUntilIdle()
        assertEquals(AliasMessage.Merged, viewModel.message.value)

        viewModel.clearMessage()
        assertNull(viewModel.message.value)
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }
}
