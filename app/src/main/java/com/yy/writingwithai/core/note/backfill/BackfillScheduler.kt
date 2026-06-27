package com.yy.writingwithai.core.note.backfill

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.yy.writingwithai.core.prefs.NoteAssociationSettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackfillScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assocSettings: NoteAssociationSettingsStore
) {
    fun scheduleIfNeeded() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_BACKFILL_DONE, false)) return

        // R3 fix M7:把重复的 5s 初始延后 + 离线 constraints builder 收到 companion helper,
        // 避免两个方法漂移(已经看到 R1 H16/H17 改其中一个忘改另一个的迹象)。
        val request = OneTimeWorkRequestBuilder<BackfillWorker>()
            .setInitialDelay(INITIAL_DELAY_SECONDS, TimeUnit.SECONDS)
            .setConstraints(noNetworkConstraints())
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME_NOTE, ExistingWorkPolicy.KEEP, request)
        // fix-2026-06-24-review-r1-high H16:flag 移到 Worker.doWork 成功后写;此处不写
    }

    /**
     * entity-extraction-association §7.2:AppDatabase 升级后 enqueue 实体抽取回填(5s 延后)。
     *
     * entity-extraction-polish §3.2:加 [NoteAssociationSettingsStore.pauseBackfill] 守卫 —
     * 已 paused 不调度(用户主动暂停后,不应在冷启动时再被 enqueue)。
     */
    fun scheduleEntityBackfillIfNeeded() {
        // §3.2:pause guard 必须在 PREF_ENTITY_BACKFILL_DONE 检查之前 —— 否则 paused 用户每次冷启动
        // 都会看到 flag==false 然后跳过,但 paused 状态下我们连"计划"都不该做,降低无谓 WorkManager 调度。
        if (assocSettings.pauseBackfill()) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_ENTITY_BACKFILL_DONE, false)) return

        // R3 fix M7:同上,复用 helper。
        // fix H17:加 .addTag(ENTITY_BACKFILL_TAG),cancel tag 真正生效
        val request = OneTimeWorkRequestBuilder<EntityBackfillWorker>()
            .setInitialDelay(INITIAL_DELAY_SECONDS, TimeUnit.SECONDS)
            .setConstraints(noNetworkConstraints())
            .addTag(ENTITY_BACKFILL_TAG)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME_ENTITY, ExistingWorkPolicy.KEEP, request)
        // fix H16:同上 flag 不在这里写
    }

    /**
     * entity-extraction-polish §3.3:用户从设置页「立即重跑回填」按钮触发的强制重排。
     *
     * R4-2-A fix:此方法现在专给"立即重跑"按钮用,REPLACE 策略取消任何 in-flight worker。
     * "pause → unpause" 路径改调 [scheduleEntityBackfillResume](KEEP 策略,不取消 in-flight)。
     *
     * 强制 [force=true] 时绕过 [NoteAssociationSettingsStore.pauseBackfill] 守卫(用户已主动按按钮,
     * 意图明确);Worker `doWork()` 仍会再次自检 pause,保证 paused 状态不被绕过。
     */
    fun scheduleEntityBackfillNow(force: Boolean) {
        if (!force && assocSettings.pauseBackfill()) return

        // 强制重排使用 REPLACE 策略:取消任何 QUEUED / RUNNING 的前任,确保用户立即看到进度。
        val request = OneTimeWorkRequestBuilder<EntityBackfillWorker>()
            .setConstraints(noNetworkConstraints())
            .addTag(ENTITY_BACKFILL_TAG)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME_ENTITY, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * R4-2-A fix:`pause → unpause` 路径专用,KEEP 策略 — 不取消 in-flight worker,保留
     * 已抽取的 N 笔记进度。绕过 [NoteAssociationSettingsStore.pauseBackfill] 检查(本方法
     * 仅在 unpause 即 pause=false 时被调用,但显式双 guard 防 stale state)。
     *
     * 用法:VM.onPauseToggle(false) 调,语义 = "让已 enqueue / RUNNING 的 backfill 继续",
     * 不是 "重启一份新的"。
     */
    fun scheduleEntityBackfillResume() {
        if (assocSettings.pauseBackfill()) return
        // KEEP 策略:不取消 in-flight worker,如果 unique name 已存在则保留。
        // 仅在从未 enqueue 时(冷启后用户首次进设置页立即点暂停又立即点恢复)才会真正入队。
        val request = OneTimeWorkRequestBuilder<EntityBackfillWorker>()
            .setConstraints(noNetworkConstraints())
            .addTag(ENTITY_BACKFILL_TAG)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME_ENTITY, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * R4-2-B fix:用户切到 pause=true 时,主动取消已 enqueue 的 worker。
     * 否则 Worker 在 5s initial delay 后起跑,自检 `shouldRun=false` 失败显示 red FAILED "已暂停"。
     *
     * R5-1 fix:**不**写 [PREF_ENTITY_BACKFILL_DONE]。R2 H9 明确 contract = "只有 Worker success 才能
     * 写 PREF_DONE",pause 路径写会违反这个 contract。后果:用户 pause 一次 → DONE 永远 true →
     * 冷启 [scheduleEntityBackfillIfNeeded] 永远 early-return → 用户再也没自动 backfill。
     *
     * 冷启路径靠 [NoteAssociationSettingsStore.pauseBackfill](store flag) 第一行 guard 拦住
     * (见 line 45),不需要 PREF_DONE 介入。
     */
    fun pauseEntityBackfill() {
        WorkManager.getInstance(context).cancelAllWorkByTag(ENTITY_BACKFILL_TAG)
    }

    companion object {
        private const val PREFS_NAME = "backfill_note_association"
        private const val PREF_BACKFILL_DONE = "backfill_v1_done"
        private const val PREF_ENTITY_BACKFILL_DONE = "backfill_entity_v1_done"
        private const val WORK_NAME_NOTE = "note-association-backfill"
        private const val WORK_NAME_ENTITY = "entity-backfill-v4"
        const val ENTITY_BACKFILL_TAG = "entity_backfill"

        // R3 fix M7:回填延后共用常量,避免 scheduleIfNeeded / scheduleEntityBackfillIfNeeded
        // 各写一份 `5` / `Constraints.Builder()` 漂移。
        private const val INITIAL_DELAY_SECONDS = 5L

        private fun noNetworkConstraints(): Constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()
    }
}
