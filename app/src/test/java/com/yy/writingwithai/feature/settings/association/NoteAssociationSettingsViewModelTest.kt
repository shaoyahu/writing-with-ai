package com.yy.writingwithai.feature.settings.association

import android.content.Context
import com.yy.writingwithai.core.note.backfill.BackfillScheduler
import com.yy.writingwithai.core.prefs.NoteAssociationSettingsStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * entity-extraction-polish · NoteAssociationSettingsViewModel 单测。
 *
 * 覆盖:
 * - Init: normal threshold → no migration banner
 * - Init: high threshold (>0.50) → migration banner + reset to 0.10
 * - acknowledgeMigrationBanner hides banner
 * - onThresholdChangeFinished clamps and persists
 * - onPauseToggle(true) → pauseEntityBackfill
 * - onPauseToggle(false) → scheduleEntityBackfillResume
 * - onReRunClick when paused → no-op
 * - onReRunClick when not paused → scheduleEntityBackfillNow(force=true)
 *
 * WorkInfo 订阅依赖 WorkManager.getInstance(context)，难以 mock，跳过。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteAssociationSettingsViewModelTest {

    private val context: Context = mockk(relaxed = true)
    private val assocSettings: NoteAssociationSettingsStore = mockk(relaxed = true)
    private val backfillScheduler: BackfillScheduler = mockk(relaxed = true)

    // Must mock WorkManager.getInstance — use mockkObject or mockkStatic
    // to prevent IllegalStateException in init. Since WorkInfo is skipped,
    // we mock WorkManager.getInstance to return a mock WorkManager.
    private val workManager: androidx.work.WorkManager = mockk(relaxed = true)

    private lateinit var viewModel: NoteAssociationSettingsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkStatic(android.util.Log::class)
        mockkStatic("androidx.work.WorkManager")

        // Default: normal threshold, not paused
        every { assocSettings.threshold() } returns 0.10f
        every { assocSettings.observeThreshold() } returns flowOf(0.10f)
        every { assocSettings.pauseBackfill() } returns false
        every { assocSettings.observePauseBackfill() } returns flowOf(false)

        // Mock WorkManager.getInstance to avoid IllegalStateException
        every { androidx.work.WorkManager.getInstance(any()) } returns workManager
        every { workManager.getWorkInfosByTagFlow(any()) } returns flowOf(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): NoteAssociationSettingsViewModel {
        return NoteAssociationSettingsViewModel(context, assocSettings, backfillScheduler)
    }

    // ===== Init / Migration =====

    @Test
    fun `init with normal threshold does not show migration banner`() {
        every { assocSettings.threshold() } returns 0.10f

        viewModel = createViewModel()

        assertFalse(
            viewModel.migrationBanner.value,
            "Normal threshold MUST NOT show migration banner"
        )
    }

    @Test
    fun `init with high threshold shows migration banner and resets to 0_10`() {
        every { assocSettings.threshold() } returns 0.60f

        viewModel = createViewModel()

        assertTrue(
            viewModel.migrationBanner.value,
            "High threshold (>0.50) MUST show migration banner"
        )
        verify { assocSettings.setThreshold(NoteAssociationSettingsViewModel.THRESHOLD_DEFAULT) }
    }

    // ===== acknowledgeMigrationBanner =====

    @Test
    fun `acknowledgeMigrationBanner hides banner`() {
        every { assocSettings.threshold() } returns 0.60f
        viewModel = createViewModel()
        assertTrue(viewModel.migrationBanner.value, "Precondition: banner should be shown")

        viewModel.acknowledgeMigrationBanner()

        assertFalse(
            viewModel.migrationBanner.value,
            "acknowledgeMigrationBanner MUST hide the banner"
        )
    }

    // ===== onThresholdChangeFinished =====

    @Test
    fun `onThresholdChangeFinished clamps and persists`() {
        viewModel = createViewModel()

        // Value below range → clamped to 0.05
        viewModel.onThresholdChangeFinished(0.01f)
        verify { assocSettings.setThreshold(0.05f) }

        // Value above range → clamped to 0.80
        viewModel.onThresholdChangeFinished(0.99f)
        verify { assocSettings.setThreshold(0.80f) }

        // Value within range → persisted as-is
        viewModel.onThresholdChangeFinished(0.30f)
        verify { assocSettings.setThreshold(0.30f) }
    }

    // ===== onPauseToggle =====

    @Test
    fun `onPauseToggle true calls pauseEntityBackfill`() {
        viewModel = createViewModel()

        viewModel.onPauseToggle(true)

        verify(exactly = 1) { backfillScheduler.pauseEntityBackfill() }
    }

    @Test
    fun `onPauseToggle false calls scheduleEntityBackfillResume`() {
        viewModel = createViewModel()

        viewModel.onPauseToggle(false)

        verify(exactly = 1) { backfillScheduler.scheduleEntityBackfillResume() }
    }

    // ===== onReRunClick =====

    @Test
    fun `onReRunClick when paused does nothing`() = runTest {
        every { assocSettings.pauseBackfill() } returns true

        viewModel = createViewModel()
        viewModel.onReRunClick()

        coVerify(exactly = 0) { backfillScheduler.scheduleEntityBackfillNow(any()) }
    }

    @Test
    fun `onReRunClick when not paused schedules force backfill`() = runTest {
        every { assocSettings.pauseBackfill() } returns false
        coEvery { backfillScheduler.scheduleEntityBackfillNow(any()) } returns mockk()

        viewModel = createViewModel()
        viewModel.onReRunClick()

        coVerify(exactly = 1) { backfillScheduler.scheduleEntityBackfillNow(force = true) }
    }
}
