package com.yy.writingwithai.feature.settings.association

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.yy.writingwithai.core.note.backfill.BackfillScheduler
import com.yy.writingwithai.core.prefs.NoteAssociationSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * entity-extraction-polish §4.1:设置页 VM，统一管理 threshold / pauseBackfill / WorkInfo 订阅 /
 * 强制重排按钮。
 *
 * 设计:threshold 默认对齐 SQL 当前 0.10(从 store 读),slider 范围 0.05–0.80。
 * `onThresholdChangeFinished` 而非 `onValueChange` 触发写盘 — 避免滑动时高频 IO。
 */
@HiltViewModel
class NoteAssociationSettingsViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val assocSettings: NoteAssociationSettingsStore,
    private val backfillScheduler: BackfillScheduler
) : ViewModel() {

    /**
     * §4.3:一次性迁移 banner — 升级时若 store 里残留 >0.50 的旧值，自动重置 0.10 并提示用户。
     * bannerFlow 是普通 StateFlow，首次消费后调用 [acknowledgeMigrationBanner] 清空。
     */
    private val _migrationBanner = MutableStateFlow(false)
    val migrationBanner: StateFlow<Boolean> = _migrationBanner.asStateFlow()

    /** Slider 范围与默认值，UI 用。 */
    val sliderRange: ClosedFloatingPointRange<Float> = THRESHOLD_RANGE
    val sliderSteps: Int = THRESHOLD_STEPS
    val defaultThreshold: Float = THRESHOLD_DEFAULT.toFloat()

    // review-2026-07-02 coroutine-scope:Eagerly → WhileSubscribed(5s)，与 workInfo 一致;
    // 设置页不在前台时停止上游订阅，避免 DataStore 持续发射无意义更新。
    val threshold: StateFlow<Float> = assocSettings.observeThreshold()
        .map { it.coerceIn(THRESHOLD_RANGE.start, THRESHOLD_RANGE.endInclusive) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), assocSettings.threshold())

    /**
     * §4.1:观察 [NoteAssociationSettingsStore.pauseBackfill]。store 没显式 observe,
     * 这里直接 subscribe callbackFlow 风格 — 通过 [observePauseBackfill] 包装(若不存在则走原始 observeEnabled)。
     */
    val paused: StateFlow<Boolean> = assocSettings.observePauseBackfill()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), assocSettings.pauseBackfill())

    /**
     * §4.4:WorkInfo 订阅 — tag = entity_backfill，优先取最新 non-terminal worker(RUNNING / ENQUEUED / BLOCKED)。
     *
     * R4-2-D fix:原实现 `infos.maxByOrNull { state.ordinal }` 在 REPLACE 后会选旧 CANCELLED worker
     * (ordinal=5),UI 显示 stale 状态而非新 ENQUEUED (ordinal=0)。现在优先 non-terminal,
     * 全部终态时退回最近一条(scheduler.cancelAllWorkByTag 后会有 CANCELLED 残留)。
     */
    val workInfo: StateFlow<WorkInfo?> = WorkManager.getInstance(context)
        .getWorkInfosByTagFlow(BackfillScheduler.ENTITY_BACKFILL_TAG)
        .map { infos ->
            infos.firstOrNull {
                it.state == WorkInfo.State.RUNNING ||
                    it.state == WorkInfo.State.ENQUEUED ||
                    it.state == WorkInfo.State.BLOCKED
            }
                // R5-5 fix:fallback 跳过 stale CANCELLED — cancelAllWorkByTag 后残留 CANCELLED
                // 不应显示在 UI。全部终态时若无 SUCCEEDED/FAILED 则返回 null(隐藏进度块)。
                ?: infos.firstOrNull { it.state == WorkInfo.State.SUCCEEDED || it.state == WorkInfo.State.FAILED }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        runMigrationCheck()
    }

    private fun runMigrationCheck() {
        val current = assocSettings.threshold()
        if (current > 0.50f) {
            // §4.3:仅异常值重置;正常用户不动
            assocSettings.setThreshold(THRESHOLD_DEFAULT)
            _migrationBanner.value = true
        }
    }

    fun acknowledgeMigrationBanner() {
        _migrationBanner.value = false
    }

    /** §4.1:Slider `onValueChangeFinished` 调，节流写盘。 */
    fun onThresholdChangeFinished(value: Float) {
        val clamped = value.coerceIn(THRESHOLD_RANGE.start, THRESHOLD_RANGE.endInclusive)
        assocSettings.setThreshold(clamped)
    }

    fun onPauseToggle(value: Boolean) {
        assocSettings.setPauseBackfill(value)
        if (value) {
            // R4-2-B fix:用户切到 pause=true 时主动取消已 enqueue 的 worker。
            // R5-1 fix:不写 PREF_DONE(只有 Worker success 才写)，见 BackfillScheduler.pauseEntityBackfill KDoc。
            backfillScheduler.pauseEntityBackfill()
        } else {
            // R4-2-A fix:用 scheduleEntityBackfillResume(KEEP) 而不是 scheduleEntityBackfillNow(REPLACE)。
            // REPLACE 会取消任何 in-flight RUNNING worker，丢已抽取的 N 笔记进度;
            // unpause 语义是"让进行中的 backfill 继续"，不是"扔掉重启"。
            backfillScheduler.scheduleEntityBackfillResume()
        }
    }

    /** §4.2:「立即重跑回填」按钮 — force=true 绕过 pause,Worker 自检仍生效。 */
    fun onReRunClick() {
        // R5-3 fix:同步读 pauseBackfill，防止 StateFlow lag 期间按钮仍 enabled 导致
        // force=true enqueue → Worker shouldRun=false → red FAILED "已暂停"。
        if (assocSettings.pauseBackfill()) return
        viewModelScope.launch {
            backfillScheduler.scheduleEntityBackfillNow(force = true)
        }
    }

    companion object {
        // §4.1:Slider 范围 0.05–0.80,step 0.05，默认 0.10(对齐 SQL 当前值)。
        val THRESHOLD_RANGE: ClosedFloatingPointRange<Float> = 0.05f..0.80f
        const val THRESHOLD_STEPS = 14
        const val THRESHOLD_DEFAULT = 0.10f
    }
}
