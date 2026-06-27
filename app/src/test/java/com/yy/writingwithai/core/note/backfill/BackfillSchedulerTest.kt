package com.yy.writingwithai.core.note.backfill

import android.content.Context
import android.content.SharedPreferences
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.yy.writingwithai.core.prefs.NoteAssociationSettingsStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * R5-7:BackfillScheduler 新方法无 test。补测:
 * - scheduleEntityBackfillIfNeeded: pause guard + PREF_DONE guard
 * - scheduleEntityBackfillNow: force bypass / non-force pause guard
 * - scheduleEntityBackfillResume: pause guard
 * - pauseEntityBackfill: cancel tag only, no PREF_DONE write
 */
class BackfillSchedulerTest {

    private val context = mockk<Context>()
    private val assocSettings = mockk<NoteAssociationSettingsStore>()
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)

    private lateinit var scheduler: BackfillScheduler

    @BeforeEach
    fun setUp() {
        every { context.getSharedPreferences(any(), any()) } returns prefs
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(context) } returns workManager
        scheduler = BackfillScheduler(context, assocSettings)
    }

    @Test
    fun `scheduleEntityBackfillIfNeeded returns early when paused`() {
        every { assocSettings.pauseBackfill() } returns true

        scheduler.scheduleEntityBackfillIfNeeded()

        verify(exactly = 0) { prefs.getBoolean(any(), any()) }
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `scheduleEntityBackfillIfNeeded returns early when PREF_ENTITY_BACKFILL_DONE is true`() {
        every { assocSettings.pauseBackfill() } returns false
        every { prefs.getBoolean("backfill_entity_v1_done", false) } returns true

        scheduler.scheduleEntityBackfillIfNeeded()

        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `scheduleEntityBackfillIfNeeded enqueues when not paused and not done`() {
        every { assocSettings.pauseBackfill() } returns false
        every { prefs.getBoolean("backfill_entity_v1_done", false) } returns false

        scheduler.scheduleEntityBackfillIfNeeded()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `scheduleEntityBackfillNow with force=true bypasses pause guard`() {
        every { assocSettings.pauseBackfill() } returns true

        scheduler.scheduleEntityBackfillNow(force = true)

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `scheduleEntityBackfillNow with force=false respects pause guard`() {
        every { assocSettings.pauseBackfill() } returns true

        scheduler.scheduleEntityBackfillNow(force = false)

        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `scheduleEntityBackfillResume returns early when paused`() {
        every { assocSettings.pauseBackfill() } returns true

        scheduler.scheduleEntityBackfillResume()

        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `scheduleEntityBackfillResume enqueues when not paused`() {
        every { assocSettings.pauseBackfill() } returns false

        scheduler.scheduleEntityBackfillResume()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `pauseEntityBackfill cancels by tag without writing PREF_DONE`() {
        scheduler.pauseEntityBackfill()

        verify(exactly = 1) { workManager.cancelAllWorkByTag(BackfillScheduler.ENTITY_BACKFILL_TAG) }
        // R5-1 contract:pause path must NOT write PREF_ENTITY_BACKFILL_DONE
        verify(exactly = 0) { prefs.edit() }
    }
}
