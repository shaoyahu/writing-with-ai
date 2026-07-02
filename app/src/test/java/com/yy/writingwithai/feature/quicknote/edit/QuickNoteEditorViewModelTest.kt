package com.yy.writingwithai.feature.quicknote.edit

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.data.db.dao.NoteAttachmentDao
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.core.media.AttachmentStore
import com.yy.writingwithai.core.media.ImageCompressor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickNoteEditorViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: QuickNoteEditorViewModel
    private lateinit var context: Context
    private lateinit var repository: NoteRepository
    private lateinit var noteAttachmentDao: NoteAttachmentDao
    private lateinit var attachmentStore: AttachmentStore
    private lateinit var imageCompressor: ImageCompressor

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0

        context = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        noteAttachmentDao = mockk(relaxed = true)
        attachmentStore = mockk(relaxed = true)
        imageCompressor = mockk(relaxed = true)
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
    fun setTitle_updatesUiStateTitle() = runTest(dispatcher) {
        coEvery { repository.getNote(any()) } returns null
        every { repository.observeNoteWithTags(any()) } returns flowOf(null)

        viewModel = newViewModel(SavedStateHandle(mapOf("id" to "NEW")))

        // Activate WhileSubscribed by collecting uiState
        val collectJob = launch { viewModel.uiState.first() }

        viewModel.setTitle("My Title")
        assertEquals("My Title", viewModel.uiState.value.title)

        collectJob.cancel()
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun setContent_updatesUiStateContent() = runTest(dispatcher) {
        coEvery { repository.getNote(any()) } returns null
        every { repository.observeNoteWithTags(any()) } returns flowOf(null)

        viewModel = newViewModel(SavedStateHandle(mapOf("id" to "NEW")))

        val collectJob = launch { viewModel.uiState.first() }

        viewModel.setContent("Some content here")
        assertEquals("Some content here", viewModel.uiState.value.content)

        collectJob.cancel()
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun addTag_addsTagAndDeduplicates() = runTest(dispatcher) {
        coEvery { repository.getNote(any()) } returns null
        every { repository.observeNoteWithTags(any()) } returns flowOf(null)

        viewModel = newViewModel(SavedStateHandle(mapOf("id" to "NEW")))

        val collectJob = launch { viewModel.uiState.first() }

        viewModel.addTag("kotlin")
        assertEquals(listOf("kotlin"), viewModel.uiState.value.tags)

        viewModel.addTag("android")
        assertEquals(listOf("kotlin", "android"), viewModel.uiState.value.tags)

        // Duplicate should be ignored
        viewModel.addTag("kotlin")
        assertEquals(listOf("kotlin", "android"), viewModel.uiState.value.tags)

        collectJob.cancel()
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun removeTag_removesSpecifiedTag() = runTest(dispatcher) {
        coEvery { repository.getNote(any()) } returns null
        every { repository.observeNoteWithTags(any()) } returns flowOf(null)

        viewModel = newViewModel(SavedStateHandle(mapOf("id" to "NEW")))

        val collectJob = launch { viewModel.uiState.first() }

        viewModel.addTag("kotlin")
        viewModel.addTag("android")
        assertEquals(listOf("kotlin", "android"), viewModel.uiState.value.tags)

        viewModel.removeTag("kotlin")
        assertEquals(listOf("android"), viewModel.uiState.value.tags)

        collectJob.cancel()
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun addAttachment_addsUriToPendingList() = runTest(dispatcher) {
        coEvery { repository.getNote(any()) } returns null
        every { repository.observeNoteWithTags(any()) } returns flowOf(null)

        viewModel = newViewModel(SavedStateHandle(mapOf("id" to "NEW")))

        val uri1 = mockk<Uri>()
        val uri2 = mockk<Uri>()

        viewModel.addAttachment(uri1)
        assertEquals(listOf(uri1), viewModel.pendingAttachmentUris.value)

        viewModel.addAttachment(uri2)
        assertEquals(listOf(uri1, uri2), viewModel.pendingAttachmentUris.value)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun removePendingAttachment_removesUriFromPendingList() = runTest(dispatcher) {
        coEvery { repository.getNote(any()) } returns null
        every { repository.observeNoteWithTags(any()) } returns flowOf(null)

        viewModel = newViewModel(SavedStateHandle(mapOf("id" to "NEW")))

        val uri1 = mockk<Uri>()
        val uri2 = mockk<Uri>()

        viewModel.addAttachment(uri1)
        viewModel.addAttachment(uri2)
        assertEquals(listOf(uri1, uri2), viewModel.pendingAttachmentUris.value)

        viewModel.removePendingAttachment(uri1)
        assertEquals(listOf(uri2), viewModel.pendingAttachmentUris.value)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun tagsSummary_joinsTagsWithHashPrefix() = runTest(dispatcher) {
        coEvery { repository.getNote(any()) } returns null
        every { repository.observeNoteWithTags(any()) } returns flowOf(null)

        viewModel = newViewModel(SavedStateHandle(mapOf("id" to "NEW")))

        // Activate WhileSubscribed on tagsSummary
        val collectJob = launch { viewModel.tagsSummary.first() }

        viewModel.addTag("kotlin")
        viewModel.addTag("android")
        assertEquals("#kotlin #android", viewModel.tagsSummary.value)

        collectJob.cancel()
        viewModel.viewModelScope.cancel()
    }

    private fun newViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): QuickNoteEditorViewModel =
        QuickNoteEditorViewModel(
            savedStateHandle = savedStateHandle,
            appContext = context,
            repository = repository,
            noteAttachmentDao = noteAttachmentDao,
            attachmentStore = attachmentStore,
            imageCompressor = imageCompressor
        )
}
