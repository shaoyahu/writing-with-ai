package com.yy.writingwithai.feature.quicknote.detail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.yy.writingwithai.core.data.db.dao.NoteAttachmentDao
import com.yy.writingwithai.core.data.db.dao.entity.NoteEntityDao
import com.yy.writingwithai.core.data.model.Note
import com.yy.writingwithai.core.data.model.NoteWithTags
import com.yy.writingwithai.core.data.repo.NoteRepository
import com.yy.writingwithai.core.feishu.sync.FeishuRefDao
import com.yy.writingwithai.core.feishu.sync.FeishuSyncService
import com.yy.writingwithai.core.media.AttachmentStore
import com.yy.writingwithai.core.media.ImageCompressor
import com.yy.writingwithai.core.note.NoteLinker
import com.yy.writingwithai.core.note.entity.EntityExtractor
import com.yy.writingwithai.core.note.entity.NoteEntityMatcher
import com.yy.writingwithai.core.prefs.SecureApiKeyStore
import com.yy.writingwithai.feature.quicknote.model.NoteDetailUiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickNoteDetailViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: QuickNoteDetailViewModel
    private lateinit var context: Context
    private lateinit var repository: NoteRepository
    private lateinit var feishuSyncService: FeishuSyncService
    private lateinit var refDao: FeishuRefDao
    private lateinit var noteAttachmentDao: NoteAttachmentDao
    private lateinit var attachmentStore: AttachmentStore
    private lateinit var imageCompressor: ImageCompressor
    private lateinit var entityExtractor: EntityExtractor
    private lateinit var noteLinker: NoteLinker
    private lateinit var entityDao: NoteEntityDao
    private lateinit var entityMatcher: NoteEntityMatcher
    private lateinit var secureApiKeyStore: SecureApiKeyStore

    private val testNote = Note(
        id = "note-1",
        title = "Test Title",
        content = "Hello world",
        createdAt = 1000L,
        updatedAt = 2000L,
        isPinned = false,
        lastAiOp = null,
        lastAiAt = null
    )

    private val testNoteWithTags = NoteWithTags(
        note = testNote,
        tags = listOf("kotlin", "android")
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0

        context = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        feishuSyncService = mockk(relaxed = true)
        refDao = mockk(relaxed = true)
        noteAttachmentDao = mockk(relaxed = true)
        attachmentStore = mockk(relaxed = true)
        imageCompressor = mockk(relaxed = true)
        entityExtractor = mockk(relaxed = true)
        noteLinker = mockk(relaxed = true)
        entityDao = mockk(relaxed = true)
        entityMatcher = mockk(relaxed = true)
        secureApiKeyStore = mockk(relaxed = true)

        coEvery { feishuSyncService.getRef(any()) } returns null
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
    fun init_nullNoteId_yieldsNotFoundState() = runTest(dispatcher) {
        val savedState = SavedStateHandle(mapOf<String, Any>())

        viewModel = newViewModel(savedState)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is NoteDetailUiState.NotFound)

        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    @Test
    fun init_validNoteId_loadsContentFromRepository() = runTest(dispatcher) {
        val savedState = SavedStateHandle(mapOf("id" to "note-1"))
        every { repository.observeNoteWithTags("note-1") } returns flowOf(testNoteWithTags)

        viewModel = newViewModel(savedState)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is NoteDetailUiState.Content)
        state as NoteDetailUiState.Content
        assertEquals("Test Title", state.note.note.title)
        assertEquals("Hello world", state.note.note.content)

        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    @Test
    fun delete_callsRepositoryDeleteAndInvokesCallback() = runTest(dispatcher) {
        val savedState = SavedStateHandle(mapOf("id" to "note-1"))
        every { repository.observeNoteWithTags("note-1") } returns flowOf(testNoteWithTags)

        viewModel = newViewModel(savedState)
        advanceUntilIdle()

        var callbackInvoked = false
        viewModel.delete { callbackInvoked = true }
        advanceUntilIdle()

        coVerify { repository.delete("note-1") }
        assertTrue(callbackInvoked)

        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    @Test
    fun clearSyncMessage_resetsToNull() = runTest(dispatcher) {
        val savedState = SavedStateHandle(mapOf("id" to "note-1"))
        every { repository.observeNoteWithTags("note-1") } returns flowOf(testNoteWithTags)

        viewModel = newViewModel(savedState)
        advanceUntilIdle()

        // Simulate a sync message being set
        viewModel.pushToFeishu()
        advanceUntilIdle()
        // After push, syncMessage may be set; clear it
        viewModel.clearSyncMessage()

        assertNull(viewModel.syncMessage.value)

        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    @Test
    fun getExportMarkdown_returnsFormattedMarkdown() = runTest(dispatcher) {
        val savedState = SavedStateHandle(mapOf("id" to "note-1"))
        every { repository.observeNoteWithTags("note-1") } returns flowOf(testNoteWithTags)

        viewModel = newViewModel(savedState)
        advanceUntilIdle()

        val markdown = viewModel.getExportMarkdown()
        assertEquals("# Test Title\n\nHello world", markdown)

        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    @Test
    fun getExportFilename_returnsTitleExtension_orIdExtensionFallback() = runTest(dispatcher) {
        // Case 1: note with non-blank title
        val savedState = SavedStateHandle(mapOf("id" to "note-1"))
        every { repository.observeNoteWithTags("note-1") } returns flowOf(testNoteWithTags)

        viewModel = newViewModel(savedState)
        advanceUntilIdle()

        assertEquals("Test Title.md", viewModel.getExportFilename("md"))

        viewModel.viewModelScope.cancel()
        advanceUntilIdle()

        // Case 2: note with blank title falls back to id
        val blankTitleNote = testNote.copy(title = "")
        val blankTitleWithTags = NoteWithTags(note = blankTitleNote, tags = emptyList())
        val savedState2 = SavedStateHandle(mapOf("id" to "note-1"))
        every { repository.observeNoteWithTags("note-1") } returns flowOf(blankTitleWithTags)

        viewModel = newViewModel(savedState2)
        advanceUntilIdle()

        assertEquals("note-1.md", viewModel.getExportFilename("md"))

        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
    }

    private fun newViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): QuickNoteDetailViewModel =
        QuickNoteDetailViewModel(
            savedStateHandle = savedStateHandle,
            appContext = context,
            repository = repository,
            feishuSyncService = feishuSyncService,
            refDao = refDao,
            noteAttachmentDao = noteAttachmentDao,
            attachmentStore = attachmentStore,
            imageCompressor = imageCompressor,
            entityExtractor = entityExtractor,
            noteLinker = noteLinker,
            entityDao = entityDao,
            entityMatcher = entityMatcher,
            secureApiKeyStore = secureApiKeyStore
        )
}
